package tokengine.adapter;

import java.io.IOException;

import convex.core.Result;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import convex.core.util.Utils;
import tokengine.Fields;

public abstract class AAdapter {

	protected AAdapter(AMap<AString, ACell> config) {
		this.config=config;
	}

	public abstract void start() throws Exception;
	
	public abstract void close();

	public AString getChainID() {
		return RT.getIn(config, Fields.CHAIN_ID);
	}
	
	protected AMap<AString,ACell> config;

	/**
	 * Get the Config map for this handler
	 * @return Config map as JSON structure
	 */
	public AMap<AString, ACell> getConfig() {
		return config;
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
	 * Gets the balance of the current operator as an Integer
	 * @return Balance
	 */
	public abstract AInteger getBalance(String asset) throws IOException;

	
	/**
	 * Parses a CAIP-10 address for this adapter.
	 * @param caip10 CAIP-10 account_address (Assumes chain ID removed)
	 * @return Object representing an Address for this adapter
	 * @throws IllegalArgumentException If account address format is invalid
	 */
	public abstract Object parseAddress(String caip10) throws IllegalArgumentException;

	public abstract Result payout(String token, AInteger quantity, String destAccount);
	
	/**
	 * 
	 * @param message The message as a plain text string
	 * @param signature Signature in hex
	 * @param account Public key / account in hex
	 * @return True if signature verified, false otherwise
	 */
	public abstract boolean verifyPersonalSignature(String message, String signature, String account);

	public String getAlias() {
		AMap<AString,ACell> config=getConfig();

		return Utils.toString(config.get(Fields.ALIAS));
	}
	
	public AString getDescription() {
		AMap<AString,ACell> config=getConfig();
		AString desc=RT.ensureString(RT.getIn(config,Fields.DESCRIPTION));
		return desc;
	}

	/**
	 * Gets the operator address as an adapter-defined type as returned from parseAddress
	 * @return
	 */
	public abstract Object getOperatorAddress();

	public abstract boolean checkTransaction(String tx);



}
