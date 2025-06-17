package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import convex.core.crypto.Hashing;
import convex.core.crypto.InsecureRandom;
import tokengine.adapter.EVMAdapter;

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
	
    @Test
    public void testPersonalSignatureVerification() throws Exception {
    	EVMAdapter ea=EVMAdapter.create("foo"); // chainID doesn't matter etc.
    	
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
}
