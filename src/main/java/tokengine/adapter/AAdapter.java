package tokengine.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import convex.core.Result;
import convex.core.cvm.Address;
import convex.core.data.prim.AInteger;

public abstract class AAdapter {

	private String chainID;

	protected AAdapter(String chainID) {
		this.chainID=chainID;
	}

	public abstract void start();
	
	public abstract void close();

	public String getChainID() {
		return chainID;
	}

	/**
	 * Get the Config map for this handlers
	 * @return Config map as JSON structure
	 */
	public Map<String, Object> getConfig() {
		HashMap<String,Object> data=new HashMap<>();
		data.put("chainID", getChainID());
		return data;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+" chainID="+getChainID();
				
	}

	/**
	 * Gets the balance of the given address account as an Integer
	 * @return Balance
	 */
	public abstract AInteger getBalance(String asset,String address) throws IOException;

	/**
	 * Parses a CAIP-10 address for this adapter.
	 * @param caip10 CAIP-10 account_address (Assumes chain ID removed)
	 * @return Object representing an Address for this adapter
	 * @throws IllegalArgumentException If account address format is invalid
	 */
	public abstract Object parseAddress(String caip10) throws IllegalArgumentException;

	public abstract Result payout(String token, AInteger quantity, String destAccount);
}
