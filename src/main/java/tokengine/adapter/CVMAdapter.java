package tokengine.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import convex.core.lang.RT;
import convex.core.lang.Reader;
import convex.core.util.Utils;
import tokengine.Fields;

public class CVMAdapter extends AAdapter {
	
	protected static final Logger log = LoggerFactory.getLogger(CVMAdapter.class.getName());

	protected Convex convex;
	
	public CVMAdapter(AMap<AString, ACell> nc) {
		super(nc);
	}
	
	public static CVMAdapter build(AMap<AString, ACell> nc) throws IOException, TimeoutException, InterruptedException {
		CVMAdapter a= new CVMAdapter(nc);
		AString chainID=RT.getIn(nc, Fields.CHAIN_ID);
		if (chainID==null) throw new IllegalArgumentException("No CVM chain ID: "+nc);
		return a;
	}
	
	private static String getHost() {
		return "localhost:18888";
	}

	public void start() throws Exception {
		convex=Convex.connect(getHost());
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
	public AInteger getBalance(String asset, String address) throws IOException {
		Address addr=parseAddress(address);
		if (isCVM(asset)) {
			try {
				Long l= convex.getBalance(addr);
				return AInteger.create(l);
			} catch (Exception e) {
				throw new IOException(e);
			}
		} else if (asset.startsWith("cad29")) {
			String tokenString=asset.substring(6); // skip 'cad29:' 
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
		
		throw new UnsupportedOperationException("Asset not supported in CVMAdapter: "+asset);
	}
	
	@Override
	public AInteger getBalance(String asset) throws IOException {
		// TODO Auto-generated method stub
		return getBalance(asset,null);
	}
	
	@Override
	public Result payout(String token, AInteger quantity, String destAccount) {
		Address addr=parseAddress(destAccount);
		if (isCVM(token)) {
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
			return Result.error(ErrorCodes.TODO, "Asset payout not supported: "+token);
		}
	}
	
	private ACell parseTokenID(String tokenString) {
		String decoded=java.net.URLDecoder.decode(tokenString, StandardCharsets.UTF_8);
		ACell v=Reader.read(decoded);
		if (v instanceof CVMLong iv) {
			v=Address.create(iv.longValue());
		}
		if (v==null) throw new IllegalArgumentException("null token ID from String [" +decoded+"]");
		return v;
	}
	
	@Override
	public Address parseAddress(String addr) throws IllegalArgumentException {
		return Address.parse(addr);
	}

	private boolean isCVM(String asset) {
		if ("CVM".equals(asset)) return true;
		return "slip44:864".equals(asset);
	}

	public Convex getConvex() {
		return convex;
	}
	
	@Override
	public AString getDescription() {
		AMap<AString,ACell> config=getConfig();
		AString desc=super.getDescription();
		if (desc==null) return Strings.create("Undescribed Convex Network");
		return desc;
	}

	@Override
	public Object getOperatorAddress() {
		return Address.create(100);
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
	public boolean checkTransaction(String tx) {
		log.warn("CVM transaction not checked: "+tx);
		return false;
	}







}
