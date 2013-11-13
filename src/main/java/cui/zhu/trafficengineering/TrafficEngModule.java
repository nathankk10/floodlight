package cui.zhu.trafficengineering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageDamper;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TrafficEngModule implements IFloodlightModule, ICZTrafficEngService {

	public static final String PACKET_IN_LISTENER_NAME = "cui.zhu.trafficengineering.packetinlistener";
	public static final String FLOW_REMOVED_LISTENER_NAME = "cui.zhu.trafficengineering.flowremovedlistener"; 
	
	protected static final int OFMESSAGE_DAMPER_CAPACITY = 10000;
	protected static final int OFMESSAGE_DAMPER_TIMEOUT_MS = 250;
	protected static final short FLOWMOD_IDLE_TIMEOUT_SEC = 20;
	protected static final short FLOWMOD_HARD_TIMEOUT_SEC = 0; 
	protected static final long COOKIE = 0x1234;
	
	
	protected int PORT_STATUS_UPDATE_INTEVAL_MS = 30000;
	
	private static Logger logger;
	
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApiService;
	protected ITopologyService topologyService;
	protected IRoutingService routingService;
	protected IDeviceService deviceService;
	protected IThreadPoolService threadPool;
	
	protected OFMessageDamper messageDamper;
	
	protected ITopologyListener topologyListener;
	protected IOFMessageListener packetInListener;
	protected IOFMessageListener flowRemovedListener;
	
	protected SingletonTask linkStatusUpdateTask;
	protected OFStatisticsRequest allPortStatRequest;
	
	// 链路信息保存
	protected Map<RouteId, ArrayList<Route>> routeMaps;
	protected Map<NodePortTuple, OFPortStatisticsReply> linkStatusMap;
	
	// flow（以OFMatch表示）， path（以Route）表示数据
	protected FlowRouteDatabase flowRouteDatabase;
	
	/**
	 * 定期更新Switch Port信息的Worker Thread
	 */
	protected class LinkStatusUpdateWorker implements Runnable{

		@Override
		public void run() {
			try{
				linkStatusMap = updateLinkStatus();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} finally {
				linkStatusUpdateTask.reschedule(PORT_STATUS_UPDATE_INTEVAL_MS, TimeUnit.MILLISECONDS);
			}
		}
		
	}
	
	/**
	 * 通过ofp_port_stats获得交换机的LinkStatus
	 * TODO 目前获取所有OFSwitch的所有可用Port的信息
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	protected Map<NodePortTuple, OFPortStatisticsReply> updateLinkStatus() throws IOException, InterruptedException, ExecutionException{
		long updateStartTime = new Date().getTime();
		logger.debug("update link status called at {}", updateStartTime);
		Map<NodePortTuple, OFPortStatisticsReply> updateMap = new HashMap<NodePortTuple, OFPortStatisticsReply>();
		Map<Long, IOFSwitch> switchMap = floodlightProvider.getSwitches();
		Collection<Long> swDpidCollection = switchMap.keySet();
		// 准备allPortStatRequest
		if (allPortStatRequest == null){
			allPortStatRequest = new OFStatisticsRequest();
			allPortStatRequest.setStatisticType(OFStatisticsType.PORT);
			//构造port request body -- See OF Spec. 1.1.0 page47
			OFPortStatisticsRequest ofPortStatRequestBody = new OFPortStatisticsRequest();
			ofPortStatRequestBody.setPortNumber(OFPort.OFPP_NONE.getValue());
			allPortStatRequest.setStatistics(Collections.singletonList(ofPortStatRequestBody));
			int request_length = allPortStatRequest.getLength() + ofPortStatRequestBody.getLength();
			allPortStatRequest.setLengthU(request_length);
		}
		Future<List<OFStatistics>> future;
		List<OFStatistics> statList;
		for (Long swDpid: swDpidCollection){
			IOFSwitch ofSwitch = switchMap.get(swDpid);
			// 请求swDpid对应的switch的信息
			logger.debug("发送ofp_port_stat请求至{}",swDpid);
			future = ofSwitch.getStatistics(allPortStatRequest);
			statList = future.get();
			// 处理相应
			logger.debug("处理来自{}的ofp_port_stat响应",swDpid);
			for (OFStatistics statElem: statList){
				OFPortStatisticsReply portStat = (OFPortStatisticsReply)statElem;
				short portId = portStat.getPortNumber();
				NodePortTuple npt = new NodePortTuple(swDpid, portId);
				updateMap.put(npt, portStat);
				logger.debug("PORT信息:{}",portStat);
			}
		}
		long updateEndTime = new Date().getTime();
		logger.debug("update ended at {}, {} ms passed",updateEndTime,updateEndTime-updateStartTime);
		return updateMap;
	}
	
	
	
	
	/**
	 * 从缓存的Map中获得Route信息，如果没有，则调用TopologyService重新计算
	 * @param srcDpid
	 * @param dstDpid
	 */
	public ArrayList<Route> getRoutes (long srcDpid, long dstDpid){
		RouteId routeId = new RouteId(srcDpid, dstDpid);
		ArrayList<Route> routes = routeMaps.get(routeId);
		if (routes==null){
			routes= routingService.getRoutes(srcDpid, dstDpid, true);
			logger.debug("query routes for {} to {}", srcDpid, dstDpid);
			if (routes!=null){
				logger.debug("save routes for {} to {}", srcDpid,dstDpid);
				routeMaps.put(routeId, routes);
				return routes;
			}else{
				logger.debug("returned routes is null");
				return null;
			}
		} else {
			logger.debug("routes for {} to {} found in cache",srcDpid,dstDpid);
			return routes;
		}
	}
	
	// ---------------
	// IFloodlightModule
	// ---------------
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> c = new ArrayList<Class <? extends IFloodlightService>>();
		c.add(ICZTrafficEngService.class);
		return c;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		map.put(ICZTrafficEngService.class, this);
		return map;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> c = new ArrayList<Class<? extends IFloodlightService>>();
		c.add(IFloodlightProviderService.class);
		c.add(IRestApiService.class);
		c.add(ITopologyService.class);
		c.add(IRoutingService.class);
		c.add(IThreadPoolService.class);
		c.add(IDeviceService.class);
		return c;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		logger = LoggerFactory.getLogger(this.getClass());
		routeMaps = new HashMap<RouteId, ArrayList<Route>>();
		flowRouteDatabase = new FlowRouteDatabase();
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY, EnumSet.of(OFType.FLOW_MOD), OFMESSAGE_DAMPER_TIMEOUT_MS);	//参照ForwardBase模块
		initTopologyListener();
		initPacketInListener();
		initFlowRemovedListener();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new TrafficEngRoutable());
		topologyService.addListener(topologyListener);
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, flowRemovedListener);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, packetInListener);
		//跑一个线程，定期更新各路由器Port的使用情况
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		linkStatusUpdateTask = new SingletonTask(ses, new LinkStatusUpdateWorker());
		linkStatusUpdateTask.reschedule(0, TimeUnit.MILLISECONDS);
	}

	
	// ---------------
	// 处理PacketIn的方法
	// ---------------
	
	/**
	 * 将pi广播至sw的所有端口
	 * @param sw
	 * @param pi
	 */
	private void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx){
		// 避免广播风暴的出现，通过topologyService计算，block某些端口的广播，将图变成broadcastTree
		if (topologyService.isIncomingBroadcastAllowed(sw.getId(), pi.getInPort())==false){
			logger.debug("doFlood, drop broadcast packet from switch={}",sw);
			return;
		}
		// 构建PacketOut
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		List<OFAction> actions = new ArrayList<OFAction>();
		// 根据Switch能否区分flood和all，下达不同的action
		if (sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD)){
			actions.add(new OFActionOutput(OFPort.OFPP_FLOOD.getValue(), (short)0xFFFF));
		} else {
			actions.add(new OFActionOutput(OFPort.OFPP_ALL.getValue(), (short)0xFFFF));
		}
		po.setActions(actions);
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		short poLength = (short)(po.getActionsLength()+OFPacketOut.MINIMUM_LENGTH);
		// see OpenFlow spec 1.1 Page 49
		po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		po.setInPort(pi.getInPort());
		byte[] packetData = pi.getPacketData();
		poLength += packetData.length;
		po.setPacketData(packetData);
		po.setLength(poLength);
		logger.debug("Writing flood PacketOut to switch={}",sw);
		try {
			messageDamper.write(sw, po, cntx);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void doForwardFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx){
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		// 出现packet_in包，则说明这个flow已经down了，还是从flowRouteDatabase中删除吧
		flowRouteDatabase.deleteFlow(match);
		IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);
		if (dstDevice==null){
			logger.info("doForward: can't find dstDevice, do flood");
			doFlood(sw, pi, cntx);
			return;
		}
		// dstDevice is located, find route
		Route route = findBestRouteForFlow(sw, dstDevice, pi);
		if (route==null){
			logger.info("can't find a route to forward the packet, doFlood instead");
			doFlood(sw, pi, cntx);
			return;
		}
		// write flow_mod according to route
		addFlowReversely(pi, match, route.getPath(), cntx);
		// save this assignment
		flowRouteDatabase.addFlowToRoute(match, route);
		logger.info("add flow to route={}",route);
	}
	
	/**
	 * 计算从sw至dstDevice的最佳路线
	 * @param srcSw
	 * @param pi
	 * @param dstDevice
	 * @return
	 */
	private Route findBestRouteForFlow (IOFSwitch srcSw, IDevice dstDevice, OFPacketIn pi){
		// TODO 算法的优化，结合各个链路的信息，暂时只返回跳数最少的一条
		// TODO 获得dstDevice所在的AttachmentPoint，暂时只支持一个host连接到一个switch上
		SwitchPort[] dstAPs = dstDevice.getAttachmentPoints();
		SwitchPort dstAP = dstAPs[0];
		// 获得从srcSW前往dstDevice所在Switch的路径
		Long dstDpid = dstAP.getSwitchDPID();
		Long srcDpid = srcSw.getId();
		if (dstDpid==srcDpid){
			//同一个Switch上的转发
			LinkedList<NodePortTuple> link = new LinkedList<NodePortTuple>();
			link.addLast(new NodePortTuple(srcDpid, pi.getInPort()));
			link.addLast(new NodePortTuple(dstDpid, dstAP.getPort()));
			Route route = new Route(new RouteId(srcDpid, dstDpid), link);
			return route;
		}
		//不同Switch之间的转发
		ArrayList<Route> routes = getRoutes(srcDpid, dstDpid);
		Route routeWithoutEnds=routes.get(0);
		//增加源节点，目标节点
		LinkedList<NodePortTuple> link = new LinkedList<NodePortTuple>();
		link.addLast(new NodePortTuple(srcDpid, pi.getInPort()));
		link.addAll(routeWithoutEnds.getPath());
		link.addLast(new NodePortTuple(dstDpid, dstAP.getPort()));
		Route route = new Route(routeWithoutEnds.getId(), link);
		return route;
	}
	
	private void addFlowReversely (OFPacketIn pi, OFMatch match, List<NodePortTuple> links, FloodlightContext cntx){
		// 构建所有switch通用的FlowMod指令
		OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		OFActionOutput action = new OFActionOutput();
		action.setMaxLength((short)0xffff);
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(action);
		fm.setIdleTimeout(FLOWMOD_IDLE_TIMEOUT_SEC)
			.setHardTimeout(FLOWMOD_HARD_TIMEOUT_SEC)
			.setCookie(COOKIE)
			.setCommand(OFFlowMod.OFPFC_ADD)
			.setMatch(match)
			.setActions(actions)
			.setBufferId(OFPacketOut.BUFFER_ID_NONE)
			.setLengthU(OFFlowMod.MINIMUM_LENGTH+OFActionOutput.MINIMUM_LENGTH);
		
		for (int index=links.size()-1;index>0;index=index-2){
			long swDpid = links.get(index).getNodeId();
			short srcPort = links.get(index-1).getPortId();
			short dstPort = links.get(index).getPortId();
			//写入流表，将满足match的packet从srcPort转发至dstPort
			IOFSwitch sw = floodlightProvider.getSwitches().get(swDpid);
			match.setInputPort(srcPort);
			fm.setMatch(match);
			//更改action的输出端口
			((OFActionOutput)fm.getActions().get(0)).setPort(dstPort);
			//如果是源，则需要remove的通知，同时，bufferdPacket可以自动处理
			if (index==1) {
				fm.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
				fm.setBufferId(pi.getBufferId());
			}
			//写入flow_mod
			logger.info("写入流表-switch={}，from port{} to port{}",new Object[]{swDpid,srcPort,dstPort});
			try {
				messageDamper.write(sw, fm, cntx);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				fm = fm.clone();
				sw.flush();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
		}
	}
	
	// ---------------
	// ICZTrafficEngService
	// ---------------
	@Override
	public void debug() {
		logger.debug("Debug Start");
		ArrayList<Route> routes = getRoutes(5, 1);
		System.out.println(routes);
	}
	
	// ---------------
	// private listener init methods
	// ---------------
	private void initTopologyListener(){
		topologyListener = new ITopologyListener() {
			@Override
			public void topologyChanged() {
				routeMaps.clear();
			}
		};
	}
	
	private void initFlowRemovedListener(){
		flowRemovedListener = new IOFMessageListener() {
			
			@Override
			public boolean isCallbackOrderingPrereq(OFType type, String name) {
				return false;
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(OFType type, String name) {
				return false;
			}
			
			@Override
			public String getName() {
				return FLOW_REMOVED_LISTENER_NAME;
			}
			
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				logger.info("flowRemovedListener被调用");
				if (msg.getType()==OFType.FLOW_REMOVED){
					OFFlowRemoved flowRemovedMessage = (OFFlowRemoved)msg;
					OFMatch match = flowRemovedMessage.getMatch();
					flowRouteDatabase.deleteFlow(match);
					logger.info("flowRemovedListener,删除flow：{}",match);
				}
				return Command.CONTINUE;
			}
		};
	}
	
	
	private void initPacketInListener(){
		packetInListener = new IOFMessageListener() {
			
			@Override
			public boolean isCallbackOrderingPrereq(OFType type, String name) {
				return false;
			}
			
			@Override
			public boolean isCallbackOrderingPostreq(OFType type, String name) {
				return false;
			}
			
			@Override
			public String getName() {
				return PACKET_IN_LISTENER_NAME;
			}
			
			@Override
			public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
				logger.debug("packetInListener被调用:switch={}",sw);
				if (msg.getType()!=OFType.PACKET_IN) return Command.CONTINUE;
				OFPacketIn pi = (OFPacketIn)msg;
				Ethernet eth = new Ethernet();
				eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
				// 如果是multicast或者Broadcast,则packet_out广播
				if (eth.isBroadcast() || eth.isMulticast()){
					logger.debug("packetInListener，广播:switch={}",sw);
					doFlood(sw,pi,cntx);
				} else {
					logger.debug("packetInListener,转发:switch={}",sw);
					doForwardFlow(sw,pi,cntx);
				}
				//NOTE 此处该事件仍然将继续传递
				return Command.CONTINUE;
			}
		};
		
	}

}
