package nathan.zhu.sdn.debug;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class DebugResource extends ServerResource {

	@Get
	@Post
	public void debug (){
		INDebugService debugService = (INDebugService) getContext().getAttributes().get(INDebugService.class.getCanonicalName());
		debugService.debug();
	}
}
