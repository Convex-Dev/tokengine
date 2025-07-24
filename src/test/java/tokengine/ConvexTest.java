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
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.Strings;
import convex.core.util.ConfigUtils;
import tokengine.adapter.CVMAdapter;

@TestInstance(Lifecycle.PER_CLASS)
public class ConvexTest {
	
	protected Engine engine;

	@BeforeAll
	public void setup() throws Exception {
		String resourcePath="/tokengine/config-test.json";
		AMap<AString,ACell> config=ConfigUtils.readConfig(ConvexTest.class.getResourceAsStream(resourcePath));
		engine = Engine.launch(config); // default config

	}
	
	
	
	@Test public void testCVMSignatures() {
		CVMAdapter ca=(CVMAdapter) engine.getAdapter(Strings.create("convex:test"));
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
	
	@Test
	public void testCVMAdapterParseAddress() {
		CVMAdapter adapter = new CVMAdapter(null,convex.core.data.Maps.empty());
		String addrStr = "#12345";
		convex.core.cvm.Address addr = convex.core.cvm.Address.parse(addrStr);
		
		// Test with Address
		convex.core.cvm.Address parsed1 = adapter.parseAddress(addr);
		assertEquals(addr, parsed1);
		
		// Test with String
		convex.core.cvm.Address parsed2 = adapter.parseAddress(addrStr);
		assertEquals(addr, parsed2);
		
		// Test with AString
		convex.core.cvm.Address parsed3 = adapter.parseAddress(convex.core.data.Strings.create(addrStr));
		assertEquals(addr, parsed3);
	}
	
	@Test
	public void testCVMAdapterParseAddressFailureCases() {
		CVMAdapter adapter = new CVMAdapter(null,convex.core.data.Maps.empty());
		
		// Test with invalid address format (non-numeric)
		try {
			adapter.parseAddress("#abc");
			assertTrue(false, "Should throw IllegalArgumentException for non-numeric address");
		} catch (IllegalArgumentException e) {
			// Expected
		}
		
		// Test with empty string
		try {
			adapter.parseAddress("");
			assertTrue(false, "Should throw IllegalArgumentException for empty string");
		} catch (IllegalArgumentException e) {
			// Expected
		}
		
		// Test with null
		try {
			adapter.parseAddress(null);
			assertTrue(false, "Should throw IllegalArgumentException for null");
		} catch (IllegalArgumentException e) {
			// Expected
		}
		
		// Test with invalid object type
		try {
			adapter.parseAddress(123);
			assertTrue(false, "Should throw IllegalArgumentException for invalid object type");
		} catch (IllegalArgumentException e) {
			// Expected
		}
		
		// Test with negative address (as integer string)
		try {
			adapter.parseAddress("-123");
			assertTrue(false, "Should throw IllegalArgumentException for negative address");
		} catch (IllegalArgumentException e) {
			// Expected
		}
		// Test with negative address (as # string)
		try {
			adapter.parseAddress("#-123");
			assertTrue(false, "Should throw IllegalArgumentException for negative address");
		} catch (IllegalArgumentException e) {
			// Expected
		}
	}
	
	@AfterAll
	public void finish() {
		if (engine!=null) engine.close();
	}

}
