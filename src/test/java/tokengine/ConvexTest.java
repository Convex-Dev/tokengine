package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.core.crypto.AKeyPair;
import convex.core.crypto.ASignature;
import convex.core.data.AString;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.Strings;
import tokengine.adapter.CVMAdapter;

@TestInstance(Lifecycle.PER_CLASS)
public class ConvexTest {
	
	protected Engine engine;

	@BeforeAll
	public void setup() throws Exception {
		engine = Engine.launch(null); // default config

	}
	
	
	
	@Test public void testCVMSignatures() {
		CVMAdapter ca=(CVMAdapter) engine.getAdapter("convex:test");
		assertNotNull(ca);
		
		AKeyPair kp=AKeyPair.create("5FE2D4E23E8F04E5FF74006A905F989A1C17A0474A7BCB5CF09A73AFF418A605");
		
		AccountKey pk=kp.getAccountKey();
		assertEquals("0x262D6FB95117B038EE785728A190A7D9870EC2D7E68DF9715BDC8D505B718727".toLowerCase(),pk.toString());
		
		String messageText="Test message UTF-8";
		Blob msgBlob=Blob.wrap(messageText.getBytes());
		assertEquals("0x54657374206d657373616765205554462d38",msgBlob.toString());
		ASignature signed=kp.sign(msgBlob);
		
		assertEquals("66CFEDBD4B62155B70EB3F005437BE6551BD831AF4A36EAC4B0685C3ABD51F78634EF528E33E6B05135D73A4072DCA0DA98ECC9BB098791244DC25782C55950B",signed.toHexString().toUpperCase());
		
		assertTrue(ca.verifyPersonalSignature(messageText, signed.toHexString(), pk.toString()));
		
		assertFalse(ca.verifyPersonalSignature("Something else", signed.toHexString(), pk.toString()));

	}
	
	@AfterAll
	public void finish() {
		engine.close();
	}

}
