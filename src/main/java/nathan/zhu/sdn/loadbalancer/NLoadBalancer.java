package nathan.zhu.sdn.loadbalancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NLoadBalancer implements IFloodlightModule{
	
 	protected static Logger logger;
	
	protected IFloodlightProviderService floodlightProvider;
	protected IDeviceService deviceManager;
	//ʵ�ֵļ�����
	private IDeviceListener deviceListener;
	private IOFMessageListener ofMessageListener;
	
	private NLoadBalancer self;
	private ArrayList<IDevice> serverDevices; //���������Device������֤һ�����ߣ�

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	
	// ��Ҫ��Service
	// 1. IFloodlightService
	// 2. IDeviceService - ���Device�ı仯״̬
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> services = new ArrayList<Class<? extends IFloodlightService>>();
		services.add(IFloodlightProviderService.class);
		services.add(IDeviceService.class);
		return services;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		self = this;
		logger = LoggerFactory.getLogger(this.getClass());
		serverDevices = new ArrayList<IDevice>();
		floodlightProvider = (IFloodlightProviderService) context.getServiceImpl(IFloodlightProviderService.class);
		deviceManager = (IDeviceService)context.getServiceImpl(IDeviceService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		logger.debug("Start NLoadBalancer");
		initIDeviceListener();
		initIOFMessageListener();
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, ofMessageListener);
		deviceManager.addListener(deviceListener);
	}

	// ---------------
	// IDeviceListener
	// ---------------
	private void initIDeviceListener (){
		logger.debug("init IDeviceListener");
		
		deviceListener = new IDeviceListener() {
			
			@Override
			public boolean isCallbackOrderingPrereq(String type, String name) {
				return false;
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(String type, String name) {
				return false;
			}
			
			@Override
			public String getName() {
				return self.getClass().getCanonicalName();
			}
			
			@Override
			public void deviceVlanChanged(IDevice device) {
			}
			
			@Override
			public void deviceAdded(IDevice device) {
				logger.debug("notified with device added: {}", device.getMACAddressString());
				// TODO ����һ����¼�ֶ�ͨ��MAC��ַ���Server�ķ�������ʱͨ���ȶ�ʵ��
				long deviceMAC = device.getMACAddress();
				long requiredMAC = MACAddress.valueOf("00:00:00:00:01:01").toLong();
				if (deviceMAC==requiredMAC){
					logger.info("ServerDevices add {}", device.getMACAddressString());
					serverDevices.add(device);
				}
			}
			
			@Override
			public void deviceRemoved(IDevice device) {
				logger.debug("notified with device removed: {}", device.getMACAddressString());
				if (serverDevices.contains(device)){
					logger.info("ServerDevices remove {}", device.getMACAddressString());
					serverDevices.remove(device);
				}
			}
			
			@Override
			public void deviceMoved(IDevice device) {
			}
			
			@Override
			public void deviceIPV4AddrChanged(IDevice device) {	
			}

		};
	}
	
	// ---------------
	// IOFMessageListener
	// ---------------
	void initIOFMessageListener (){
		logger.debug("init IOFMessageListener");
		
		ofMessageListener = new IOFMessageListener() {
			
			@Override
			public boolean isCallbackOrderingPrereq(OFType type, String name) {
				return name.equals("forwarding");
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(OFType type, String name) {
				return false;
			}
			
			@Override
			public String getName() {
				return self.getClass().getCanonicalName();
			}
			
			/**
			 * ARP��ת�����Ѿ���Forwardingģ����ɣ���ģ����Ҫ��
			 * 1. ����һ�������IP��ַ�����䷵��ARP��
			 * 2. �Դ�IP����ĵ�ַ���޸���IP��ַ��MAC��ַ������ת����������
			 * 3. �Է��������ص����ݣ���srcIP����Ϊ������������IP��������һ��ת��
			 */
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				logger.debug("receive PACKET_IN message");
				// TODO ��ͬ��switchӦ�ö�Ӧ��ͬ��IP��ַ���ڴ��ڳ�����д��̶���ֵ
				int serverIP = IPv4.toIPv4Address("10.0.0.1");
				switch (msg.getType()) {
					case PACKET_IN:
						//OFPacketIn pi = (OFPacketIn)msg;
						//�õ�cntx�е�Ethernet��Ϣ
						Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
						/* ����һ�´�cntx�еõ��ĺʹ�pi.getPacketData()�л�õ�eth���Ƿ�һ�������һ��
						Ethernet eth2 = new Ethernet();
						eth2.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
						logger.debug("is equal?{}",eth.equals(eth2));
						*/
						if (eth.getEtherType()==Ethernet.TYPE_ARP){
							ARP arp = (ARP) eth.getPayload();
							int ipAddress = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
							logger.debug("ARP to {}",IPv4.fromIPv4Address(ipAddress));
							if (ipAddress==serverIP){
								// ͨ��packet-out message����IP ARP��·�����Լ���MAC��ַ��
								
							}
						} else if (eth.getEtherType()==Ethernet.TYPE_IPv4) {
							
						}
						break;
					default:
						break;
				}
				return Command.CONTINUE;
			}
		};
	}

}
