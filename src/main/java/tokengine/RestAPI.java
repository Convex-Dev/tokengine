package tokengine;

import java.util.ArrayList;
import java.util.HashMap;

import convex.api.ContentTypes;
import convex.java.JSON;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExampleProperty;
import io.javalin.openapi.OpenApiRequestBody;
import tokengine.model.TransferRequest;

public class RestAPI extends ATokengineAPI {
	
	private static final String TOKENGINE_TAG="TokEngine";
	
	protected Engine engine;

	public RestAPI(Engine engine) {
		this.engine=engine;
	}

	private static final String ROUTE = "/api/v1/";

	@Override
	public void addRoutes(Javalin javalin) {
		javalin.get(ROUTE + "status", this::getStatus);
		javalin.get(ROUTE + "adapters", this::getAdapters);

		javalin.post(ROUTE + "balance", this::getBalance);

		javalin.post(ROUTE + "transfer", this::postTransfer);
		javalin.post(ROUTE + "wrap", this::postWrap);
	}

	
	@OpenApi(path = ROUTE + "status", methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get a quick Tokengine status report", operationId = "status")
	protected void getStatus(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "adapters", methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get a lisp of current adapters installed", operationId = "adapters")
	protected void getAdapters(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		
		ArrayList<?> handlers=engine.getHandlers();
		HashMap<String,Object> data=new HashMap<>();
		data.put("items",handlers);
		data.put("count",handlers.size());
		ctx.result(JSON.toString(data));
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "balance", methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Queries the balance of a token", operationId = "balance")
	protected void getBalance(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "transfer", methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Transfer a quantity of equivalent tokens", operationId = "transfer",
					requestBody = @OpenApiRequestBody(
							description = "Transfer request, must provide a source, destination and quantity", 
							content = {@OpenApiContent(
											from = TransferRequest.class,  
											type = "application/json", 
											exampleObjects = {
													@OpenApiExampleProperty(name = "source", objects= {
															@OpenApiExampleProperty(name = "account", value="#11"),
															@OpenApiExampleProperty(name = "network", value="convex"),
															@OpenApiExampleProperty(name = "token", value="CVM")
													}),
													@OpenApiExampleProperty(name = "destination", objects= {
															@OpenApiExampleProperty(name = "account", value="#12"),
															@OpenApiExampleProperty(name = "network", value="convex"),
															@OpenApiExampleProperty(name = "token", value="WCVM")
													}),
													@OpenApiExampleProperty(name = "quantity", value = "1000") })}
						))
	protected void postTransfer(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "wrap", methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Wrap a quantity of tokens", operationId = "wrap")
	protected void postWrap(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
}
