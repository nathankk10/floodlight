package cui.zhu.trafficengineering;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class TrafficEngRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/debug",TrafficEngResource.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/traffic";
	}

}
