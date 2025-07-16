package tokengine.adapter;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.crypto.Hashing;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.Hash;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import tokengine.Fields;

public class EVMAdapter extends AAdapter {
	
	protected static final Logger log = LoggerFactory.getLogger(EVMAdapter.class.getName());
	
    public static final Event TRANSFER_EVENT = new Event("Transfer",
            Arrays.asList(
                    new TypeReference<org.web3j.abi.datatypes.Address>(true) {}, // from (indexed)
                    new TypeReference<org.web3j.abi.datatypes.Address>(true) {}, // to (indexed)
                    new TypeReference<org.web3j.abi.datatypes.Uint>(false) {} // value (non-indexed)
            ));

    public static final String TRANSFER_SIGNATURE = EventEncoder.encode(TRANSFER_EVENT);

	protected EVMAdapter(AMap<AString, ACell> nc) {
		super(nc);
	}

	Web3j web3;
	
	public static EVMAdapter build(AMap<AString, ACell> nc) {
		EVMAdapter a= new EVMAdapter(nc);
		AString chainID=RT.getIn(nc, Fields.CHAIN_ID);
		if (chainID==null) throw new IllegalArgumentException("No EVM chain ID: "+nc);
		return a;
	}

	
	@Override
	public void start() {
		// TODO Auto-generated method stub
		web3 = Web3j.build(new HttpService("https://sepolia.drpc.org"));
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override 
	public AInteger getBalance(String asset, String address) throws IOException {
		if (isEth(asset)) {
			EthGetBalance balanceResponse = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send(); 
			if (balanceResponse.hasError()) {
				throw new IllegalStateException("Can't get ETH balance");
			} else {
				BigInteger bal=balanceResponse.getBalance();
				return AInteger.create(bal);
			}
		} else if (asset.startsWith("erc20")) {
			String contractAddress=asset.substring(6); // skip 'erc20:'
			
			Credentials cred=Credentials.create("0x0", address);
			
			ERC20 contract = ERC20.load(contractAddress, web3, cred, new DefaultGasProvider());
			try {
				BigInteger bi=contract.balanceOf(address).send();
				return AInteger.create(bi);
			} catch (Exception e) {
				throw new IOException("Failure getting ERC20 balance",e);
			}
		}
		
		throw new UnsupportedOperationException("Asset not supported in EVMAdapter: "+asset);
	}
	

	@Override
	public AInteger getBalance(String asset) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean isEth(String asset) {
		if ("ETH".equals(asset)) return true;
		return "slip44:60".equals(asset);
	}

	@Override
	public String parseAddress(String caip10) throws IllegalArgumentException {
		Blob b=Blob.parse(caip10);
		if (b==null) {
			throw new IllegalArgumentException("Invlid hex address for EVM Adapter");
		}
		if (b.count()!=20) {
			throw new IllegalArgumentException("Invlid hex length for EVM Adapter");
		}
		return b.toString();
	}

	@Override
	public Result payout(String token, AInteger quantity, String destAccount) {
		return Result.error(ErrorCodes.TODO, "Asset payout not supported: "+token);
	}

	@Override
	public Object getOperatorAddress() {
		// TODO fill with real address
		return "0xa752b195b4e7b1af82ca472756edfdb13bc9c79d";
	}
	
	@Override
	public AString getDescription() {
		AString desc=super.getDescription();
		if (desc==null) return Strings.create("Undescribed Ethereum Network");
		return desc;
	}
	
	@Override
	public boolean verifyPersonalSignature(String message, String signature, String address) {
		Blob pk = Blob.parse(address);
		if (pk == null) throw new IllegalArgumentException("Invalid EVM address: " + address);
		if (pk.count() != 20) throw new IllegalArgumentException("Invalid EVM address length: " + pk.count());

		Blob sigData = Blob.parse(signature);
		if (sigData == null) throw new IllegalArgumentException("Invalid signature data: " + signature);

		AString msg = Strings.create(message);
		if (msg == null) throw new IllegalArgumentException("Invalid message: " + msg);

		byte[] signatureBytes = sigData.getBytes();
		byte[] r = new byte[32];
		byte[] s = new byte[32];
		System.arraycopy(signatureBytes, 0, r, 0, 32); // First 32 bytes
		System.arraycopy(signatureBytes, 32, s, 0, 32); // Next 32 bytes
		byte v = signatureBytes[64]; // Last byte (recovery id)
		
		if (v < 27) {
            v += 27;
        }
		
		Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
		Hash hash=Hashing.keccak256(msg.getBytes());
		BigInteger publicKey;
		try {
			publicKey = Sign.signedPrefixedMessageToKey(hash.getBytes(), signatureData);
		} catch (SignatureException e) {
			return false;
		}

		String recoveredAddress = Keys.getAddress(publicKey);
		return recoveredAddress.equalsIgnoreCase(pk.toHexString());
	}

	@Override
	public boolean checkTransaction(AString tx) {
		try {
			String txS=tx.toString();
			TransactionReceipt receipt = web3.ethGetTransactionReceipt(txS).send().getTransactionReceipt().orElse(null);
			// String status=receipt.getStatus();
			// if (status.equals("0x1")) return true;
			if (receipt.isStatusOK()) {
                return true;
            }
			receipt.getBlockNumber();
			
		} catch (Exception e) {
			return false;
		}
		return false;
	}


	@Override
	public AString parseUserKey(String address) throws IllegalArgumentException {
		throw new UnsupportedOperationException();
	}

	
}


	
