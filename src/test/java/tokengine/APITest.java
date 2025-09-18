package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.util.ConfigUtils;

/**
 * These are basically smoke tests for the API server
 */
@TestInstance(Lifecycle.PER_CLASS)
public class APITest {
	
	public static int PORT=8081;
	
	private APIServer venueServer;
	private Engine engine;
	private final HttpClient httpClient;

	public APITest() {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	}

	@BeforeAll
	public void setupServer() throws Exception {
		AMap<AString,ACell> config=ConfigUtils.readConfig(APITest.class.getResourceAsStream("/tokengine/config-test.json"));
		engine=new Engine(config);
		engine.start();
		venueServer=APIServer.create(engine);
		venueServer.start(PORT);
	}
	
	

	@Test public void testAPIDoc() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
		HttpRequest req = HttpRequest.newBuilder()
			.uri(new URI("http://localhost:"+PORT+"/openapi"))
			.GET()
			.build();
		CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> resp = future.get(10000,TimeUnit.MILLISECONDS);
		assertEquals(200,resp.statusCode(),()->"Got error response: "+resp);
	}
	
	@Test public void testWebApp() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
		HttpRequest req = HttpRequest.newBuilder()
			.uri(new URI("http://localhost:"+PORT))
			.GET()
			.build();
		CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> resp = future.get(10000,TimeUnit.MILLISECONDS);
		assertEquals(200,resp.statusCode(),()->"Got error response: "+resp);
	}
	
	@Test public void test404() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
		HttpRequest req = HttpRequest.newBuilder()
			.uri(new URI("http://localhost:"+PORT+"/notanendpoint"))
			.GET()
			.build();
		CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> resp = future.get(10000,TimeUnit.MILLISECONDS);
		assertEquals(404,resp.statusCode(),()->"Got error response: "+resp);
	}

	
	@AfterAll
	public void shutDown() {
		venueServer.close();
	}
}

