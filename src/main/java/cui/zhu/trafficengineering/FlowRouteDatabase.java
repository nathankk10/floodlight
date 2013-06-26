package cui.zhu.trafficengineering;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.routing.Route;

import org.openflow.protocol.OFMatch;

public class FlowRouteDatabase {
	
	// Route上跑的流(Route-Match1,Match2……),flowRouteMaps通过flow快速查找routeFlowsMaps中的位置
	// hopefully这样可以增加一定的搜索效率，但是其同步性的问题，就比较麻烦了，需要写几个函数
	private Map<Route, Set<OFMatch>> routeFlowsMaps;
	private Map<OFMatch,Route> flowRouteMaps;
	
	
	public FlowRouteDatabase(){
		routeFlowsMaps = new HashMap<Route,Set<OFMatch>>();;
		flowRouteMaps = new HashMap<OFMatch,Route>();
	}
	
	// ---------------
	// Route Flow 纪录的操作都通过一下方法进行，以保证一致性，增删查改
	// ---------------
	/**
	 * 向记录集中添加一条“flow安排在Route上”
	 * @param match
	 * @param route
	 */
	public void addFlowToRoute (OFMatch match, Route route){
		Set<OFMatch> flows = routeFlowsMaps.get(route);
		// 1. 修改routeFlowMaps
		if (flows == null){
			//如果routeFlowsMap中不存在该route则添加一个route
			flows = new HashSet<OFMatch>();
			flows.add(match);
			routeFlowsMaps.put(route, flows);
		} else {
			//flows是routeFlowMaps中Route对应的flow集合
			flows.add(match);
		}
		// 2. 修改flowRouteMaps
		flowRouteMaps.put(match, route);
	}
	
	/**
	 * 从记录集中删除一个Flow
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
	 * 从数据集中删除一个Route，所有该Route上的flow都会被随带删除
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
	 * @return 数据集中所有Routes
	 */
	public Set<Route> getRoutes(){
		return routeFlowsMaps.keySet();
	}
	
	/**
	 * 数据集中Route上的所有flow
	 * @param route
	 * @return 如果没有，则null
	 */
	public Set<OFMatch> getFlowsOnRoute (Route route){
		return routeFlowsMaps.get(route);
	}
}
