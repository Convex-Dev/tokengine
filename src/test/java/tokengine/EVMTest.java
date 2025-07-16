package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.WalletUtils;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import convex.core.crypto.Hashing;
import convex.core.crypto.InsecureRandom;
import convex.core.data.Maps;
import tokengine.adapter.EVMAdapter;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;

public class EVMTest {

    static String nodeUrl = "https://sepolia.drpc.org";
	
	public static void main(String[] args) throws IOException {
        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        
        
        String txHash = "0x9d3a3663d32b9ff5cf2d393e433b7b31489d13b398133a35c4bb6e2085bd8e83"; // Replace with your transaction hash
        TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
        
        System.out.println(receipt);
	}
	
	public static void temp() {
        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        
        // Token contract address
        String contractAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"; // Replace with token contract address
        // Recipient address to monitor
        String recipientAddress = "0x5FbE74A283f7954f10AA04C2eDf55578811aeb03"; // Replace with the address receiving tokens

        // Define the Transfer event
        Event transferEvent = new Event("Transfer",
                Arrays.asList(
                        new TypeReference<Address>(true) {}, // from (indexed)
                        new TypeReference<Address>(true) {}, // to (indexed)
                        new TypeReference<Int>(false) {} // value (non-indexed)
                ));

        // Create a filter for the Transfer event
        EthFilter filter = new EthFilter(
                DefaultBlockParameterName.EARLIEST, // Start block
                DefaultBlockParameterName.LATEST,   // End block
                contractAddress
        );
        filter.addSingleTopic(EventEncoder.encode(transferEvent));
        filter.addSingleTopic(null); // from address (null for any)
        filter.addSingleTopic("0x" + recipientAddress.substring(2).toLowerCase()); // to address

        // Subscribe to the event
        web3j.ethLogFlowable(filter).subscribe(log -> {
            // Extract event data
            String from = log.getTopics().get(1); // Topic 1 is the "from" address
            String to = log.getTopics().get(2);   // Topic 2 is the "to" address
            BigInteger value = new BigInteger(log.getData().substring(2), 16); // Token amount

            System.out.println("Transfer detected:");
            System.out.println("From: " + from);
            System.out.println("To: " + to);
            System.out.println("Amount: " + value);
        }, error -> {
            error.printStackTrace();
        });
    }
	
	@Test public void testEVMWallet() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException, CipherException {
		ECKeyPair keyPair = Keys.createEcKeyPair(new InsecureRandom(64566754));
		
		// Create credentials from the key pair
		Credentials credentials = Credentials.create(keyPair);
		String originalAddress = credentials.getAddress();
		
		// Create a temporary directory for the wallet
		File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-wallet-dir");
		tempDir.mkdirs();
		tempDir.deleteOnExit();
		
		// Save the wallet to the temporary directory
		WalletUtils.generateWalletFile("test-password", credentials.getEcKeyPair(), tempDir, false);
		
		// Find the generated wallet file
		File[] walletFiles = tempDir.listFiles((dir, name) -> name.endsWith(".json"));
		assertTrue(walletFiles != null && walletFiles.length > 0, "Wallet file should be generated");
		File tempWalletFile = walletFiles[0];
		
		// Load the wallet from the file
		Credentials loadedCredentials = WalletUtils.loadCredentials("test-password", tempWalletFile);
		
		// Verify the restored key is the same as the saved one
		assertEquals(originalAddress, loadedCredentials.getAddress(), "Wallet address should match after save/load");
		assertEquals(keyPair.getPrivateKey(), loadedCredentials.getEcKeyPair().getPrivateKey(), "Private key should match after save/load");
		assertEquals(keyPair.getPublicKey(), loadedCredentials.getEcKeyPair().getPublicKey(), "Public key should match after save/load");
		
		// Clean up
		tempWalletFile.delete();
	}
	
	@Test public void testLoadWalletsFromDirectory() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, IOException, CipherException {
		// Create EVMAdapter instance
		EVMAdapter adapter = EVMAdapter.build(Maps.of(Fields.CHAIN_ID, "eip155:11155111"));
		
		// Create a temporary directory for multiple wallets
		File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-multiple-wallets");
		tempDir.mkdirs();
		tempDir.deleteOnExit();
		
		// Generate multiple wallets
		List<Credentials> originalCredentials = new ArrayList<>();
		String password = "test-password";
		
		for (int i = 0; i < 3; i++) {
			ECKeyPair keyPair = Keys.createEcKeyPair(new InsecureRandom(64566754 + i));
			Credentials credentials = Credentials.create(keyPair);
			originalCredentials.add(credentials);
			// Save wallet to directory
			WalletUtils.generateWalletFile(password, credentials.getEcKeyPair(), tempDir, false);
		}
		
		// Load all wallets from directory using EVMAdapter method
		java.util.HashMap<String, Credentials> loadedMap = adapter.loadWalletsFromDirectory(tempDir, password);
		
		// Verify all wallets were loaded
		assertEquals(originalCredentials.size(), loadedMap.size(), "Should load all wallet files");
		
		// Verify each loaded wallet matches the original by address (no 0x, lowercase)
		for (Credentials original : originalCredentials) {
			String address = original.getAddress();
			if (address.startsWith("0x") || address.startsWith("0X")) address = address.substring(2);
			address = address.toLowerCase();
			Credentials loaded = loadedMap.get(address);
			assertTrue(loaded != null, "Wallet should be loaded for address: " + address);
			assertEquals(original.getAddress().toLowerCase().replaceFirst("^0x", ""), address, "Address format should match");
			assertEquals(original.getEcKeyPair().getPrivateKey(), loaded.getEcKeyPair().getPrivateKey(), "Private key should match");
			assertEquals(original.getEcKeyPair().getPublicKey(), loaded.getEcKeyPair().getPublicKey(), "Public key should match");
		}
		
		// Clean up
		File[] files = tempDir.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
		tempDir.delete();
	}
	
	@Test public void testEVMAdapterStartupWithWallets() throws Exception {
		// Create a test config with key-dir
		AMap<AString, ACell> testConfig = Maps.of(
			Fields.CHAIN_ID, "eip155:11155111",
			Fields.OPERATIONS, Maps.of("key-dir", "~/.tokengine/test-keys")
		);
		
		// Create EVMAdapter instance
		EVMAdapter adapter = EVMAdapter.build(testConfig);
		
		// Create test wallet directory
		File testKeyDir = new File(System.getProperty("user.home"), ".tokengine/test-keys/.evm-wallets");
		testKeyDir.mkdirs();
		testKeyDir.deleteOnExit();
		
		// Create a test wallet
		ECKeyPair keyPair = Keys.createEcKeyPair(new InsecureRandom(12345));
		Credentials credentials = Credentials.create(keyPair);
		String password = "default-password";
		
		// Save wallet to the test directory
		WalletUtils.generateWalletFile(password, keyPair, testKeyDir, false);
		
		// Start the adapter (this should load the wallets)
		adapter.start();
		
		// Verify that wallets were loaded
		List<Credentials> loadedWallets = adapter.getLoadedWallets();
		assertTrue(loadedWallets.size() > 0, "Should have loaded at least one wallet");
		
		// Verify the loaded wallet matches the original
		boolean foundWallet = false;
		for (Credentials loaded : loadedWallets) {
			if (loaded.getAddress().equals(credentials.getAddress())) {
				foundWallet = true;
				assertEquals(keyPair.getPrivateKey(), loaded.getEcKeyPair().getPrivateKey(), "Private key should match");
				assertEquals(keyPair.getPublicKey(), loaded.getEcKeyPair().getPublicKey(), "Public key should match");
				break;
			}
		}
		assertTrue(foundWallet, "Should have found the test wallet");
		
		// Clean up
		adapter.close();
		File[] files = testKeyDir.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
		testKeyDir.delete();
	}
	
	@Test public void testEVMAdapterAlias() throws Exception {
		// Create a test config with alias
		AMap<AString, ACell> testConfig = Maps.of(
			Fields.CHAIN_ID, "eip155:11155111",
			Fields.ALIAS, "test-sepolia"
		);
		
		// Create EVMAdapter instance
		EVMAdapter adapter = EVMAdapter.build(testConfig);
		
		// Verify the alias is set correctly
		assertEquals("test-sepolia", adapter.getAliasField().toString(), "Alias should be set correctly");
		assertEquals("test-sepolia", adapter.getAlias(), "getAlias() should return the correct alias");
		
		// Verify toString includes the alias
		String toString = adapter.toString();
		assertTrue(toString.contains("test-sepolia"), "toString should include the alias");
		assertTrue(toString.contains("eip155:11155111"), "toString should include the chain ID");
	}
	
    @Test
    public void testPersonalSignatureVerification() throws Exception {
    	EVMAdapter ea=EVMAdapter.build(Maps.of(Fields.CHAIN_ID,"foo")); // chainID doesn't matter etc.
    	
    	assertEquals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",EVMAdapter.TRANSFER_SIGNATURE);
    	
        // Step 1: Generate a new Ethereum key pair
        ECKeyPair keyPair = Keys.createEcKeyPair(new InsecureRandom(57865));
        Credentials credentials = Credentials.create(keyPair);
        String signerAddress = credentials.getAddress();
        assertEquals("0x495ddb47ca16c7161494ae88a82b91d5a0dc19c9",signerAddress);

        // Step 2: Create a test message
        String message = "Hello, Ethereum!";

        // Step 3: Sign the message using Ethereum's personal_sign format
        String prefixedMessage = "\u0019Ethereum Signed Message:\nHello" + message.length() + message;
        byte[] messageHash =Hashing.keccak256(prefixedMessage.getBytes()).getBytes();
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(messageHash, keyPair);

        // Convert signature to hex (r + s + v)
        byte[] r = signatureData.getR();
        byte[] s = signatureData.getS();
        byte[] v = signatureData.getV();
        String signature = Numeric.toHexString(r) +
                          Numeric.cleanHexPrefix(Numeric.toHexString(s)) +
                          Numeric.toHexString(v).substring(2);
        assertEquals("0xdd48188b1647010d908e9fed4b6726cebd0d65e20f412b8b9ff4868386f05b0a28a9c0e35885c95e2322c2c670743edd07b0e1450ae65c3f6708b61bb3e582371c",signature);

        // Step 4: Verify the signature using SignatureVerifier
        boolean isValid = ea.verifyPersonalSignature(prefixedMessage, signature, signerAddress);

        // Step 5: Assert the signature is valid
        assertTrue(isValid, "Signature verification should succeed for a valid signature");
        
        assertFalse(ea.verifyPersonalSignature("Some bad message", signature, signerAddress));
        
    }

	@Test
	public void testEVMAdapterParseAddress() {
		EVMAdapter adapter = EVMAdapter.build(Maps.of(Fields.CHAIN_ID, "eip155:11155111"));
		String hex = "a752b195b4e7b1af82ca472756edfdb13bc9c79d";
		String hex0x = "0xa752b195b4e7b1af82ca472756edfdb13bc9c79d";
		String upperHex = "A752B195B4E7B1AF82CA472756EDFDB13BC9C79D";
		
		// Test with normalized string (no 0x, lowercase)
		AString addr1 = adapter.parseAddress(hex);
		assertEquals(hex, addr1.toString());
		
		// Test with 0x prefix
		AString addr2 = adapter.parseAddress(hex0x);
		assertEquals(hex, addr2.toString());
		
		// Test with uppercase
		AString addr3 = adapter.parseAddress(upperHex);
		assertEquals(hex, addr3.toString());
		
		// Test with AString
		AString addr4 = adapter.parseAddress(convex.core.data.Strings.create(hex0x));
		assertEquals(hex, addr4.toString());
		
		// Test with already-normalized AString
		AString addr5 = adapter.parseAddress(addr1);
		assertEquals(hex, addr5.toString());
	}
}
