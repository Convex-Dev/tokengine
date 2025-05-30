package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.Result;
import convex.core.data.ACell;
import convex.java.HTTPClients;
import tokengine.api.Client;


@TestInstance(Lifecycle.PER_CLASS)
public class APITest {
	
	public static int PORT=8081;
	
	private APIServer venueServer;

	private Engine engine;

	@BeforeAll
	public void setupServer() throws Exception {
		ACell config=null;
		
		// JSONUtils.parse(Utils.readResourceAsString("/tokengine/config-test.json"));
		engine=new Engine(config);
		engine.start();
		venueServer=APIServer.create(engine);
		venueServer.start(PORT);
	}
	
	@Test public void testAddAsset() throws InterruptedException, ExecutionException {
		Client client=Client.create(URI.create("http://localhost:"+PORT));
		
		Future<Result> r=client.getStatus();
		
		Result result=r.get();
		assertFalse(result.isError(),()->"Bad Result: "+result);
	}
	
	@Test public void testBalance() throws InterruptedException, ExecutionException {
		Client client=Client.create(URI.create("http://localhost:"+PORT));
		
		Future<Result> r=client.getStatus();
		
		Result result=r.get();
		assertFalse(result.isError(),()->"Bad Result: "+result);
	}

	@Test public void testAPIDoc() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
		SimpleHttpRequest req=SimpleHttpRequest.create(Method.GET, new URI("http://localhost:"+PORT+"/openapi-plugin/openapi-tokengine.json?v=default"));
		CompletableFuture<SimpleHttpResponse> future=HTTPClients.execute(req);
		SimpleHttpResponse resp=future.get(10000,TimeUnit.MILLISECONDS);
		assertEquals(200,resp.getCode(),()->"Got error response: "+resp);
	}
	
	@Test public void testWebApp() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
		SimpleHttpRequest req=SimpleHttpRequest.create(Method.GET, new URI("http://localhost:"+PORT));
		CompletableFuture<SimpleHttpResponse> future=HTTPClients.execute(req);
		SimpleHttpResponse resp=future.get(10000,TimeUnit.MILLISECONDS);
		assertEquals(200,resp.getCode(),()->"Got error response: "+resp);
	}

	

	
	@AfterAll
	public void shutDown() {
		venueServer.close();
	}
}

