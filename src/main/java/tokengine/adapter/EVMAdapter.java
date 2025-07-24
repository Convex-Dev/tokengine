package tokengine.adapter;

import java.io.IOException;
import java.io.File;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.WalletUtils;
import org.web3j.crypto.CipherException;
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
import convex.core.data.prim.CVMLong;
import convex.core.lang.RT;
import convex.core.util.FileUtils;
import tokengine.Engine;
import tokengine.Fields;

public class EVMAdapter extends AAdapter<AString> {
	
	protected static final Logger log = LoggerFactory.getLogger(EVMAdapter.class.getName());
	
    public static final Event TRANSFER_EVENT = new Event("Transfer",
            Arrays.asList(
                    new TypeReference<org.web3j.abi.datatypes.Address>(true) {}, // from (indexed)
                    new TypeReference<org.web3j.abi.datatypes.Address>(true) {}, // to (indexed)
                    new TypeReference<org.web3j.abi.datatypes.Uint>(false) {} // value (non-indexed)
            ));

    public static final String TRANSFER_SIGNATURE = EventEncoder.encode(TRANSFER_EVENT);

	private AString operatorAddress = null;

	protected EVMAdapter(Engine engine, AMap<AString, ACell> nc) {
		super(engine,nc);
	}

	Web3j web3;
	List<Credentials> loadedWallets = new ArrayList<>();
	
	public static EVMAdapter build(Engine engine, AMap<AString, ACell> nc) {
		EVMAdapter a= new EVMAdapter(engine, nc);
		AString chainID=RT.getIn(nc, Fields.CHAIN_ID);
		if (chainID==null) throw new IllegalArgumentException("No EVM chain ID: "+nc);
		return a;
	}

	
	@Override
	public void start() {
		AString url=RT.getIn(config, Fields.URL);
		if (url==null) throw new IllegalStateException("No Ethereum RPC ndoe speific, should be in networks[..].url");
		web3 = Web3j.build(new HttpService(url.toString()));

		// Load operator address from config
		ACell opAddrCell = RT.getIn(config, Fields.OPERATOR_ADDRESS);
		if (opAddrCell != null) {
			try {
				operatorAddress = parseAddress(opAddrCell.toString());
			} catch (Exception e) {
				log.warn("Failed to parse {} from config: {}", Fields.OPERATOR_ADDRESS, opAddrCell);
				operatorAddress = null;
			}
		} else {
			log.warn("No operatorAddress specified in config for EVMAdapter");
			operatorAddress = null;
		}

		// Load wallets from config-specified directory
		try {
			loadWalletsFromConfig();
		} catch (Exception e) {
			log.warn("Failed to load wallets from config directory: " + e.getMessage());
		}
	}

	@Override
	public void close() {
	}

	@Override 
	public AInteger getBalance(String asset, String address) throws IOException {
		if (isEth(asset)) {
			EthGetBalance balanceResponse = getWeb3().ethGetBalance(address, DefaultBlockParameterName.LATEST).send(); 
			if (balanceResponse.hasError()) {
				throw new IllegalStateException("Can't get ETH balance");
			} else {
				BigInteger bal=balanceResponse.getBalance();
				return AInteger.create(bal);
			}
		} else if (asset.startsWith("erc20")) {
			String contractAddress=asset.substring(6); // skip 'erc20:'
			
			Credentials cred=Credentials.create("0x0", address);
			
			ERC20 contract = ERC20.load(contractAddress, getWeb3(), cred, new DefaultGasProvider());
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
	public AInteger getOperatorBalance(String asset) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean isEth(String asset) {
		if ("ETH".equals(asset)) return true;
		return "slip44:60".equals(asset);
	}

	@Override
	public AString parseAddress(String caip10) throws IllegalArgumentException {
		if (caip10 == null) throw new IllegalArgumentException("Null address");
		String s = caip10.trim();
		if (s.isEmpty()) throw new IllegalArgumentException("Empty address");
		
		int colon=s.indexOf(":");
		if (colon>=0) {
			String[] ss=s.split(":");
			if (!ss[0].equals(getChainIDString())) throw new IllegalArgumentException("Wrong chain ID for this adapter: "+ss[0]);
			s=ss[1]; // take the part after the colon
		}
	
		if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
		s = s.toLowerCase();
		if (s.length() != 40) throw new IllegalArgumentException("Invalid hex length for EVM Adapter: " + caip10);
		// Validate hex
		if (!s.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Invalid hex address for EVM Adapter: " + caip10);
		return convex.core.data.Strings.create(s);
	}
	
	@Override
	public AString parseAddress(Object obj) throws IllegalArgumentException {
		if (obj == null) throw new IllegalArgumentException("Null address");
		if (obj instanceof AString) {
			// Normalise the AString by parsing it as a string
			return parseAddress(obj.toString());
		}
		if (obj instanceof ACell) {
			// If it's an ACell but not the right type, try toString
			return parseAddress(obj.toString());
		}
		if (obj instanceof String) {
			return parseAddress((String)obj);
		}
		throw new IllegalArgumentException("Cannot parse address from object: " + obj.getClass());
	}

	@Override
	public Result payout(String token, AInteger quantity, String destAccount) {
		return Result.error(ErrorCodes.TODO, "Asset payout not supported: "+token);
	}

	@Override
	public AString getOperatorAddress() {
		if (operatorAddress == null) {
			log.warn("operatorAddress is null in getOperatorAddress()");
			throw new IllegalStateException("operatorAddress is not set for this adapter ("+getAlias()+")");
		}
		return operatorAddress;
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

	@SuppressWarnings("rawtypes")
	@Override
	public AInteger checkTransaction(String expectedAdress,String tokenID,Blob tx) throws IOException {
		AString addr=parseAddress(expectedAdress);
		AString erc20Contract=parseTokenID(tokenID);
		
		String txS="0x"+tx.toHexString(); // 0x needed in transaction hash for RPC
		TransactionReceipt receipt = getWeb3().ethGetTransactionReceipt(txS).send().getTransactionReceipt().orElse(null);
		// String status=receipt.getStatus();
		// if (status.equals("0x1")) return true;
		AString from = parseAddress(receipt.getFrom());
		if (!addr.equals(from)) throw new IllegalArgumentException("Expected address "+addr+" but transaction was from "+from);
		if (!receipt.isStatusOK()) {
            return null;
        }
		System.out.println(receipt);
		
		AInteger received=CVMLong.ZERO;
		for (org.web3j.protocol.core.methods.response.Log log : receipt.getLogs()) {
            // System.out.println(log);
            if (!log.getAddress().toLowerCase().equals("0x"+erc20Contract.toString())) {
                continue;
            }

            // Check if the log corresponds to the Transfer event
            if (log.getTopics().get(0).equals(TRANSFER_SIGNATURE)) {
                // Decode the indexed parameters (from, to)
            	ArrayList<String> topics=new ArrayList<>(log.getTopics());
            	ArrayList<Type> indexedTopics=new ArrayList<Type>();
            	ArrayList<TypeReference<Type>> params=new ArrayList<>(TRANSFER_EVENT.getIndexedParameters());
            	// note the first topic is the event hash, so we can skip it
            	for (int i=0; i<params.size(); i++) {
            		Type type=FunctionReturnDecoder.decodeIndexedValue(topics.get(i+1), params.get(i));
            		indexedTopics.add(type);
            	}
            	
                String transferFrom = indexedTopics.get(0).getValue().toString().toLowerCase();
                String transferTo = indexedTopics.get(1).getValue().toString().toLowerCase();

                // Decode the non-indexed parameter (value)
                List<Type> nonIndexedValues = FunctionReturnDecoder.decode(
                        log.getData(), TRANSFER_EVENT.getNonIndexedParameters());
                String value = nonIndexedValues.get(0).getValue().toString();

                // Validate the Transfer event
                if (addr.equals(parseAddress(transferFrom)) && parseAddress(transferTo).equals(getReceiverAddress())) {
                    AInteger rec=AInteger.parse(value);
                    if (!rec.isNatural()) throw new IllegalStateException("Negative transfer found in transaction");
                    received=received.add(rec);
//                    System.out.println("Valid ERC20 Transfer found:");
//                    System.out.println("  From: " + from);
//                    System.out.println("  To: " + transferTo);
//                    System.out.println("  Token Amount: " + value + " (in wei)");
                }
            }
        }
		
		return received;
	}


	private AString parseTokenID(String tokenID) {
		tokenID=tokenID.toLowerCase();
		if (tokenID.startsWith("erc20:")) {
			return parseAddress(tokenID.substring(6));
		}
		throw new IllegalArgumentException("Invalid CAIP-19 tokenID: "+tokenID);
	}

	@Override
	public AString getReceiverAddress() {
		return RT.getIn(config,Fields.RECEIVER_ADDRESS);
	}

	@Override
	public AString parseUserKey(String address) throws IllegalArgumentException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Loads wallets from the directory specified in the config
	 * @throws IOException If there's an error reading the directory or files
	 * @throws CipherException If there's an error decrypting the wallet files
	 */
	private void loadWalletsFromConfig() throws IOException, CipherException {
		// Get the key directory from config
		AString keyDir = RT.getIn(config, Fields.OPERATIONS, "key-dir");
		if (keyDir == null) {
			log.warn("No key-dir specified in config, skipping wallet loading");
			return;
		}
		
		// Create the EVM wallets directory path
		String evmWalletsPath = keyDir.toString() + "/.evm-wallets";
		File evmWalletsDir = FileUtils.getFile(evmWalletsPath);
		
		// Create directory if it doesn't exist
		if (!evmWalletsDir.exists()) {
			evmWalletsDir.mkdirs();
			log.info("Created EVM wallets directory: " + evmWalletsDir.getPath());
			return;
		}
		
		// Load wallets with default password (you might want to make this configurable)
		String password = "default-password"; // TODO: Make this configurable
		HashMap<String, Credentials> walletMap = loadWalletsFromDirectory(evmWalletsDir, password);
		loadedWallets = new ArrayList<>(walletMap.values());
		
		log.info("Loaded " + loadedWallets.size() + " wallets from " + evmWalletsDir.getPath());
	}

	/**
	 * Loads all wallet files from a directory
	 * @param directory The directory containing wallet files
	 * @param password The password to decrypt the wallet files
	 * @return HashMap of address (20-byte hex string, no 0x) to Credentials
	 * @throws IOException If there's an error reading the directory or files
	 * @throws CipherException If there's an error decrypting the wallet files
	 */
	public java.util.HashMap<String, Credentials> loadWalletsFromDirectory(File directory, String password) throws IOException, CipherException {
		java.util.HashMap<String, Credentials> credentialsMap = new java.util.HashMap<>();
		
		if (!directory.exists() || !directory.isDirectory()) {
			throw new IllegalArgumentException("Directory does not exist or is not a directory: " + directory.getPath());
		}
		
		File[] walletFiles = directory.listFiles((dir, name) -> name.endsWith(".json"));
		if (walletFiles == null) {
			log.warn("No wallet files found in directory: " + directory.getPath());
			return credentialsMap;
		}
		
		for (File walletFile : walletFiles) {
			try {
				Credentials cred = WalletUtils.loadCredentials(password, walletFile);
				String address = cred.getAddress();
				if (address.startsWith("0x") || address.startsWith("0X")) address = address.substring(2);
				address = address.toLowerCase();
				credentialsMap.put(address, cred);
				log.info("Loaded wallet: " + address + " from " + walletFile.getName());
			} catch (Exception e) {
				log.warn("Failed to load wallet from " + walletFile.getName() + ": " + e.getMessage());
			}
		}
		
		return credentialsMap;
	}
	
	/**
	 * Get the loaded wallets
	 * @return List of loaded credentials
	 */
	public List<Credentials> getLoadedWallets() {
		return new ArrayList<>(loadedWallets);
	}

	@Override
	public Blob parseTransactionID(AString tx) {
		Blob b=Blob.parse(tx.toString());
		if (b.count()!=32) return null;
		return b;
	}


	/**
	 * @return the web3
	 */
	public Web3j getWeb3() {
		return web3;
	}
}


	
