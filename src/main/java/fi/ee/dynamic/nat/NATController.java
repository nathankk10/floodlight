package fi.ee.dynamic.nat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.python.antlr.PythonParser.return_stmt_return;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Wildcard;


public class NATController implements IFloodlightModule {
	
	private Coordinator coordinator;	//用来模拟实际的Coordinator，函数调用将在之后通过RESTAPI实现
	
	private static Logger logger;
	private NATController self;
	private IOFSwitch gatewaySwitch;
	
	private IFloodlightProviderService floodlightProvider;
	private IDeviceService deviceService;
	private IRoutingService routingService;

	private IOFMessageListener ofMessageListener;
	private IOFSwitchListener ofSwitchListener; //当网关OvS连接上的时候配置基本流表（ARP，floodlight控制器），这样这些流就不会被影响
	
	private String MODULE_NAME = "natcontroller";

	
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
		services.add(IDeviceService.class);
		services.add(IRoutingService.class);
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
		deviceService = (IDeviceService) context.getServiceImpl(IDeviceService.class);
		routingService = (IRoutingService) context.getServiceImpl(IRoutingService.class);
		coordinator = new Coordinator();
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
				return self.MODULE_NAME;
			}
			
			@Override
			public void addedSwitch(IOFSwitch sw) {
				Long switchDPID = sw.getId();
				
				logger.debug("Switch DPID 比较结果：{}", (switchDPID & 0x00FFFFFFFFFFFFL) == MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong());
				
				if ( (switchDPID & 0x00FFFFFFFFFFFFL) != MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong())
					return;
				
				// 注册gateway switch
				gatewaySwitch = sw;
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
				if (eth.getEtherType() == Ethernet.TYPE_ARP) 
				{	
					// 处理ARP数据包。由OpenStack给出
					// or 对ARP包也进行透明传输，广播到所有的不同的域中
					ARP arp_packet = (ARP)eth.getPayload();
					if (arp_packet.getOpCode() == ARP.OP_REQUEST)
					{
						//logger.debug("ARP REQUEST:{}",arp_packet);
						byte[] senderIP = arp_packet.getSenderProtocolAddress();
						byte[] senderMAC = arp_packet.getSenderHardwareAddress();
						byte[] targetIP = arp_packet.getTargetProtocolAddress();
						// 向Coordinator询问对应的MAC地址
						byte[] targetMAC = coordinator.get_MAC_address(targetIP);
						// 如果MAC地址Coordinator未知，则交由Forwarding模块广播
						if (targetMAC == null){
							return Command.CONTINUE;
						}
						ARP arp_response = (ARP) arp_packet.clone();
						arp_response.setOpCode(ARP.OP_REPLY);
						arp_response.setSenderHardwareAddress(targetMAC);
						arp_response.setSenderProtocolAddress(targetIP);
						arp_response.setTargetHardwareAddress(senderMAC);
						arp_response.setTargetProtocolAddress(senderIP);
						//logger.debug("Output ARP RESPONSE:{}",arp_response);
						// make up Ethernet packet
						Ethernet eth_response = (Ethernet)eth.clone();
						eth_response.setSourceMACAddress(targetMAC);
						eth_response.setDestinationMACAddress(senderMAC);
						eth_response.setPayload(arp_response);
						// make up OFMessage
						OFPacketOut po = (OFPacketOut)floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
						ArrayList<OFAction> actions = new ArrayList<OFAction>();
						actions.add(new OFActionOutput(of_Pi.getInPort()));
						po.setActions(actions);
						po.setActionsLength((short)OFActionOutput.MINIMUM_LENGTH);
						short poLength = (short)(po.getActionsLength()+OFPacketOut.MINIMUM_LENGTH);
						po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
						po.setInPort(OFPort.OFPP_CONTROLLER);
						byte[] packetData = eth_response.serialize();
						poLength += packetData.length;
						po.setPacketData(packetData);
						po.setLength(poLength);
						try {
							sw.write(po,cntx);
						} catch (IOException e) {
							e.printStackTrace();
						}
						sw.flush();
						return Command.STOP;
					}
				}
				else if (eth.getEtherType() == eth.TYPE_IPv4) {
					// 不处理广播的IP包
					if (eth.isMulticast() || eth.isBroadcast())
						return Command.CONTINUE;
					// 处理IPv4的数据包
					IPv4 nw_packet = (IPv4)eth.getPayload();
					logger.debug("dst_ip:{}",IPv4.fromIPv4Address(nw_packet.getDestinationAddress()));
					int dst_ipv4address = nw_packet.getDestinationAddress();
					Iterator<? extends IDevice> devices =  deviceService.queryDevices(null, null, dst_ipv4address, null, null);
					if (devices.hasNext()) 
					{
						// 目标IP在本地，直接交由Forwarding模块转发
						logger.debug("Device:{}",devices.next());
						return Command.CONTINUE;
					}
					
					// 本域不存在这一IP地址，需要由OvS转发
					// TODO: 1.向Coordianator询问IP地址是否在其他域中，进行怎样的NAT转换 2.向Gateway Switch写入相应的NAT转换流表 3.将数据包Forwarding到目标Gateway Switch
					
					// Gateway Switch已经发现
					if (gatewaySwitch==null){
						logger.debug("Gateway Switch not connected");
						return Command.CONTINUE;
					}
					// 将IP包送至Gateway Switch
					OFMatch match = new OFMatch();
					match.loadFromPacket(of_Pi.getPacketData(), of_Pi.getInPort());
					
					OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
					OFActionOutput action = new OFActionOutput();
					action.setMaxLength((short)0xffff);
			        List<OFAction> actions = new ArrayList<OFAction>();
			        actions.add(action);

			        fm.setIdleTimeout(Config.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			            .setHardTimeout(Config.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
			            .setCommand(OFFlowMod.OFPFC_ADD)
			            .setMatch(match)
			            .setActions(actions)
			            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
			        
			        Route route = routingService.getRoute(sw.getId(), of_Pi.getInPort(), gatewaySwitch.getId(), Config.NAT_SWITCH_OUTPORT, 0);
			        
			        if (route==null){
			        	logger.debug("No route");
			        	return Command.CONTINUE;
			        }
			        List<NodePortTuple> switchPortList = route.getPath();
			        logger.debug("Path: {}",switchPortList);
			        for (int index = switchPortList.size()-1;index>0;index=index-2){
			        	long switchDPID = switchPortList.get(index).getNodeId();
			        	IOFSwitch cur_sw = floodlightProvider.getSwitches().get(switchDPID);
			        	if (cur_sw==null){
			        		logger.debug("Switch {} on route not available",switchDPID);
			        		return Command.CONTINUE;
			        	}
			        	if (index == 1){
			        		// 入口switch，需要重发
			        		fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
			        	}
			        	short outPort = switchPortList.get(index).getPortId();
			        	short inPort = switchPortList.get(index-1).getPortId();
			        	fm.getMatch().setInputPort(inPort);
			        	((OFActionOutput)fm.getActions().get(0)).setPort(outPort);
			        	logger.debug("Route flowmod sw={} inPort={} outPort={}",new Object[] {cur_sw,fm.getMatch().getInputPort(), outPort });
			        	try {
							cur_sw.write(fm, cntx);
						} catch (IOException e) {
							e.printStackTrace();
						}
			        	cur_sw.flush();
			        }
					return Command.STOP;
				}	// endif of Ipv4
				
				
				
				
				return Command.CONTINUE;
			}	//end of Receive function
		};
	}

}
