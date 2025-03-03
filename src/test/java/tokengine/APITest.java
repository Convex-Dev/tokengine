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
	
	
	private APIServer venueServer;

	@BeforeAll
	public void setupServer() {
		venueServer=APIServer.create(null);
		venueServer.start(8080);
	}
	
	@Test public void testAddAsset() throws InterruptedException, ExecutionException {
		Client client=Client.create(URI.create("http://localhost:8080"));
		
		Future<Result> r=client.getStatus();
		
		Result result=r.get();
		assertFalse(result.isError(),()->"Bad Result: "+result);
	}

	
	@AfterAll
	public void shutDown() {
		venueServer.close();
	}
}

