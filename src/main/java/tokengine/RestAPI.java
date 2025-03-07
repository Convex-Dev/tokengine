package tokengine;

import convex.api.ContentTypes;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;

public class RestAPI extends ATokengineAPI {

	private static final String ROUTE = "/api/v1/";

	@Override
	public void addRoutes(Javalin javalin) {
		javalin.get(ROUTE + "status", this::getStatus);

		javalin.get(ROUTE + "transfer", this::postTransfer);
		javalin.get(ROUTE + "wrap", this::postWrap);
	}

	@OpenApi(path = ROUTE + "status", methods = HttpMethod.GET, tags = {
			"Tokengine" }, summary = "Get a quick Tokengine status report", operationId = "status")
	protected void getStatus(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "transfer", methods = HttpMethod.GET, tags = {
	"Tokengine" }, summary = "Transfer a quantity of equivalent tokens", operationId = "transfer")
	protected void postTransfer(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "wrap", methods = HttpMethod.GET, tags = {
	"Tokengine" }, summary = "Wrap a quantity tokens", operationId = "wrap")
	protected void postWrap(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
}
