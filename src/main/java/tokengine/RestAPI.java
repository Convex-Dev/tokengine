package tokengine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.ContentTypes;
import convex.core.Result;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import convex.core.util.JSONUtils;
import convex.java.JSON;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExampleProperty;
import io.javalin.openapi.OpenApiRequestBody;
import tokengine.adapter.AAdapter;
import tokengine.model.BalanceRequest;
import tokengine.model.TransferRequest;

public class RestAPI extends ATokengineAPI {
	
	protected static final Logger log = LoggerFactory.getLogger(RestAPI.class.getName());

	
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
		
		javalin.get(ROUTE + "config", this::getConfig);

	}

	
	@OpenApi(path = ROUTE + "status", methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get a quick TokEngine status report", operationId = "status")
	protected void getStatus(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result(JSONUtils.toString(engine.getStatus()));
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "config", methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get the tokengine configuration", operationId = "config")
	protected void getConfig(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result(JSON.toString(engine.getConfig()));
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "adapters", methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get a list of current DLT adapters installed", operationId = "adapters")
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
			TOKENGINE_TAG }, summary = "Queries the balance of a token", operationId = "balance",
					requestBody = @OpenApiRequestBody(
							description = "Balance request, must provide a token (CAIP-19) and an address", 
							content = {@OpenApiContent(
											from = BalanceRequest.class,  
											type = "application/json", 
											exampleObjects = {
													@OpenApiExampleProperty(name = "source", objects= {
															@OpenApiExampleProperty(name = "account", value="#11"),
															@OpenApiExampleProperty(name = "network", value="convex:main"),
															@OpenApiExampleProperty(name = "token", value="CVM")
													}) })}
						)		)
	protected void getBalance(Context ctx) {
		AMap<AString,ACell> req=parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Strings.create("source")));
		if (src==null) throw new BadRequestResponse("Expected 'source' object specifying token");
		
		ACell network=src.get(Strings.create("network"));
		if (network==null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID=RT.str(network).toString();
		log.info("Querying balance on network: "+chainID);
		AAdapter adapter=engine.getAdapter(chainID);
		if (adapter==null) throw new BadRequestResponse("Can't find network: "+chainID);
		try {
			String token=RT.str(src.get(Strings.create("token"))).toString();
			String address=RT.str(src.get(Strings.create("account"))).toString();
			AInteger bal=adapter.getBalance(token,address);
			prepareResult(ctx,Result.value(bal));
		} catch (IOException e) {
			throw new BadRequestResponse(e.getMessage());
		}

	}
	
	private AMap<AString,ACell> parseRequest(Context ctx) {
		String json=ctx.body();
		try {
			ACell data=JSONUtils.parse(json);
			AMap<AString,ACell> m=RT.ensureMap(data);
			if (m==null) throw new BadRequestResponse("JSON Object expected as request body");
			return m;
		} catch (Exception e) {
			throw new BadRequestResponse("JSON Parsing failed: "+e.getMessage());
		}
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
															@OpenApiExampleProperty(name = "network", value="convex:main"),
															@OpenApiExampleProperty(name = "token", value="CVM")
													}),
													@OpenApiExampleProperty(name = "destination", objects= {
															@OpenApiExampleProperty(name = "account", value="#12"),
															@OpenApiExampleProperty(name = "network", value="convex:main"),
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
