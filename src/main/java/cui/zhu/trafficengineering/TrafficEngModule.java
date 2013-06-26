package cui.zhu.trafficengineering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.python.indexer.ast.NBoolOp.OpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TrafficEngModule implements IFloodlightModule, ICZTrafficEngService {

	public static final String PACKET_IN_LISTENER_NAME = "cui.zhu.trafficengineering.packetinlistener";
	public static final String FLOW_REMOVED_LISTENER_NAME = "cui.zhu.trafficengineering.flowremovedlistener"; 
	
	protected static final int OFMESSAGE_DAMPER_CAPACITY = 10000;
	protected static final int OFMESSAGE_DAMPER_TIMEOUT_MS = 250;
	
	protected int PORT_STATUS_UPDATE_INTEVAL_MS = 30000;
	
	private static Logger logger;
	
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApiService;
	protected ITopologyService topologyService;
	protected IRoutingService routingService;
	protected IThreadPoolService threadPool;
	
	protected OFMessageDamper messageDamper;
	
	protected ITopologyListener topologyListener;
	protected IOFMessageListener packetInListener;
	protected IOFMessageListener flowRemovedListener;
	
	protected SingletonTask linkStatusUpdateTask;
	protected OFStatisticsRequest allPortStatRequest;
	
	// ��·��Ϣ����
	protected Map<RouteId, ArrayList<Route>> routeMaps;
	protected Map<NodePortTuple, OFPortStatisticsReply> linkStatusMap;
	
	// flow����OFMatch��ʾ���� path����Route����ʾ����
	protected FlowRouteDatabase flowRouteDatabase;
	
	/**
	 * ���ڸ���Switch Port��Ϣ��Worker Thread
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
	 * ͨ��ofp_port_stats��ý�������LinkStatus
	 * TODO Ŀǰ��ȡ����OFSwitch�����п���Port����Ϣ
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
		// ׼��allPortStatRequest
		if (allPortStatRequest == null){
			allPortStatRequest = new OFStatisticsRequest();
			allPortStatRequest.setStatisticType(OFStatisticsType.PORT);
			//����port request body -- See OF Spec. 1.1.0 page47
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
			// ����swDpid��Ӧ��switch����Ϣ
			logger.debug("����ofp_port_stat������{}",swDpid);
			future = ofSwitch.getStatistics(allPortStatRequest);
			statList = future.get();
			// ������Ӧ
			logger.debug("��������{}��ofp_port_stat��Ӧ",swDpid);
			for (OFStatistics statElem: statList){
				OFPortStatisticsReply portStat = (OFPortStatisticsReply)statElem;
				short portId = portStat.getPortNumber();
				NodePortTuple npt = new NodePortTuple(swDpid, portId);
				updateMap.put(npt, portStat);
				logger.debug("PORT��Ϣ:{}",portStat);
			}
		}
		long updateEndTime = new Date().getTime();
		logger.debug("update ended at {}, {} ms passed",updateEndTime,updateEndTime-updateStartTime);
		return updateMap;
	}
	
	
	
	
	/**
	 * �ӻ����Map�л��Route��Ϣ�����û�У������TopologyService���¼���
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
		return c;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		logger = LoggerFactory.getLogger(this.getClass());
		routeMaps = new HashMap<RouteId, ArrayList<Route>>();
		flowRouteDatabase = new FlowRouteDatabase();
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY, EnumSet.of(OFType.FLOW_MOD), OFMESSAGE_DAMPER_TIMEOUT_MS);	//����ForwardBaseģ��
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
		//��һ���̣߳����ڸ��¸�·����Port��ʹ�����
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		linkStatusUpdateTask = new SingletonTask(ses, new LinkStatusUpdateWorker());
		linkStatusUpdateTask.reschedule(0, TimeUnit.MILLISECONDS);
	}

	
	// ---------------
	// ����PacketIn�ķ���
	// ---------------
	
	/**
	 * ��pi�㲥��sw�����ж˿�
	 * @param sw
	 * @param pi
	 */
	private void doFlood(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx){
		
		if (topologyService.isIncomingBroadcastAllowed(sw.getId(), pi.getInPort())==false){
			logger.debug("doFlood, drop broadcast packet from switch={}",sw);
			return;
		}
		// ����PacketOut
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		List<OFAction> actions = new ArrayList<OFAction>();
		// ����Switch�ܷ�����flood��all���´ﲻͬ��action
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
	
	private void doForwardFlow(IOFSwitch sw, OFPacketIn pi){
		// TODO
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
				logger.debug("flowRemovedListener������");
				if (msg.getType()==OFType.FLOW_REMOVED){
					OFFlowRemoved flowRemovedMessage = (OFFlowRemoved)msg;
					OFMatch match = flowRemovedMessage.getMatch();
					flowRouteDatabase.deleteFlow(match);
					logger.debug("flowRemovedListener,ɾ��flow��{}",match);
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
				logger.debug("packetInListener������:switch={}",sw);
				if (msg.getType()!=OFType.PACKET_IN) return Command.CONTINUE;
				OFPacketIn pi = (OFPacketIn)msg;
				Ethernet eth = new Ethernet();
				eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
				// �����multicast����Broadcast,��packet_out�㲥
				if (eth.isBroadcast() || eth.isMulticast()){
					logger.debug("packetInListener���㲥:switch={}",sw);
					doFlood(sw,pi,cntx);
				} else {
					logger.debug("packetInListener,ת��:switch={}",sw);
					doForwardFlow(sw,pi);
				}
				//NOTE �˴����¼���Ȼ����������
				return Command.CONTINUE;
			}
		};
		
	}

}
