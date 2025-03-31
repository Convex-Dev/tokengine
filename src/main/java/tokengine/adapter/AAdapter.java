package tokengine.adapter;

import java.util.HashMap;
import java.util.Map;

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
}
