package nathan.zhu.sdn.debug;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyService;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DebugModule implements IFloodlightModule,INDebugService {
	
	protected static Logger logger;

	protected IRestApiService restApi;
	protected IFloodlightProviderService floodlightProvider;
	protected IDeviceService deviceService;
	protected ILinkDiscoveryService linkService;
	protected ITopologyService topologyService;
	
	//
	// INDebugService
	//
	
	@Override
	public void debug() {
		logger.debug("Nathan Debug called");
		
		// ��IFloodlightProviderService�л�����ӵ�����Switch
		Map<Long, IOFSwitch> switches = floodlightProvider.getSwitches();
		Collection<Long> sw_keys = switches.keySet();
		// sw_keys������switches��switch.getId()
		Iterator<Long> sw_key_iter = sw_keys.iterator();
		while (sw_key_iter.hasNext()){
			Long sw_id = sw_key_iter.next();
			IOFSwitch sw = switches.get(sw_id);
			// ���switch��Ϣ
			logger.debug("Got Switch: {}",sw);
			
			// �˴����Ը���OFStatistics
			boolean ofp_desc_stats=true;
			boolean ofp_flow_stat = true;
			// ofp_desc_stat
			if (ofp_desc_stats){
				OFStatisticsRequest request = new OFStatisticsRequest();
				request.setStatisticType(OFStatisticsType.DESC);
				try {
					logger.debug("request desc future");
					Future<List<OFStatistics>> future = sw.getStatistics(request);
					List<OFStatistics> list = future.get(10, TimeUnit.SECONDS);
					for (int i=0; i<list.size(); i++){
						OFDescriptionStatistics desc = (OFDescriptionStatistics)list.get(i);
						logger.info("Got Desc: {}",desc);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					e.printStackTrace();
				}				
			}
			// ofp_flow_stat
			if (ofp_flow_stat){
				// flow request body
				OFFlowStatisticsRequest flow_request = new OFFlowStatisticsRequest();
				OFMatch match = new OFMatch();
				flow_request.setMatch(match);
				flow_request.setOutPort(OFPort.OFPP_NONE.getValue());
				flow_request.setTableId((byte) 0xFF);
				// statistics request
				OFStatisticsRequest request = new OFStatisticsRequest();
				request.setStatisticType(OFStatisticsType.FLOW);
				request.setStatistics(Collections.singletonList((OFStatistics)flow_request));
				// �ⲽ�ǳ���Ҫ����Ҫ��ʾ������request�ĳ��ȣ����򽫳���Խ�����
				int request_length = request.getLength() + flow_request.getLength();
				request.setLengthU(request_length);
				try{
					logger.debug("request flow future");
					Future<List<OFStatistics>> future = sw.getStatistics(request);
					List<OFStatistics> list = future.get(10, TimeUnit.SECONDS);
					for (int i=0; i<list.size(); i++){
						OFFlowStatisticsReply flow = (OFFlowStatisticsReply)list.get(i);
						logger.info("Got Flow: {}",flow);
					}
				} catch (IOException e){
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
			}
			
			
		}
		
		// obtain all attached devices from IDeviceService
		Collection<? extends IDevice> devices = deviceService.getAllDevices();
		Iterator<? extends IDevice> dev_iter = devices.iterator();
		while (dev_iter.hasNext()){
			IDevice dev= dev_iter.next();
			logger.info("Got Device: {}",dev);
		}
		// obtain all links between switches from ILinkDiscoveryService
		Map<Link, LinkInfo> link_map = linkService.getLinks();
		Collection<Link> links = link_map.keySet();
		// links contains all links, and it can be used as key to get its corresponding LinkInfo
		Iterator<Link> link_iter = links.iterator();
		while (link_iter.hasNext()){
			Link link = link_iter.next();
			logger.info("Got Link: {}"+link);
		}
		
	}

	//
	// IFloodlightModule
	//
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// ģ���ṩ�ķ���
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class <? extends IFloodlightService>>();
		l.add(INDebugService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// ģ���ṩ�����ʵ��
		Map<Class<? extends IFloodlightService>,IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,IFloodlightService>();
        m.put(INDebugService.class, this);	//�Լ�this�����ṩ�����INDebugService����
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// ģ����Ҫ�ķ��񣬴˴�������ҪRestAPI����
		Collection<Class<? extends IFloodlightService>> c = new ArrayList<Class <? extends IFloodlightService>>();
		c.add(IRestApiService.class);
		c.add(IFloodlightProviderService.class);
		c.add(IDeviceService.class);
		c.add(ILinkDiscoveryService.class);
		c.add(ITopologyService.class);
		return c;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// ģ���ʼ����������
		restApi = context.getServiceImpl(IRestApiService.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		linkService = context.getServiceImpl(ILinkDiscoveryService.class);
		topologyService = context.getServiceImpl(ITopologyService.class);
		logger = LoggerFactory.getLogger(DebugModule.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// ��������
		// ��RestAPI������Routable��
		restApi.addRestletRoutable(new DebugRoutable());

	}

}
