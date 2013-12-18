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
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
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
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NATController implements IFloodlightModule {
	
	private Coordinator coordinator;	//����ģ��ʵ�ʵ�Coordinator���������ý���֮��ͨ��RESTAPIʵ��
	
	private static Logger logger;
	private NATController self;
	private IOFSwitch gatewaySwitch;
	
	private IFloodlightProviderService floodlightProvider;
	private IDeviceService deviceService;
	private IRoutingService routingService;

	private IOFMessageListener ofMessageListener;
	private IOFSwitchListener ofSwitchListener; //������OvS�����ϵ�ʱ�����û�������ARP��floodlight����������������Щ���Ͳ��ᱻӰ��
	private IDeviceListener deviceListener;
	
	private String MODULE_NAME = "natcontroller";

	
	/**
	 * �������ã��������ṩ������ҪIFloodlightProviderService
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
	 * ��ʼ��
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
		initDeviceListener();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		logger.debug("NATController is starting up!");
		floodlightProvider.addOFSwitchListener(ofSwitchListener);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, ofMessageListener);
		deviceService.addListener(deviceListener);
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
				
				logger.debug("Switch DPID �ȽϽ����{}", (switchDPID & 0x00FFFFFFFFFFFFL) == MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong());
				
				if ( (switchDPID & 0x00FFFFFFFFFFFFL) != MACAddress.valueOf(Config.NAT_SWITCH_MAC).toLong())
					return;
				
				// ע��gateway switch
				gatewaySwitch = sw;
				// �˴���OvSд��ARP��̬����
				
				// �˴���OvSд��FloodLight������·��̬����
				
				// �˴���OvSд��Floodlight����������ת����������Coordinator���ľ�̬����
				
				// д���������������ARP agent��Floodlight�Ŀ�����·������
			}
		};
	}
	
	private void initDeviceListener(){
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
				return self.MODULE_NAME;
			}
			
			@Override
			public void deviceVlanChanged(IDevice device) {
			}
			
			@Override
			public void deviceRemoved(IDevice device) {
				coordinator.deviceRemoved(device);
			}
			
			@Override
			public void deviceMoved(IDevice device) {
				
			}
			
			@Override
			public void deviceIPV4AddrChanged(IDevice device) {
				coordinator.deviceIPV4AddrChanged(device);
			}
			
			@Override
			public void deviceAdded(IDevice device) {
				coordinator.deviceAdded(device);
			}
		};
	}
	
	
	private void initOFMessageListener(){
		ofMessageListener = new IOFMessageListener() {
			
			//�԰��Ĵ�������·���֡��豸����֮����ת��ģ��֮ǰ
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
				logger.debug("���յ�Packet in");
				OFPacketIn of_Pi = (OFPacketIn)msg;
				byte[] of_Pi_payload = of_Pi.getPacketData();
				Ethernet eth = new Ethernet();
				eth.deserialize(of_Pi_payload, 0, of_Pi_payload.length);
				/**
				 * ��ȡ�õ�msg�е�payload�����ݰ���arp
				 * dl_vlan: untagged
				 * dl_vlan_pcp: 0
				 * dl_src: 00:00:00:00:01:02
				 * dl_dst: 00:00:00:00:01:01
				 * nw_src: 10.0.2.1
				 * nw_dst: 10.0.1.1
				 */
				if (eth.getEtherType() == Ethernet.TYPE_ARP) 
				{	
					// ����ARP���ݰ�����OpenStack����
					ARP arp_packet = (ARP)eth.getPayload();
					if (arp_packet.getOpCode() == ARP.OP_REQUEST)
					{
						//logger.debug("ARP REQUEST:{}",arp_packet);
						byte[] senderIP = arp_packet.getSenderProtocolAddress();
						byte[] senderMAC = arp_packet.getSenderHardwareAddress();
						byte[] targetIP = arp_packet.getTargetProtocolAddress();
						// ��Coordinatorѯ�ʶ�Ӧ��MAC��ַ
						byte[] targetMAC = coordinator.get_MAC_address(targetIP);
						// ���MAC��ַCoordinatorδ֪������Forwardingģ��㲥
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
					// ������㲥��IP��
					if (eth.isMulticast() || eth.isBroadcast())
						return Command.CONTINUE;
					// ����IPv4�����ݰ�
					IPv4 nw_packet = (IPv4)eth.getPayload();
					logger.debug("dst_ip:{}",IPv4.fromIPv4Address(nw_packet.getDestinationAddress()));
					int dst_ipv4address = nw_packet.getDestinationAddress();
					Iterator<? extends IDevice> devices =  deviceService.queryDevices(null, null, dst_ipv4address, null, null);
					// Ŀ��IP�ڱ�����
					if (devices.hasNext()) 
					{
						// Ŀ��IP�ڱ��أ�ֱ�ӽ���Forwardingģ��ת��
						logger.debug("Device:{}",devices.next());
						return Command.CONTINUE;
					}
					
					// ���򲻴�����һIP��ַ����Ҫ��OvSת��
					// TODO: 1.��Coordianatorѯ��IP��ַ�Ƿ����������У�����������NATת�� 2.��Gateway Switchд����Ӧ��NATת������ 3.�����ݰ�Forwarding��Ŀ��Gateway Switch
					TCP tcp_packet = (TCP)nw_packet.getPayload();
					NATRecord original = new NATRecord(nw_packet.getSourceAddress(), tcp_packet.getSourcePort(), nw_packet.getDestinationAddress(), tcp_packet.getDestinationPort());
					NATRecord target = coordinator.performNat(original);
					if (target.dst_IPAddress==0){
						// ��������ӦIP��������ǹ����ĵ�ַ����ֻ�ı�ԴIP��
						// outbound
						OFMatch out_match = new OFMatch();
						out_match.setWildcards(OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_TOS);
						out_match.setInputPort(OFPort.OFPP_ALL.getValue());
						out_match.setDataLayerSource(eth.getSourceMACAddress());
						out_match.setDataLayerDestination(eth.getDestinationMACAddress());
						out_match.setDataLayerType(eth.getEtherType());
						out_match.setNetworkProtocol(nw_packet.getProtocol());
						out_match.setNetworkDestination(nw_packet.getDestinationAddress());
						out_match.setNetworkSource(nw_packet.getSourceAddress());
						out_match.setNetworkProtocol(nw_packet.getProtocol());
						out_match.setTransportSource(tcp_packet.getSourcePort());
						out_match.setTransportDestination(tcp_packet.getDestinationPort());
						OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						// action 1: outport
						OFActionOutput action_output = new OFActionOutput();
						action_output.setMaxLength((short)0xffff);
						action_output.setPort(Config.NAT_SWITCH_OUTPORT);
						// action 2: set DL src to ovs mac
						OFActionDataLayerSource action_DataLayerSource = new OFActionDataLayerSource();
						action_DataLayerSource.setDataLayerAddress(MACAddress.valueOf(Config.NAT_SWITCH_MAC).toBytes());
						// action 3: set DL dst to normal router
						OFActionDataLayerDestination action_DataLayerDestination = new OFActionDataLayerDestination();
						action_DataLayerDestination.setDataLayerAddress(MACAddress.valueOf(Config.ROUTER_MAC).toBytes());
						// action 4: set Src IP address
						OFActionNetworkLayerSource action_NetworkLayerSource = new OFActionNetworkLayerSource();
						action_NetworkLayerSource.setNetworkAddress(IPv4.toIPv4Address(Config.PUBLIC_IP));
						// action 5: set Src TCP port
						OFActionTransportLayerSource action_TransportLayerSource = new OFActionTransportLayerSource();
						action_TransportLayerSource.setTransportPort(target.src_IPPort);
						List<OFAction> actions = new ArrayList<OFAction>();
						actions.add(action_output);
						actions.add(action_DataLayerSource);
						actions.add(action_DataLayerDestination);
						actions.add(action_NetworkLayerSource);
						actions.add(action_TransportLayerSource);
						fm.setIdleTimeout(Config.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				            .setHardTimeout(Config.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
				            .setCommand(OFFlowMod.OFPFC_ADD)
				            .setMatch(out_match)
				            .setActions(actions)
				            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
						try {
							gatewaySwitch.write(fm, cntx);
							gatewaySwitch.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
						//inbound
						OFMatch in_match = new OFMatch();
						in_match.setWildcards(OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_TOS);
						in_match.setInputPort(OFPort.OFPP_ALL.getValue());
						in_match.setDataLayerSource(MACAddress.valueOf(Config.ROUTER_MAC).toBytes());
						in_match.setDataLayerDestination(MACAddress.valueOf(Config.NAT_SWITCH_MAC).toBytes());
						in_match.setDataLayerType(eth.getEtherType());
						in_match.setNetworkProtocol(nw_packet.getProtocol());
						in_match.setNetworkDestination(IPv4.toIPv4Address(Config.PUBLIC_IP));
						in_match.setNetworkSource(nw_packet.getDestinationAddress());
						in_match.setNetworkProtocol(nw_packet.getProtocol());
						in_match.setTransportSource(tcp_packet.getDestinationPort());
						in_match.setTransportDestination(target.src_IPPort);
						
						fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						// action 1: outport
						action_output = new OFActionOutput();
						action_output.setMaxLength((short)0xffff);
						action_output.setPort(of_Pi.getInPort());
						// action 2: set DL dst to input
						action_DataLayerDestination = new OFActionDataLayerDestination();
						action_DataLayerDestination.setDataLayerAddress(eth.getSourceMACAddress());
						// action 3: set Dst IP address 
						OFActionNetworkLayerDestination action_NetworkLayerDestination = new OFActionNetworkLayerDestination();
						action_NetworkLayerDestination.setNetworkAddress(nw_packet.getSourceAddress());
						// action 4: set Dst TCP port
						OFActionTransportLayerDestination action_TransportLayerDestination = new OFActionTransportLayerDestination();
						action_TransportLayerDestination.setTransportPort(tcp_packet.getSourcePort());
						actions = new ArrayList<OFAction>();
						actions.add(action_output);
						actions.add(action_DataLayerDestination);
						actions.add(action_NetworkLayerDestination);
						actions.add(action_TransportLayerDestination);
						fm.setIdleTimeout(Config.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				            .setHardTimeout(Config.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
				            .setCommand(OFFlowMod.OFPFC_ADD)
				            .setMatch(in_match)
				            .setActions(actions)
				            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
						try {
							gatewaySwitch.write(fm, cntx);
							gatewaySwitch.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						// ������ӦIP���������������ĵ�ַ����ı�ԴIP��Ŀ��IP
						// outbound
						OFMatch out_match = new OFMatch();
						out_match.setWildcards(OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_TOS);
						out_match.setInputPort(OFPort.OFPP_ALL.getValue());
						out_match.setDataLayerSource(eth.getSourceMACAddress());
						out_match.setDataLayerDestination(eth.getDestinationMACAddress());
						out_match.setDataLayerType(eth.getEtherType());
						out_match.setNetworkProtocol(nw_packet.getProtocol());
						out_match.setNetworkDestination(nw_packet.getDestinationAddress());
						out_match.setNetworkSource(nw_packet.getSourceAddress());
						out_match.setNetworkProtocol(nw_packet.getProtocol());
						out_match.setTransportSource(tcp_packet.getSourcePort());
						out_match.setTransportDestination(tcp_packet.getDestinationPort());
						OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						// action 1: output
						OFActionOutput action_output = new OFActionOutput();
						action_output.setMaxLength((short)0xffff);
						action_output.setPort(Config.NAT_SWITCH_OUTPORT);
						// action 2: set DL src to ovs mac
						OFActionDataLayerSource action_DataLayerSource = new OFActionDataLayerSource();
						action_DataLayerSource.setDataLayerAddress(MACAddress.valueOf(Config.NAT_SWITCH_MAC).toBytes());
						// action 3: set DL dst to normal router
						OFActionDataLayerDestination action_DataLayerDestination = new OFActionDataLayerDestination();
						action_DataLayerDestination.setDataLayerAddress(MACAddress.valueOf(Config.ROUTER_MAC).toBytes());
						// action 4: set Src IP address
						OFActionNetworkLayerSource action_NetworkLayerSource = new OFActionNetworkLayerSource();
						action_NetworkLayerSource.setNetworkAddress(IPv4.toIPv4Address(Config.PUBLIC_IP));
						// action 5: set Src TCP port
						OFActionTransportLayerSource action_TransportLayerSource = new OFActionTransportLayerSource();
						action_TransportLayerSource.setTransportPort(target.src_IPPort);
						// action 6: set Dst IP address
						OFActionNetworkLayerDestination action_NetworkLayerDestination = new OFActionNetworkLayerDestination();
						action_NetworkLayerDestination.setNetworkAddress(target.dst_IPAddress);
						// action 7: set Dst TCP port
						OFActionTransportLayerDestination action_TransportLayerDestination = new OFActionTransportLayerDestination();
						action_TransportLayerDestination.setTransportPort(target.dst_IPPort);
						List<OFAction> actions = new ArrayList<OFAction>();
						actions.add(action_output);
						actions.add(action_DataLayerSource);
						actions.add(action_DataLayerDestination);
						actions.add(action_NetworkLayerSource);
						actions.add(action_TransportLayerSource);
						actions.add(action_NetworkLayerDestination);
						actions.add(action_TransportLayerDestination);
						fm.setIdleTimeout(Config.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				            .setHardTimeout(Config.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
				            .setCommand(OFFlowMod.OFPFC_ADD)
				            .setMatch(out_match)
				            .setActions(actions)
				            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
						try {
							gatewaySwitch.write(fm, cntx);
							gatewaySwitch.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
						//inbound
						OFMatch in_match = new OFMatch();
						in_match.setWildcards(OFMatch.OFPFW_IN_PORT | OFMatch.OFPFW_NW_TOS);
						in_match.setInputPort(OFPort.OFPP_ALL.getValue());
						in_match.setDataLayerSource(MACAddress.valueOf(Config.ROUTER_MAC).toBytes());
						in_match.setDataLayerDestination(MACAddress.valueOf(Config.NAT_SWITCH_MAC).toBytes());
						in_match.setDataLayerType(eth.getEtherType());
						in_match.setNetworkProtocol(nw_packet.getProtocol());
						in_match.setNetworkDestination(IPv4.toIPv4Address(Config.PUBLIC_IP));
						in_match.setNetworkSource(target.dst_IPAddress);
						in_match.setNetworkProtocol(nw_packet.getProtocol());
						in_match.setTransportSource(target.dst_IPPort);
						in_match.setTransportDestination(target.src_IPPort);
						
						fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						// action 1: outport
						action_output = new OFActionOutput();
						action_output.setMaxLength((short)0xffff);
						action_output.setPort(of_Pi.getInPort());
						// action 2: set DL dst to original src
						action_DataLayerDestination = new OFActionDataLayerDestination();
						action_DataLayerDestination.setDataLayerAddress(eth.getSourceMACAddress());
						// action 3: set DL src to original dst
						action_DataLayerSource = new OFActionDataLayerSource();
						action_DataLayerSource.setDataLayerAddress(eth.getDestinationMACAddress());
						// action 4: set NW dst to original src
						action_NetworkLayerDestination = new OFActionNetworkLayerDestination();
						action_NetworkLayerDestination.setNetworkAddress(nw_packet.getSourceAddress());
						// action 5: set NW src to original dst
						action_NetworkLayerSource = new OFActionNetworkLayerSource();
						action_NetworkLayerSource.setNetworkAddress(nw_packet.getDestinationAddress());
						// action 6: set TCP dst to original src
						action_TransportLayerDestination = new OFActionTransportLayerDestination();
						action_TransportLayerDestination.setTransportPort(tcp_packet.getSourcePort());
						// action 7: set TCP src to original dst
						action_TransportLayerSource = new OFActionTransportLayerSource();
						action_TransportLayerSource.setTransportPort(tcp_packet.getDestinationPort());
						
						actions = new ArrayList<OFAction>();
						actions.add(action_output);
						actions.add(action_DataLayerDestination);
						actions.add(action_DataLayerSource);
						actions.add(action_NetworkLayerDestination);
						actions.add(action_NetworkLayerSource);
						actions.add(action_TransportLayerDestination);
						actions.add(action_TransportLayerSource);
						fm.setIdleTimeout(Config.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				            .setHardTimeout(Config.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				            .setBufferId(OFPacketOut.BUFFER_ID_NONE)
				            .setCommand(OFFlowMod.OFPFC_ADD)
				            .setMatch(in_match)
				            .setActions(actions)
				            .setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
						try {
							gatewaySwitch.write(fm, cntx);
							gatewaySwitch.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					
					
					
					// Gateway Switch�Ѿ�����
					if (gatewaySwitch==null){
						logger.debug("Gateway Switch not connected");
						return Command.CONTINUE;
					}
					// ��IP������Gateway Switch
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
			        		// ���switch����Ҫ�ط�
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
