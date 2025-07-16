package tokengine.api;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.Method;

import convex.core.data.ACell;
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
