package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import convex.api.Convex;
import convex.core.crypto.AKeyPair;
import convex.core.cvm.Address;
import convex.core.data.ACell;
import convex.core.data.prim.AInteger;
import convex.core.util.ConfigUtils;
import tokengine.adapter.CVMAdapter;

public class EngineTest {

	@Test public void testDefaultEngine() throws Exception {
		Engine e = Engine.launch(null); // default config
		
		ACell status=e.getStatus();
		assertNotNull(status);
		
		CVMAdapter ad=(CVMAdapter) e.getAdapter("convex:test");
		assertNotNull(ad);
		
		Address ADDR=Address.create(11);
		Convex c=ad.getConvex();
		Long bal=c.getBalance(ADDR);
		
		AInteger a1=ad.getBalance("CVM","11" );
		assertEquals(bal,a1.longValue());
		AInteger a2=ad.getBalance("slip44:864","#11");
		assertEquals(bal,a2.longValue());
		
		e.close();
	}
	
	@Test public void testExampleEngine() throws Exception {
		ACell config=ConfigUtils.readConfigFile("config-example.json");

		Engine e = Engine.launch(config); // default config
		
		
		
		
		e.close();			
	}
	
	@Test public void testCVMAdapter() {
		
	}

}
