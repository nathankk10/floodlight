package nathan.zhu.sdn.debug;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class DebugRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/debug",DebugResource.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/nathan";
	}

}
