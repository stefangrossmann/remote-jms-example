package stefan.demo.jms.boundary;

import stefan.demo.jms.control.DemoService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("demo")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Stateless
public class DemoResource {
    @Inject
    private DemoService demoCachedService;

    @POST @Path("message/topic")
    public Response postTopic(@QueryParam("message") @DefaultValue("Hello World") String message) {
        demoCachedService.sendTextMessageToAll(message);
        return Response.ok().build();
    }

    @POST @Path("message/queue")
    public Response postQueue(@QueryParam("message") @DefaultValue("Hello World") String message) {
        demoCachedService.sendTextMessageToOne(message);
        return Response.ok().build();
    }
}