package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import convex.api.Convex;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.crypto.ASignature;
import convex.core.cvm.Address;
import convex.core.cvm.Keywords;
import convex.core.data.ABlob;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.Blobs;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.util.ConfigUtils;
import tokengine.adapter.convex.CVMAdapter;
import tokengine.exception.PaymentException;

@TestInstance(Lifecycle.PER_CLASS)
public class ConvexTest {
	
	public static AccountKey TEST_KEY=AccountKey.fromHex("b8c4f552c1749315c3347bcd0ceb9594a80bd204edd27645b096f733b1a7855b");
	public static AKeyPair TEST_KP=AKeyPair.create("87b8ae6774ac7892ded2c89d5bce417b7da07b61c81a23e30534e62cde9768a1");
	
	protected Engine engine;

	@BeforeAll
	public void setup() throws Exception {
		String resourcePath="/tokengine/config-test.json";
		AMap<AString,ACell> config=ConfigUtils.readConfig(ConvexTest.class.getResourceAsStream(resourcePath));
		engine = Engine.launch(config); // default config
		assertTrue(engine.isTest());
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
	
	@Test public void testWCVMDeposit() throws InterruptedException, IOException, TimeoutException, PaymentException {
		CVMAdapter ca=(CVMAdapter) engine.getAdapter(Strings.create("convex:test"));
		assertNotNull(ca);
		Address receiverAddress=ca.getReceiverAddress();
		assertNotNull(receiverAddress);
		
		AInteger HOLDING=CVMLong.create(1000000);
		
		// Convex connection for operator
		Convex convex=ca.getConvex();
		
		Address addr = distributeWCVM(HOLDING, convex);
		
		// Convex connection for operator
		AInteger DEPOSIT=CVMLong.create(300000);
		Convex convex2=Convex.connect(convex.getHostAddress(),addr,TEST_KP);
		Result dr=convex2.transactSync("(@convex.asset/transfer "+receiverAddress+" [@asset.wrap.convex "+DEPOSIT+"])");
		ABlob txID=RT.getIn(dr, Keywords.INFO,Keywords.TX);
		// System.out.println(dr);
		
		AInteger deposited=engine.makeDeposit(ca, "WCVM", addr.toString(), Maps.of(Fields.TX,txID.toString()));
		assertEquals(DEPOSIT,deposited);
		
		// duplicate transaction
		assertThrows(PaymentException.class,()->engine.makeDeposit(ca, "WCVM", addr.toString(), Maps.of(Fields.TX,txID.toString())));

		// fictitious tokens
		assertThrows(IllegalArgumentException.class,()->engine.makeDeposit(ca, "BOB", addr.toString(), Maps.of(Fields.TX,txID.toString())));
		assertThrows(IllegalArgumentException.class,()->engine.makeDeposit(ca, "cvm", addr.toString(), Maps.of(Fields.TX,txID.toString())));

		// fictitious tx ID
		assertNull(engine.makeDeposit(ca, "WCVM", addr.toString(), Maps.of(Fields.TX,txID.getHash().toString())));

	}

	public static Address distributeWCVM(AInteger amount, Convex convex)  {
		try {
			// Give a new account some WCVM
			Result r;
				r = convex.transactSync("(do (def t1 (create-account "+TEST_KEY+")) (@convex.asset/transfer t1 [@asset.wrap.convex "+amount+"]) (transfer t1 1000000000) (@convex.asset/balance @asset.wrap.convex t1))");
			assertFalse(r.isError(),()->"Unexpected error: "+r);
			assertEquals(amount,r.getValue());
			Address addr=convex.querySync("t1").getValue();
			
			// Check for valid transaction ID
			{
				ABlob txID=RT.getIn(r, Keywords.INFO,Keywords.TX);
				assertNotNull(txID);
			}
			return addr;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Error(e);
		}

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
	public void testParseTXID() {
		Blob txID=Blobs.createRandom(32);
		CVMAdapter ca = new CVMAdapter(null,convex.core.data.Maps.empty());
		assertEquals(txID,ca.parseTransactionID(txID.print()));
		assertEquals(txID,ca.parseTransactionID(Strings.create(txID.toHexString())));
		assertEquals(txID,ca.parseTransactionID(txID.toCVMHexString()));

	}
	
	@Test
	public void testConvexAction() throws InterruptedException {
		Convex convex=engine.getConvex();
		assertEquals(Init.GENESIS_ADDRESS,convex.getAddress());
		assertEquals(Init.GENESIS_ADDRESS,convex.query("*address*").join().getValue());
		assertEquals(convex.getAccountKey(),convex.transact("*key*").join().getValue());
		
		Result r=convex.transactSync(CVMLong.ONE);
		assertNull(r.getErrorCode(),()->"Failed result: "+r);
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
