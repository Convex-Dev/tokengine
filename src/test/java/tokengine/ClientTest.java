package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

import convex.api.Convex;
import convex.core.Result;
import convex.core.crypto.ASignature;
import convex.core.cvm.Address;
import convex.core.cvm.transactions.ATransaction;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Hash;
import convex.core.data.Maps;
import convex.core.data.SignedData;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.util.ConfigUtils;
import convex.core.util.TXUtils;
import tokengine.adapter.AAdapter;
import tokengine.client.Client;

@TestInstance(Lifecycle.PER_CLASS)
public class ClientTest {

	int TEST_PORT=8999;
	Engine engine=null;
	APIServer server=null;
	Client client=null;
	
	@BeforeAll public void setup() throws Exception {
		AMap<AString,ACell> config=ConfigUtils.readConfig(EngineTest.class.getResourceAsStream("/tokengine/config-test.json"));
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
	
	/**
	 *  This is a USDC transaction on Sepolia to the defined receiver account in confio-test.json:
	 *  - 0x06eEB4bb0BC58671d097824611F76fe50C5dB075
	 * @throws InterruptedException 
	 * @throws TimeoutException 
	 * @throws IOException 
	 */
	@Test public void testE2ETransfer() throws IOException, TimeoutException, InterruptedException {
		AInteger HOLDING=CVMLong.create(1000000000);
		Convex convex=engine.getConvex();
		Address user=ConvexTest.distributeWCVM(HOLDING, convex);
		Address user2=ConvexTest.distributeWCVM(HOLDING, convex);

		Address receiver=(Address) engine.getAdapter(Strings.create("convex")).getReceiverAddress();
		assertNotNull(receiver);
		
		// Convex connection for user
		Convex cc=Convex.connect(convex.getHostAddress(),user,ConvexTest.TEST_KP);
		Result r=cc.transactSync("(@convex.asset/transfer "+receiver+" [@asset.wrap.convex 1090])");
		Hash h=TXUtils.getTransactionID(r);
		assertNotNull(h);
		
		AInteger dep=client.deposit(h, user.toString(), "convex", "cad29:72").join();
		assertEquals(1090,dep.longValue());
		
		{ // test initial credit
			AInteger credit=client.getCredit(user.toString(), "convex", "cad29:72").join();
			assertEquals(1090,credit.longValue());
		}

		AInteger payout=client.payout(user.toString(),"convex", "cad29:72", user2.toString(),"convex", "cad29:72","500").join();
		assertEquals(500,payout.longValue());
		
		{ // test final credit
			AInteger credit=client.getCredit(user.toString(), "convex", "cad29:72").join();
			assertEquals(1090-500,credit.longValue());
		}

		AInteger destBal=client.getBalance("convex", "cad29:72", user2.toString()).join();
		assertEquals(1000000500,destBal.longValue());
	}
	
	@Test public void testTransfer() throws IOException, TimeoutException, InterruptedException {
		AInteger HOLDING=CVMLong.create(1000000000);
		Convex convex=engine.getConvex();
		Address user=ConvexTest.distributeWCVM(HOLDING, convex);
		Address user2=ConvexTest.distributeWCVM(HOLDING, convex);
		assertNotNull(user);

		Address receiver=(Address) engine.getAdapter(Strings.create("convex")).getReceiverAddress();
		assertNotNull(receiver);
		
		Convex cc=Convex.connect(convex.getHostAddress(),user,ConvexTest.TEST_KP);
		Result r=cc.transactSync("(@convex.asset/transfer "+receiver+" [@asset.wrap.convex 2000])");
		Hash h=TXUtils.getTransactionID(r);
		assertNotNull(h);
		
		String token="WCVM"; // refers to cad29:72

		{ // test initial credit
			AInteger credit=client.getCredit(user.toString(), "convex", "cad29:72").join();
			assertEquals(0,credit.longValue());
		}
		
		AAdapter<?> adapter=engine.getAdapter(Strings.create("convex"));
		AInteger dep=engine.makeDeposit(adapter, token, user.toString(), Maps.of(Fields.TX,h.toCVMHexString()));
		assertEquals(2000,dep.longValue());
		
		{ // test credit after deposit
			AInteger credit=client.getCredit(user.toString(), "convex", "cad29:72").join();
			assertEquals(2000,credit.longValue());
		}
		
		AString msg=Strings.create("Transfer 2000 to "+user2);
		ASignature sig=cc.getKeyPair().sign(msg.toFlatBlob());
		AMap<AString,ACell> depProof=Maps.of(
			Fields.SIG,sig,
			Fields.MSG,msg
		);
		AString r2=engine.makePayout(user2.toString(), "slip44:864", adapter, CVMLong.create(500),depProof);
		// System.out.println("Payout Result: "+r2);
		Hash txID=Hash.parse(r2);
		SignedData<ATransaction> tx=engine.getPeer().getTransaction(txID);
		assertEquals(txID,tx.getHash());
		// System.out.println("Payout Transaction: "+tx);
	}
	
	@AfterAll public void shutdown() {
		server.close();
		engine.close();
	}
}
