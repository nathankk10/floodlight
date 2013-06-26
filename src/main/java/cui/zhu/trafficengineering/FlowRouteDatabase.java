package cui.zhu.trafficengineering;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.routing.Route;

import org.openflow.protocol.OFMatch;

public class FlowRouteDatabase {
	
	// Route���ܵ���(Route-Match1,Match2����),flowRouteMapsͨ��flow���ٲ���routeFlowsMaps�е�λ��
	// hopefully������������һ��������Ч�ʣ�������ͬ���Ե����⣬�ͱȽ��鷳�ˣ���Ҫд��������
	private Map<Route, Set<OFMatch>> routeFlowsMaps;
	private Map<OFMatch,Route> flowRouteMaps;
	
	
	public FlowRouteDatabase(){
		routeFlowsMaps = new HashMap<Route,Set<OFMatch>>();;
		flowRouteMaps = new HashMap<OFMatch,Route>();
	}
	
	// ---------------
	// Route Flow ��¼�Ĳ�����ͨ��һ�·������У��Ա�֤һ���ԣ���ɾ���
	// ---------------
	/**
	 * ���¼�������һ����flow������Route�ϡ�
	 * @param match
	 * @param route
	 */
	public void addFlowToRoute (OFMatch match, Route route){
		Set<OFMatch> flows = routeFlowsMaps.get(route);
		// 1. �޸�routeFlowMaps
		if (flows == null){
			//���routeFlowsMap�в����ڸ�route�����һ��route
			flows = new HashSet<OFMatch>();
			flows.add(match);
			routeFlowsMaps.put(route, flows);
		} else {
			//flows��routeFlowMaps��Route��Ӧ��flow����
			flows.add(match);
		}
		// 2. �޸�flowRouteMaps
		flowRouteMaps.put(match, route);
	}
	
	/**
	 * �Ӽ�¼����ɾ��һ��Flow
	 * @param match
	 */
	public void deleteFlow (OFMatch match){
		Route route = flowRouteMaps.get(match);
		if (route == null){
			return;
		}
		flowRouteMaps.remove(match);
		Set<OFMatch> flows = routeFlowsMaps.get(route);
		flows.remove(match);
	}
	
	/**
	 * �����ݼ���ɾ��һ��Route�����и�Route�ϵ�flow���ᱻ���ɾ��
	 * @param route
	 */
	public void deleteRoute (Route route){
		Set<OFMatch> flows = routeFlowsMaps.get(route);
		for (OFMatch flow:flows){
			flowRouteMaps.remove(flow);
		}
		routeFlowsMaps.remove(route);
	}
	
	/**
	 * @return ���ݼ�������Routes
	 */
	public Set<Route> getRoutes(){
		return routeFlowsMaps.keySet();
	}
	
	/**
	 * ���ݼ���Route�ϵ�����flow
	 * @param route
	 * @return ���û�У���null
	 */
	public Set<OFMatch> getFlowsOnRoute (Route route){
		return routeFlowsMaps.get(route);
	}
}
