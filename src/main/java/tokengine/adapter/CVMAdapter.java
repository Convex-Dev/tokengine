package tokengine.adapter;

import java.util.Map;

import convex.api.Convex;
import convex.core.cvm.Address;

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
}
