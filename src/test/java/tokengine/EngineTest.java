package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.prim.*;
import convex.core.data.Strings;
import convex.core.data.prim.CVMLong;
import convex.core.util.ConfigUtils;

@TestInstance(Lifecycle.PER_CLASS)
public class EngineTest {
	
	Engine engine=null;
	
	@BeforeAll public void setup() throws Exception {
		ACell config=ConfigUtils.readConfig(EngineTest.class.getResourceAsStream("/tokengine/config-test.json"));
		if (config==null) throw new IllegalStateException("Can't have null config for tests");
		engine = Engine.launch(config); // default config
	}
	
	@Test public void testExampleEngine() throws Exception {
		Engine e = engine;
		assertEquals(2,e.getAdapters().size());
		e.close();			
	}
	
	@Test public void testDepositCredit() throws Exception {
		Engine e = engine;
		
		// use "true" as asset key
		AString assetKey=Strings.TRUE;
		AString userKey=Strings.create("ImplausibleTestUser");
		AInteger DEPOSIT=CVMLong.create(666);
		AInteger WITHDRAWAL=CVMLong.create(300);
		
		// Credit should be initially null
		assertNull(e.getVirtualCredit(assetKey, userKey));
		System.out.println(e.getStateSnapshot());
		
		e.addVirtualCredit(assetKey,userKey,DEPOSIT);
		assertEquals(DEPOSIT,e.getVirtualCredit(assetKey, userKey));
		
		
		e.subtractVirtualCredit(assetKey, userKey, WITHDRAWAL);
		assertEquals(DEPOSIT.sub(WITHDRAWAL),e.getVirtualCredit(assetKey, userKey));
	}
	

}
