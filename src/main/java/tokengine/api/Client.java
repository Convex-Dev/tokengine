package tokengine.api;

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
import convex.core.util.JSONUtils;
import convex.java.ARESTClient;
import convex.java.HTTPClients;
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
	 * Gets the balance of a token for a holder
	 * @param network The DLT network to query. Can be a network alias or a canonical Chain ID e.g. "eip155:11155111"
	 * @param holder The account address to query
	 * @param assetId The token/asset identifier in CAIP19 format
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
}
