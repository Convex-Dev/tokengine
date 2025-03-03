package tokengine.api;

import java.net.URI;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.Method;

import convex.core.Result;
import convex.java.ARESTClient;

public class Client extends ARESTClient {

	public Client(URI host) {
		super(host,"/api/v1/");
	}

	public static Client create(URI uri) {
	
		return new Client(uri);
	}

	public Future<Result> getStatus() {
		SimpleHttpRequest req=SimpleHttpRequest.create(Method.GET, getBaseURI().resolve("status"));
		Future<Result> resultFuture=super.doRequest(req);
		return resultFuture;
	}
}
