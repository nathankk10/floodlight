package cui.zhu.trafficengineering;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class TrafficEngResource extends ServerResource {
	
	@Get
	@Post
	public void debug (){
		ICZTrafficEngService trafficEngService = (ICZTrafficEngService) getContext().getAttributes().get(ICZTrafficEngService.class.getCanonicalName());
		trafficEngService.debug();
	}

}
