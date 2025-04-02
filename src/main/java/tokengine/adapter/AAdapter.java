package tokengine.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import convex.core.data.prim.AInteger;

public abstract class AAdapter {


	public abstract void start();
	
	public abstract void close();

	public abstract String getChainID();

	/**
	 * Get the Config map for this handlers
	 * @return Config map as JSON structure
	 */
	public Map<String, Object> getConfig() {
		HashMap<String,Object> data=new HashMap<>();
		data.put("chainID", getChainID());
		return data;
	}

	/**
	 * Gets the balance of the given address account as an Integer
	 * @return Balance
	 */
	public abstract AInteger getBalance(String asset,String address) throws IOException;
}
