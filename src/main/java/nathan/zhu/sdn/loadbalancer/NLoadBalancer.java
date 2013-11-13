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
	//实现的监听器
	private IDeviceListener deviceListener;
	private IOFMessageListener ofMessageListener;
	
	private NLoadBalancer self;
	private ArrayList<IDevice> serverDevices; //保存服务器Device（不保证一定在线）

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	
	// 需要的Service
	// 1. IFloodlightService
	// 2. IDeviceService - 获得Device的变化状态
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
				// TODO 考虑一个记录手动通过MAC地址添加Server的方法，暂时通过比对实现
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
			 * ARP包转发等已经由Forwarding模块完成，此模块需要：
			 * 1. 构建一个虚拟的IP地址，对其返回ARP包
			 * 2. 对此IP进入的地址，修改其IP地址和MAC地址，将其转发到服务器
			 * 3. 对服务器返回的数据，将srcIP更改为服务器的虚拟IP，进行下一跳转发
			 */
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				logger.debug("receive PACKET_IN message");
				// TODO 不同的switch应该对应不同的IP地址，在此于程序中写入固定的值
				int serverIP = IPv4.toIPv4Address("10.0.0.1");
				switch (msg.getType()) {
					case PACKET_IN:
						//OFPacketIn pi = (OFPacketIn)msg;
						//得到cntx中的Ethernet信息
						Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
						/* 测试一下从cntx中得到的和从pi.getPacketData()中获得的eth包是否一样，结果一样
						Ethernet eth2 = new Ethernet();
						eth2.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
						logger.debug("is equal?{}",eth.equals(eth2));
						*/
						if (eth.getEtherType()==Ethernet.TYPE_ARP){
							ARP arp = (ARP) eth.getPayload();
							int ipAddress = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
							logger.debug("ARP to {}",IPv4.fromIPv4Address(ipAddress));
							if (ipAddress==serverIP){
								// 通过packet-out message将该IP ARP到路由器自己的MAC地址上
								
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
