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
		// ģ���ṩ�ķ���
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class <? extends IFloodlightService>>();
		l.add(INDebugService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// ģ���ṩ�����ʵ��
		Map<Class<? extends IFloodlightService>,IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,IFloodlightService>();
        m.put(INDebugService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// ģ����Ҫ�ķ��񣬴˴�������ҪRestAPI����
		Collection<Class<? extends IFloodlightService>> c = new ArrayList<Class <? extends IFloodlightService>>();
		c.add(IRestApiService.class);
		return c;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// ģ���ʼ����������
		restApi = context.getServiceImpl(IRestApiService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// ��������
		// ��RestAPI������Routable��
		restApi.addRestletRoutable(new DebugRoutable());

	}

}
