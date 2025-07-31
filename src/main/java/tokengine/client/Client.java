package tokengine.client;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;

import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.util.JSONUtils;
import convex.java.ARESTClient;
import convex.java.HTTPClients;
import tokengine.Fields;
import tokengine.exception.ResponseException;

public class Client extends ARESTClient {

	public Client(URI host) {
		super(host,"/api/v1/");
	}

	/**
	 * Create a client instance for the given host
	 * @param uri Host URI e.g. URI.create("https://foo.org")
	 * @return New client instance
	 */
	public static Client create(URI uri) {
		return new Client(uri);
	}

	/**
	 * Gets the status of the TokEngine server
	 * @return Future for the status result
	 */
	public Future<ACell> getStatus() {
		SimpleHttpRequest req=SimpleHttpRequest.create(Method.GET, getBaseURI().resolve("status"));
		Future<ACell> resultFuture=doJSONRequest(req);
		return resultFuture;
	}
	
	/**
	 * Gets the config of the TokEngine server
	 * @return Future for the config data structure
	 */
	public Future<ACell> getConfig() {
		SimpleHttpRequest req=SimpleHttpRequest.create(Method.GET, getBaseURI().resolve("config"));
		Future<ACell> resultFuture=doJSONRequest(req);
		return resultFuture;
	}
	
	/**
	 * Gets the balance of a token for a holder on an underlying DLT network
	 * @param network The DLT network to query. Can be a network alias or a canonical Chain ID e.g. "eip155:11155111"
	 * @param holder The account address to query
	 * @param assetId The token/asset identifier in CAIP19 format or asset alias
	 * @return Future for the balance as AInteger
	 */
	public CompletableFuture<AInteger> getBalance(String network, String assetId, String holder) {
		// Create the request body structure
		AMap<AString, ACell> source = Maps.of(
			Strings.create("account"), Strings.create(holder),
			Strings.create("network"), Strings.create(network), // Default network, could be parameterised
			Strings.create("token"), Strings.create(assetId)
		);
		AMap<AString, ACell> requestBody = Maps.of(
			Strings.create("source"), source
		);
		
		String jsonBody = JSONUtils.toString(requestBody);
		
		SimpleHttpRequest req = SimpleHttpRequest.create(Method.POST, getBaseURI().resolve("balance"));
		req.setBody(jsonBody, ContentType.APPLICATION_JSON);
		
		CompletableFuture<SimpleHttpResponse> future = HTTPClients.execute(req);
		return future.thenApplyAsync(resp -> {
			int code = resp.getCode();
			if ((code / 100) == 2) {
				ACell result = JSONUtils.parse(resp.getBodyText());
				// The API returns a Result.value(balance), so we need to extract the AInteger
				if (result instanceof AMap) {
					@SuppressWarnings("unchecked")
					AMap<AString, ACell> resultMap = (AMap<AString, ACell>) result;
					ACell value = resultMap.get(Strings.create("value"));
					if (value instanceof AInteger) {
						return (AInteger)value;
					}
				}
				throw new ResponseException("Unexpected response format", resp);
			}
			throw new ResponseException("Failed request with status "+code+" and data "+resp.getBodyText(), resp);
		});
	}
	
	/**
	 * Gets the virtual credit of a token for a holder with TokEngine
	 * @param holder The account address to query
	 * @param network The network to query. Can be a network alias or a canonical Chain ID e.g. "eip155:11155111"
	 * @param assetId The token/asset identifier in CAIP19 format or asset alias
	 * @return Future for the balance as AInteger
	 */
	public CompletableFuture<AInteger> getCredit(String holder, String network, String assetId) {
		// Create the request body structure
		AMap<AString, ACell> source = Maps.of(
			Strings.create("account"), Strings.create(holder),
			Strings.create("network"), Strings.create(network), // Default network, could be parameterised
			Strings.create("token"), Strings.create(assetId)
		);
		AMap<AString, ACell> requestBody = Maps.of(
			Strings.create("source"), source
		);
		
		String jsonBody = JSONUtils.toString(requestBody);
		
		SimpleHttpRequest req = SimpleHttpRequest.create(Method.POST, getBaseURI().resolve("credit"));
		req.setBody(jsonBody, ContentType.APPLICATION_JSON);
		
		CompletableFuture<SimpleHttpResponse> future = HTTPClients.execute(req);
		return future.thenApplyAsync(resp -> {
			int code = resp.getCode();
			if ((code / 100) == 2) {
				ACell result = JSONUtils.parse(resp.getBodyText());
				// The API returns a Result.value(balance), so we need to extract the AInteger
				if (result instanceof AMap) {
					@SuppressWarnings("unchecked")
					AMap<AString, ACell> resultMap = (AMap<AString, ACell>) result;
					ACell value = resultMap.get(Fields.VALUE);
					if (value instanceof AInteger) {
						return (AInteger)value;
					} else if (value==null) {
						return CVMLong.ZERO;
					}
				}
				throw new ResponseException("Unexpected response format: "+result, resp);
			}
			throw new ResponseException("Failed request with status "+code+" and data "+resp.getBodyText(), resp);
		});
	}
	
	public CompletableFuture<AInteger> deposit(Object txID, String holder, String network, String assetId) {
		AMap<AString, ACell> source = Maps.of(
			Fields.NETWORK, Strings.create(network), // Default network, could be parameterised
			Fields.TOKEN, Strings.create(assetId),
			Fields.ACCOUNT, Strings.create(holder)
		);
		AMap<AString, ACell> requestBody = Maps.of(
			Fields.SOURCE, source,
			Fields.DEPOSIT,Maps.of(Fields.TX,txID)
		);

		String jsonBody = JSONUtils.toString(requestBody);
		
		SimpleHttpRequest req = SimpleHttpRequest.create(Method.POST, getBaseURI().resolve("deposit"));
		req.setBody(jsonBody, ContentType.APPLICATION_JSON);
		
		CompletableFuture<SimpleHttpResponse> future = HTTPClients.execute(req);
		return future.thenApplyAsync(resp -> {
			int code = resp.getCode();
			if ((code / 100) == 2) {
				ACell result = JSONUtils.parse(resp.getBodyText());
				// The API returns a Result.value(balance), so we need to extract the AInteger
				if (result instanceof AMap) {
					@SuppressWarnings("unchecked")
					AMap<AString, ACell> resultMap = (AMap<AString, ACell>) result;
					ACell value = resultMap.get(Fields.VALUE);
					if (value instanceof AInteger) {
						return (AInteger)value;
					} else if (value==null) {
						return CVMLong.ZERO;
					}
				}
				throw new ResponseException("Unexpected response format: "+result, resp);
			}
			
			throw new ResponseException("Failed request with status "+code+" and data "+resp.getBodyText(), resp);
		});
	}
	
	/**
	 * Makes a HTTP request as a CompletableFuture
	 * @param request Request object
	 * @return Future to be filled with JSON response.
	 */
	protected CompletableFuture<ACell> doJSONRequest(SimpleHttpRequest request) {
		CompletableFuture<SimpleHttpResponse> future=HTTPClients.execute(request);
		return future.thenApplyAsync(resp->{
			int code=resp.getCode();
			if ((code/100)==2) {
				return JSONUtils.parse(resp.getBodyText());
			}
			throw new ResponseException("Failed request",resp); 
		});
	}

	public CompletableFuture<AInteger> payout(String fromUser, String fromNetwork, String fromToken, String toUser, String toNetwork, String toToken,String quantity) {
		AMap<AString, ACell> source = Maps.of(
			Fields.NETWORK, Strings.create(fromNetwork), // Default network, could be parameterised
			Fields.TOKEN, Strings.create(fromToken),
			Fields.ACCOUNT, Strings.create(fromUser)
		);
		AMap<AString, ACell> dest = Maps.of(
			Fields.NETWORK, Strings.create(toNetwork), // Default network, could be parameterised
			Fields.TOKEN, Strings.create(toToken),
			Fields.ACCOUNT, Strings.create(toUser)
		);
		AMap<AString, ACell> requestBody = Maps.of(
			Fields.SOURCE, source,
			Fields.DESTINATION,dest,
			Fields.QUANTITY,quantity
		);

		SimpleHttpRequest req = SimpleHttpRequest.create(Method.POST, getBaseURI().resolve("payout"));
		String jsonBody = JSONUtils.toString(requestBody);
		req.setBody(jsonBody, ContentType.APPLICATION_JSON);
		
		CompletableFuture<SimpleHttpResponse> future = HTTPClients.execute(req);
		return future.thenApplyAsync(resp -> {
			int code = resp.getCode();
			if ((code / 100) == 2) {
				ACell result = JSONUtils.parse(resp.getBodyText());
				// The API returns a Result.value(balance), so we need to extract the AInteger
				if (result instanceof AMap) {
					@SuppressWarnings("unchecked")
					AMap<AString, ACell> resultMap = (AMap<AString, ACell>) result;
					ACell value = resultMap.get(Fields.VALUE);
					if (value instanceof AInteger) {
						return (AInteger)value;
					} else if (value==null) {
						return CVMLong.ZERO;
					}
				}
				throw new ResponseException("Unexpected response format: "+result, resp);
			}
			
			throw new ResponseException("Failed request with status "+code+" and data "+resp.getBodyText(), resp);
		});
	}
}
