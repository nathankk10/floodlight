package fi.ee.dynamic.nat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.python.antlr.PythonParser.return_stmt_return;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NATController implements IFloodlightModule {
	
	private Coordinator coordinator;	//用来模拟实际的Coordinator，函数调用将在之后通过RESTAPI实现
	
	private static Logger logger;
	private NATController self;
	
	private IFloodlightProviderService floodlightProvider;

	private IOFMessageListener ofMessageListener;
	private IOFSwitchListener ofSwitchListener; //当网关OvS连接上的时候配置基本流表（ARP，floodlight控制器），这样这些流就不会被影响
	
	private String MODULE_NAME = "natcontroller";
	private String OF_SWITCH_LISTENER_NAME = "natcontroller.ofswitchlistener";

	
	/**
	 * 服务配置：不向外提供服务，需要IFloodlightProviderService
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> services = new ArrayList<Class<? extends IFloodlightService>>();
		services.add(IFloodlightProviderService.class);
		return null;
	}

	/**
	 * 初始化
	 */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		self = this;
		logger = LoggerFactory.getLogger(this.getClass());
		floodlightProvider = (IFloodlightProviderService) context.getServiceImpl(IFloodlightProviderService.class);
		initOFSwitchListener();
		initOFMessageListener();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		logger.debug("NATController is starting up!");
		floodlightProvider.addOFSwitchListener(ofSwitchListener);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, ofMessageListener);
	}
	
	
	
	
	private void initOFSwitchListener(){
		ofSwitchListener = new IOFSwitchListener() {
			
			@Override
			public void switchPortChanged(Long switchId) {
			}
			
			@Override
			public void removedSwitch(IOFSwitch sw) {
			}
			
			@Override
			public String getName() {
				return OF_SWITCH_LISTENER_NAME;
			}
			
			@Override
			public void addedSwitch(IOFSwitch sw) {
				Long switchDPID = sw.getId();
				
				logger.debug("Switch DPID 比较结果：{}", (switchDPID & 0x00FFFFFFFFFFFFL) == MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong());
				
				if ( (switchDPID & 0x00FFFFFFFFFFFFL) != MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong())
					return;
				// 此处对OvS写入ARP静态流表
				
				// 此处对OvS写入FloodLight控制链路静态流表
				
				// 写入上述两个流表后，ARP agent和Floodlight的控制链路被保障
			}
		};
	}
	
	private void initOFMessageListener(){
		ofMessageListener = new IOFMessageListener() {
			
			//对包的处理在链路发现、设备发现之后，在转发模块之前
			@Override
			public boolean isCallbackOrderingPrereq(OFType type, String name) {
				return ( type.equals(OFType.PACKET_IN) && 
						(name.equals("linkdiscovery")||name.equals("devicemanager")) );
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(OFType type, String name) {
				return ( type.equals(OFType.PACKET_IN) &&
						(name.equals("forwarding")) );
			}
			
			@Override
			public String getName() {
				return self.MODULE_NAME;
			}
			
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				logger.debug("接收到Packet in");
				OFPacketIn of_Pi = (OFPacketIn)msg;
				byte[] of_Pi_payload = of_Pi.getPacketData();
				Ethernet eth = new Ethernet();
				eth.deserialize(of_Pi_payload, 0, of_Pi_payload.length);
				/**
				 * 提取得到msg中的payload，数据包：arp
				 * dl_vlan: untagged
				 * dl_vlan_pcp: 0
				 * dl_src: 00:00:00:00:01:02
				 * dl_dst: 00:00:00:00:01:01
				 * nw_src: 10.0.2.1
				 * nw_dst: 10.0.1.1
				 */
				if (eth.getEtherType() != eth.TYPE_IPv4) 
					return Command.CONTINUE;
				// 仅处理IPv4的数据包
				IPv4 nw_packet = (IPv4)eth.getPayload();
				logger.debug("dst_ip:{}",IPv4.fromIPv4Address(nw_packet.getDestinationAddress()));
				
				
				return Command.CONTINUE;
			}
		};
	}

}
