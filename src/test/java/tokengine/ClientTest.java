package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.data.ACell;
import convex.core.data.prim.AInteger;
import convex.core.util.ConfigUtils;
import tokengine.client.Client;

@TestInstance(Lifecycle.PER_CLASS)
public class ClientTest {

	int TEST_PORT=8999;
	Engine engine=null;
	APIServer server=null;
	Client client=null;
	
	@BeforeAll public void setup() throws Exception {
		ACell config=ConfigUtils.readConfig(EngineTest.class.getResourceAsStream("/tokengine/config-test.json"));
		if (config==null) throw new IllegalStateException("Can't have null config for tests");
		engine = Engine.launch(config); // default config
		
		server=APIServer.create(engine);
		server.start(TEST_PORT);
		
		client=Client.create(URI.create("http://localhost:"+TEST_PORT));
	}
	
	@Test public void testStatus() throws InterruptedException, ExecutionException {
		Future<ACell> r=client.getStatus();
		ACell status=r.get();
		assertNotNull(status);
	}
	
	@Test public void testConfig() throws InterruptedException, ExecutionException {
		Future<ACell> r=client.getConfig();
		assertEquals(engine.getConfig(),r.get());
	}
	
	@Test public void testGetBalance() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<AInteger> balanceFuture = client.getBalance("convex", "CVM","#11");
		AInteger balance = balanceFuture.get(10000, TimeUnit.MILLISECONDS);
		assertNotNull(balance);
		assertTrue(balance.isPositive(), "Balance should be greater than zero");
	}

	@Test
	public void testGetBalanceFailsWithFictitiousNetwork() {
		CompletableFuture<AInteger> balanceFuture = client.getBalance("notanetwork", "CVM", "#11");
		ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, () -> {
			balanceFuture.get(5000, TimeUnit.MILLISECONDS);
		});
		assertNotNull(thrown.getCause());
	}

	@Test
	public void testGetBalanceFailsWithGarbageAccount() {
		CompletableFuture<AInteger> balanceFuture = client.getBalance("convex", "CVM", "notanaccount");
		ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, () -> {
			balanceFuture.get(5000, TimeUnit.MILLISECONDS);
		});
		assertNotNull(thrown.getCause());
	}

	@Test
	public void testGetBalanceFailsWithFictitiousTokens() {
		{ // bad token ID format for Convex
			CompletableFuture<AInteger> balanceFuture = client.getBalance("convex", "notatoken", "#11");
			ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, () -> {
				balanceFuture.get(5000, TimeUnit.MILLISECONDS);
			});
			assertNotNull(thrown.getCause());
		}
		
		{ // cad29 token ID, but non-existent asset
			CompletableFuture<AInteger> balanceFuture = client.getBalance("convex", "cad29:9999999", "#11");
			ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class, () -> {
				balanceFuture.get(5000, TimeUnit.MILLISECONDS);
			});
			assertNotNull(thrown.getCause());
		}
	}
	
	@AfterAll public void shutdown() {
		server.close();
		engine.close();
	}
}
