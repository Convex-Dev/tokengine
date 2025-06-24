package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import convex.core.data.ACell;
import convex.core.util.ConfigUtils;

public class EngineTest {
	
	@Test public void testExampleEngine() throws Exception {
		ACell config=ConfigUtils.readConfigFile("config-example.json");
		
		Engine e = Engine.launch(config); // default config
		
		assertEquals(2,e.getAdapters().size());
		
		e.close();			
	}
	

}
