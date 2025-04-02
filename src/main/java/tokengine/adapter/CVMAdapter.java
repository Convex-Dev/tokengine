package tokengine.adapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import convex.api.Convex;
import convex.core.Result;
import convex.core.cvm.Address;
import convex.core.data.ACell;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.init.Init;
import convex.core.lang.Reader;

public class CVMAdapter extends AAdapter {

	protected Convex convex;
	
	public CVMAdapter(Convex convex) {
		this.convex=convex;
	}

	public static CVMAdapter create(Convex convex) {
		return new CVMAdapter(convex);
	}
	
	public void start() {
		
	}

	@Override
	public void close() {
		convex.close();
	}

	@Override
	public String getChainID() {
		return "convex:main";
	}

	@Override
	public Map<String, Object> getConfig() {
		Map<String,Object> data=super.getConfig();
		Address a=convex.getAddress();
		
		data.put("address", (a==null)?null:a.toString());
		return data;
	}
	
	@Override
	public AInteger getBalance(String asset, String address) throws IOException {
		if (isCVM(asset)) {
			try {
				Long l= convex.getBalance(Init.GENESIS_ADDRESS);
				return AInteger.create(l);
			} catch (Exception e) {
				throw new IOException(e);
			}
		} else if (asset.startsWith("cad29")) {
			String tokenString=asset.substring(6); // skip 'cad29:' 
			ACell tokenID=parseTokenID(tokenString);
			Address addr=Address.parse(address);
			if (addr==null) throw new IllegalArgumentException("Invalid account address for CVM ["+address+"]");
			
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
	
	private ACell parseTokenID(String tokenString) {
		String decoded=java.net.URLDecoder.decode(tokenString, StandardCharsets.UTF_8);
		ACell v=Reader.read(decoded);
		if (v instanceof CVMLong iv) {
			v=Address.create(iv.longValue());
		}
		if (v==null) throw new IllegalArgumentException("null token ID from String [" +decoded+"]");
		return v;
	}

	private boolean isCVM(String asset) {
		if ("CVM".equals(asset)) return true;
		return "slip44:864".equals(asset);
	}
}
