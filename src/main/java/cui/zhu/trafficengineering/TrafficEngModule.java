package cui.zhu.trafficengineering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TrafficEngModule implements IFloodlightModule, ICZTrafficEngService {

	private static Logger logger;
	
	protected int PORT_STATUS_UPDATE_INTEVAL_MS = 3000;
	
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApiService;
	protected ITopologyService topologyService;
	protected IRoutingService routingService;
	protected IThreadPoolService threadPool;
	
	protected ITopologyListener topologyListener;
	
	protected SingletonTask linkStatusUpdateTask;
	protected OFStatisticsRequest allPortStatRequest;
	
	protected Map<RouteId, ArrayList<Route>> routeMaps;
	protected Map<NodePortTuple, OFPortStatisticsReply> linkStatusMap;
	
	
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
				logger.debug("returned routes if null");
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
		initTopologyListener();
		routeMaps = new HashMap<RouteId, ArrayList<Route>>();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApiService.addRestletRoutable(new TrafficEngRoutable());
		topologyService.addListener(topologyListener);
		//��һ���̣߳����ڸ��¸�·����Port��ʹ�����
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		linkStatusUpdateTask = new SingletonTask(ses, new LinkStatusUpdateWorker());
		linkStatusUpdateTask.reschedule(0, TimeUnit.MILLISECONDS);
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
	// private methods
	// ---------------
	private void initTopologyListener(){
		topologyListener = new ITopologyListener() {
			@Override
			public void topologyChanged() {
				routeMaps.clear();
			}
		};
	}

}
