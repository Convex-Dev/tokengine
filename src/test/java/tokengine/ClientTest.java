package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.data.ACell;
import convex.core.util.ConfigUtils;
import tokengine.api.Client;

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
	
	@AfterAll public void shutdown() {
		server.close();
		engine.close();
	}
}
