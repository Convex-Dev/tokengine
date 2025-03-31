package tokengine;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.Result;
import tokengine.api.Client;


@TestInstance(Lifecycle.PER_CLASS)
public class APITest {
	
	public static int PORT=8081;
	
	private APIServer venueServer;

	private Engine engine;

	@BeforeAll
	public void setupServer() throws Exception {
		engine=new Engine();
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
	
	

	
	@AfterAll
	public void shutDown() {
		venueServer.close();
	}
}

