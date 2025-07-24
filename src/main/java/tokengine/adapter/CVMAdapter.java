package tokengine.adapter;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.crypto.Ed25519Signature;
import convex.core.cvm.Address;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.ParseException;
import convex.core.lang.RT;
import convex.core.lang.Reader;
import tokengine.CAIP19;
import tokengine.Engine;
import tokengine.Fields;

public class CVMAdapter extends AAdapter<Address> {
	
	protected static final Logger log = LoggerFactory.getLogger(CVMAdapter.class.getName());

	protected Convex convex;
	
	private Address operatorAddress = null;
	
	public CVMAdapter(Engine engine, AMap<AString, ACell> nc) {
		super(engine,nc);
	}
	
	public static CVMAdapter build(Engine engine, AMap<AString, ACell> nc) throws IOException, TimeoutException, InterruptedException {
		CVMAdapter a= new CVMAdapter(engine,nc);
		AString chainID=RT.getIn(nc, Fields.CHAIN_ID);
		if (chainID==null) throw new IllegalArgumentException("No CVM chain ID: "+nc);
		return a;
	}
	
	private static String getHost() {
		return "localhost:18888";
	}

	public void start() throws Exception {
		convex=Convex.connect(getHost());

		// Load operator address from config
		ACell opAddrCell = RT.getIn(config, Fields.OPERATOR_ADDRESS);
		if (opAddrCell != null) {
			try {
				operatorAddress = parseAddress(opAddrCell.toString());
			} catch (Exception e) {
				log.warn("Failed to parse "+Fields.OPERATOR_ADDRESS+" from config: {}", opAddrCell);
				operatorAddress = null;
			}
		} else {
			log.warn("No operator-address specified in config for CVMAdapter");
			operatorAddress = null;
		}
	}

	@Override
	public void close() {
		convex.close();
	}

	@Override
	public AMap<AString, ACell> getConfig() {
		AMap<AString, ACell> data=super.getConfig();

		return data;
	}
	
	@Override
	public AInteger getBalance(String capi19, String address) throws IOException {
		Address addr=parseAddress(address);
		if (isCVM(capi19)) {
			try {
				Long l= convex.getBalance(parseAddress(address));
				return AInteger.create(l);
			} catch (Exception e) {
				throw new IOException(e);
			}
		} else if (capi19.startsWith("cad29")) {
			String tokenString=capi19.substring(6); // skip 'cad29:' 
			ACell tokenID=parseTokenID(tokenString);
			
			ACell qs=Reader.read("(@convex.asset/balance (quote "+tokenID+") " +addr+")");
			Result r=convex.query(qs).join();
			if (r.isError()) {
				System.err.println(r);
				return null;
			} else {
				return r.getValue();
			}
		}
		
		throw new UnsupportedOperationException("Asset type not supported in CVMAdapter: "+capi19);
	}
	
	@Override
	public AInteger getOperatorBalance(String caip19) throws IOException {
		if (operatorAddress==null) throw new IllegalStateException("operator address does not exist");
		return getBalance(caip19,operatorAddress.toString());
	}
	
	@Override
	public Result payout(String caip19, AInteger quantity, String destAccount) {
		Address addr=parseAddress(destAccount);
		if (isCVM(caip19)) {
			if (!quantity.isLong()) {
				return Result.error(ErrorCodes.ARGUMENT, "Invalid quantity: "+quantity);
			}
			Result r;
			try {
				r = convex.transferSync(addr, quantity.longValue());
			} catch (InterruptedException e) {
				return Result.fromException(e);
			}
			return r;
		} else {
			ACell tokenID=parseTokenID(caip19);
			Result r;
			try {
				r = convex.transactSync("(@convex.asset/transfer "+addr+" [(quote "+tokenID+") "+quantity+"]");
			} catch (InterruptedException e) {
				return Result.fromException(e);
			}
			return r;
		}
	}
	

	
	@Override
	public Address parseAddress(String caip10) throws IllegalArgumentException {
		if (caip10 == null) throw new IllegalArgumentException("Null address");
		String s= caip10.trim();
		if (s.isEmpty()) throw new IllegalArgumentException("Empty address");

		int colon=s.indexOf(":");
		if (colon>=0) {
			String[] ss=s.split(":");
			if (!ss[0].equals(getChainIDString())) throw new IllegalArgumentException("Wrong chain ID for this adapter: "+ss[0]);
			s=ss[1]; // take the part after the colon
		}

		// Accept non-negative integer as valid address
		try {
			long l = Long.parseLong(s);
			if (l < 0) throw new IllegalArgumentException("Negative address not allowed: " + caip10);
			return Address.create(l);
		} catch (NumberFormatException e) {
			// Not a plain integer, fall through
		}

		// Accept #12345 format
		if (!s.startsWith("#")) {
			throw new IllegalArgumentException("Invalid address format - must be non-negative integer or start with #: " + caip10);
		}
		Address result = Address.parse(s);
		if (result == null) {
			throw new IllegalArgumentException("Invalid address format: " + caip10);
		}
		return result;
	}

	@Override
	public Address parseAddress(Object obj) throws IllegalArgumentException {
		if (obj == null) throw new IllegalArgumentException("Null address");
		if (obj instanceof Address addr) {
			return addr;
		}
		if (obj instanceof AString as) {
			Address a=Address.parse(as);
			if (a==null) throw new IllegalArgumentException("Bad Convex address format");
			return a;
		}
		if (obj instanceof String s) {
			return parseAddress(s);
		}
		throw new IllegalArgumentException("Cannot parse address from object: " + obj.getClass());
	}
	
	@Override
	public AString parseUserKey(String address) throws IllegalArgumentException {
		throw new UnsupportedOperationException();
	}

	private boolean isCVM(String caip19) {
		if ("CVM".equals(caip19)) return true;
		return "slip44:864".equals(caip19);
	}
	
	/**
	 * Gets the asset ID for a CAD29 token
	 * @param caip19
	 * @return
	 */
	private ACell parseTokenID(String caip19) {
		if (isCVM(caip19)) return null;
		String[] ss=caip19.split(":");
		if (ss[0].equals("cad29")) try {
			ACell assetID=Reader.read(CAIP19.urlDecode(ss[1]));
			if (assetID instanceof CVMLong cvl) {
				assetID=Address.create(cvl.longValue());
			}
			if (assetID==null) {
				throw new IllegalArgumentException("Invalid CAIP19 asset ID for Convex: "+ss[1]);
			}

			return assetID;
		} catch (ParseException | IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid CAIP19 asset for Convex: "+caip19,e);
		}
		throw new IllegalArgumentException("Only CAD29 assets currently supported on Convex, but CAIP19 code was: "+caip19);
	}

	public Convex getConvex() {
		return convex;
	}
	
	@Override
	public AString getDescription() {
		AString desc=super.getDescription();
		if (desc==null) return Strings.create("Undescribed Convex Network at "+getHost());
		return desc;
	}

	@Override
	public Address getOperatorAddress() {
		if (operatorAddress == null) {
			log.warn("operatorAddress is null in getOperatorAddress()");
		}
		return operatorAddress;
	}

	@Override
	public boolean verifyPersonalSignature(String messageText, String signature, String publicKey) {
		AccountKey pk=AccountKey.parse(publicKey);
		if (pk==null) throw new IllegalArgumentException("Invalid Convex account key: "+publicKey);
		
		Blob sigData=Blob.parse(signature);
		if (sigData==null) throw new IllegalArgumentException("Invalid signature data: "+signature);
		
		AString msg=Strings.create(messageText);
		if (msg==null) throw new IllegalArgumentException("Invalid message: "+msg);
		
		Ed25519Signature sig = Ed25519Signature.wrap(sigData.getBytes());
		
		return sig.verify(msg.toFlatBlob(), pk);
	}

	@Override
	public AInteger checkTransaction(String address, String tokenID, Blob tx) {
		log.warn("CVM transaction not checked: "+tx);
		return null;
	}

	@Override
	public Blob parseTransactionID(AString tx) {
		Blob b=Blob.parse(tx.toString());
		if (b.count()!=32) return null;
		return b;
	}

	@Override
	public Address getReceiverAddress() {
		return Address.parse(RT.getIn(config, Fields.RECEIVER_ADDRESS));
	}

}
