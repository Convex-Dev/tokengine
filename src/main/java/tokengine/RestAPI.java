package tokengine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.ContentTypes;
import convex.core.Result;
import convex.core.cvm.Symbols;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.json.JSON5Reader;
import convex.core.lang.RT;
import convex.core.util.JSONUtils;
import convex.java.JSON;
import io.javalin.Javalin;
import io.javalin.http.PaymentRequiredResponse;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExampleProperty;
import io.javalin.openapi.OpenApiRequestBody;
import tokengine.adapter.AAdapter;
import tokengine.model.BalanceRequest;
import tokengine.model.DepositRequest;
import tokengine.model.PayoutRequest;
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
		
		javalin.post(ROUTE + "credit", this::getCredit);

		
		javalin.post(ROUTE + "transfer", this::postTransfer);
		javalin.post(ROUTE + "payout", this::postPayout);
		javalin.post(ROUTE + "wrap", this::postWrap);
		javalin.post(ROUTE + "deposit", this::postDeposit);
		
		javalin.get(ROUTE + "config", this::getConfig);

	}

	
	@OpenApi(path = ROUTE + "status", 
			methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get a quick TokEngine status report", operationId = "status")
	protected void getStatus(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result(JSONUtils.toString(engine.getStatus()));
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "config", 
			methods = HttpMethod.GET, tags = {
			TOKENGINE_TAG }, summary = "Get the tokengine configuration", operationId = "config")
	protected void getConfig(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result(JSON.toString(engine.getConfig()));
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "adapters", 
			methods = HttpMethod.GET, tags = {
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
	
	@OpenApi(path = ROUTE + "balance", 
			methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Queries the on-chain balance of a token", operationId = "balance",
					requestBody = @OpenApiRequestBody(
							description = "Balance request, must provide a network (alias or CAIP-2 chainID), token (symbol alias or CAIP-19 token ID) and an address. TokEngine aliases and defined symbols may be used.", 
							content = {@OpenApiContent(
											from = BalanceRequest.class,  
											type = "application/json", 
											exampleObjects = {
													@OpenApiExampleProperty(name = "source", objects= {
															@OpenApiExampleProperty(name = "account", value="#11"),
															@OpenApiExampleProperty(name = "network", value="convex"),
															@OpenApiExampleProperty(name = "token", value="CVM")
													}) })}
						)		)
	protected void getBalance(Context ctx) {
		AMap<AString,ACell> req=parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Fields.SOURCE));
		if (src==null) throw new BadRequestResponse("Expected 'source' object specifying token");
		
		ACell network=src.get(Fields.NETWORK);
		if (network==null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID=RT.str(network).toString();
		AAdapter<?> adapter=engine.getAdapter(chainID);
		if (adapter==null) throw new BadRequestResponse("Can't find network: "+chainID);
		try {
			String token=RT.str(src.get(Fields.TOKEN)).toString();
			String address=RT.str(src.get(Fields.ACCOUNT)).toString();
			AInteger bal=adapter.getBalance(token,address);
			log.info("Querying balance on network: "+chainID +" token: "+token+" account: "+address + " bal="+bal);
			prepareResult(ctx,Result.value(bal));
		} catch (IOException e) {
			throw new BadRequestResponse(e.toString());
		}

	}
	
	@OpenApi(path = ROUTE + "credit", 
			methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Queries the virtual balance of a token", operationId = "balance",
					requestBody = @OpenApiRequestBody(
							description = "TokEngine virtual Balance request, must provide a token (CAIP-19) and an address. TokEngine aliases and defined symbols may be used.", 
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
	protected void getCredit(Context ctx) {
		AMap<AString,ACell> req=parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Fields.SOURCE));
		if (src==null) throw new BadRequestResponse("Expected 'source' object specifying token");
		
		ACell network=src.get(Fields.NETWORK);
		if (network==null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID=RT.str(network).toString();
		AAdapter<?> adapter=engine.getAdapter(chainID);
		if (adapter==null) throw new BadRequestResponse("Can't find network: "+chainID);
		try {
			String token=RT.str(src.get(Fields.TOKEN)).toString();
			String address=RT.str(src.get(Fields.ACCOUNT)).toString();
			AInteger bal=adapter.getBalance(token,address);
			log.info("Querying balance on network: "+chainID +" token: "+token+" account: "+address + " bal="+bal);
			prepareResult(ctx,Result.value(bal));
		} catch (IOException e) {
			throw new BadRequestResponse(e.getMessage());
		}

	}
	
	
	private AMap<AString,ACell> parseRequest(Context ctx) {
		try {
			ACell data=JSON5Reader.read(ctx.bodyInputStream());
			AMap<AString,ACell> m=RT.ensureMap(data);
			if (m==null) throw new BadRequestResponse("JSON Object expected as request body");
			return m;
		} catch (Exception e) {
			throw new BadRequestResponse("JSON Parsing failed: "+e.getMessage());
		}
	}


	@OpenApi(path = ROUTE + "transfer", 
			methods = HttpMethod.POST, tags = {
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
		AMap<AString,ACell> req=parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Strings.create("destination")));
		if (src==null) throw new BadRequestResponse("Expected 'destination' object specifying token");
		AInteger q= AInteger.parse(req.get(Strings.create("quantity")));
		if (q==null) throw new BadRequestResponse("Expected 'quantity' as valid integer amount");
		
		ACell network=src.get(Strings.create("network"));
		if (network==null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID=RT.str(network).toString();
		AAdapter<?> adapter=engine.getAdapter(chainID);
		if (adapter==null) throw new BadRequestResponse("Can't find network: "+chainID);
		
		String token=RT.str(src.get(Strings.create("token"))).toString();
		String address=RT.str(src.get(Strings.create("account"))).toString();
		Result r=adapter.payout(token,q,address);
		
		log.info("Paying out on network: "+chainID +" token: "+token+" account: "+address + " quantity="+q);
		prepareResult(ctx,r);
	}
	
	@OpenApi(path = ROUTE + "payout", 
			methods = HttpMethod.POST, tags = {
			TOKENGINE_TAG }, summary = "Payout a quantity of owned tokens", operationId = "payout",
					requestBody = @OpenApiRequestBody(
							description = "Payout request, must provide a destination and quantity", 
							content = {@OpenApiContent(
											from = PayoutRequest.class,  
											type = "application/json", 
											exampleObjects = {
													@OpenApiExampleProperty(name = "destination", objects= {
															@OpenApiExampleProperty(name = "account", value="#12"),
															@OpenApiExampleProperty(name = "network", value="convex:test"),
															@OpenApiExampleProperty(name = "token", value="CVM")
													}),
													@OpenApiExampleProperty(name = "quantity", value = "1000000") })}
						))
	protected void postPayout(Context ctx) {
		AMap<AString,ACell> req=parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Fields.DESTINATION));
		if (src==null) throw new BadRequestResponse("Expected 'destination' object specifying token");
		AInteger q= AInteger.parse(req.get(Fields.QUANTITY));
		if (q==null) throw new BadRequestResponse("Expected 'quantity' as valid integer amount");
		
		ACell network=src.get(Fields.NETWORK);
		if (network==null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID=RT.str(network).toString();
		AAdapter<?> adapter=engine.getAdapter(chainID);
		if (adapter==null) throw new BadRequestResponse("Can't find network: "+chainID);
		
		String token=RT.str(src.get(Fields.TOKEN)).toString();
		String address=RT.str(src.get(Fields.ACCOUNT)).toString();
		Result r=adapter.payout(token,q,address);
		
		log.info("Paying out on network: "+chainID +" token: "+token+" account: "+address + " quantity="+q);
		prepareResult(ctx,r);
	}
	
	
	
	@OpenApi(path = ROUTE + "wrap", 
			methods = HttpMethod.POST, 
			tags = {TOKENGINE_TAG }, 
			summary = "Wrap a quantity of tokens", operationId = "wrap")
	protected void postWrap(Context ctx) {
		ctx.header("Content-type", ContentTypes.JSON);
		ctx.result("{\"status\":\"OK\"}");
		ctx.status(200);
	}
	
	@OpenApi(path = ROUTE + "deposit", 
			methods = HttpMethod.POST, 
			tags = {TOKENGINE_TAG }, 
			summary = "Deposit tokens into the system", 
			operationId = "deposit",
			requestBody = @OpenApiRequestBody(
					description = "Deposit request, must provide a source and despsit proof", 
					content = {@OpenApiContent(
									type = "application/json", 
									from = DepositRequest.class,  
									exampleObjects = {
											@OpenApiExampleProperty(name = "source", objects= {
													@OpenApiExampleProperty(name = "account", value="0xab16a96D359eC26a11e2C2b3d8f8B8942d5Bfcdb"),
													@OpenApiExampleProperty(name = "network", value="eip155:11155111"),
													@OpenApiExampleProperty(name = "token", value="0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238")
											}),
											@OpenApiExampleProperty(name = "deposit", objects= {
													@OpenApiExampleProperty(name = "tx", value="0x9d3a3663d32b9ff5cf2d393e433b7b31489d13b398133a35c4bb6e2085bd8e83"),
													@OpenApiExampleProperty(name = "msg", value="\u0019Ethereum Signed Message:\nHello"),
													@OpenApiExampleProperty(name = "sig", value="0xdd48188b1647010d908e9fed4b6726cebd0d65e20f412b8b9ff4868386f05b0a28a9c0e35885c95e2322c2c670743edd07b0e1450ae65c3f6708b61bb3e582371c")
											}) 
									})}
				))
	protected void postDeposit(Context ctx) {
		AMap<AString,ACell> req = parseRequest(ctx);
		AMap<AString,ACell> src = RT.ensureMap(req.get(Strings.create("source")));
		if (src == null) throw new BadRequestResponse("Expected 'source' object specifying token");
		//AInteger q = AInteger.parse(req.get(Strings.create("quantity")));
		//if (q == null) throw new BadRequestResponse("Expected 'quantity' as valid integer amount");
		
		AMap<AString,ACell> dep = RT.ensureMap(req.get(Strings.create("deposit")));
		if (dep == null) throw new BadRequestResponse("Expected 'deposit' object specifying transaction");

		ACell network = src.get(Strings.create("network"));
		if (network == null) throw new BadRequestResponse("Expected 'network' property for source");
		String chainID = RT.str(network).toString();
		AAdapter<?> adapter = engine.getAdapter(chainID);
		if (adapter == null) throw new BadRequestResponse("Can't find network: " + chainID);
		
		// Validate token
		AString tokenAS=RT.ensureString(src.get(Fields.TOKEN));
		if (tokenAS == null) throw new BadRequestResponse("Expected 'source.token' value specifying token");
		String token = tokenAS.toString();
		
		// Validate sender address
		AString addressAS=RT.ensureString(src.get(Fields.ACCOUNT));
		if (addressAS == null) throw new BadRequestResponse("Expected 'source.account' value specifying account on network "+chainID);
		String address = addressAS.toString();
			
		// Check transaction validity
		try {
			engine.makeDeposit(adapter,token,address,dep);
		} catch (Exception e) {
			throw new PaymentRequiredResponse("Could not confirm deposit: "+e.getMessage());
		}
		
		// For now, we'll treat deposit similar to a transfer, using the engine's transfer functionality
		Result r = Result.value(Symbols.FOO);
		prepareResult(ctx, r);
	}
}
