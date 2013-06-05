package nathan.zhu.sdn.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.topology.ITopologyService;

public class DebugModule implements IFloodlightModule,INDebugService {

	protected IRestApiService restApi;
	
	

	
	//
	// INDebugService
	//
	
	@Override
	public void debug() {
		System.out.println("Nathan starts to debug!");
		
	}

	//
	// IFloodlightModule
	//
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// 模块提供的服务
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class <? extends IFloodlightService>>();
		l.add(INDebugService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// 模块提供服务的实例
		Map<Class<? extends IFloodlightService>,IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,IFloodlightService>();
        m.put(INDebugService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// 模块需要的服务，此处首先需要RestAPI服务
		Collection<Class<? extends IFloodlightService>> c = new ArrayList<Class <? extends IFloodlightService>>();
		c.add(IRestApiService.class);
		return c;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// 模块初始化自身内容
		restApi = context.getServiceImpl(IRestApiService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// 启动事宜
		// 向RestAPI中增加Routable类
		restApi.addRestletRoutable(new DebugRoutable());

	}

}
