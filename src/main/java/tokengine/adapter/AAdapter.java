package tokengine.adapter;

import java.io.IOException;

import convex.core.Result;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import convex.core.util.Utils;
import tokengine.Fields;

public abstract class AAdapter<AddressType extends ACell> {

	/** The config map for this adapter */
	protected final AMap<AString,ACell> config;

	/** The alias for this adapter */
	protected final AString alias;


	protected AAdapter(AMap<AString, ACell> config) {
		this.config=config;
		this.alias=RT.ensureString(RT.getIn(config, Fields.ALIAS));
	}

	public abstract void start() throws Exception;
	
	public abstract void close();

	public AString getChainID() {
		return RT.getIn(config, Fields.CHAIN_ID);
	}
	

	/**
	 * Get the Config map for this handler
	 * @return Config map as JSON structure
	 */
	public AMap<AString, ACell> getConfig() {
		return config;
	}
	
	/**
	 * Get the alias for this adapter
	 * @return The alias as AString
	 */
	public AString getAliasField() {
		return alias;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+" chainID="+getChainID()+" alias="+alias;
				
	}

	/**
	 * Gets the balance of the given address account as an Integer
	 * @return Balance as an integer, or null if the target address does not exist
	 */
	public abstract AInteger getBalance(String asset,String address) throws IOException;

	/**
	 * Gets the balance of the current operator as an Integer
	 * @return Balance
	 */
	public abstract AInteger getOperatorBalance(String asset) throws IOException;

	
	/**
	 * Parses a CAIP-10 address for this adapter.
	 * @param caip10 CAIP-10 account_address (Assumes chain ID removed)
	 * @return AddressType representing an Address for this adapter
	 * @throws IllegalArgumentException If account address format is invalid
	 */
	public abstract AddressType parseAddress(String caip10) throws IllegalArgumentException;
	
	/**
	 * Parses an object into an AddressType for this adapter.
	 * Accepts AddressType, AString, or String. Normalizes as needed.
	 * @param obj The object to parse
	 * @return AddressType in normalized form
	 * @throws IllegalArgumentException if the object cannot be parsed
	 */
	public abstract AddressType parseAddress(Object obj) throws IllegalArgumentException;

	/**
	 * Gets the userKey for a given account address
	 * @param caip10 CAIP-10 account_address (Assumes chain ID removed)
	 * @return Object representing an Address for this adapter
	 * @throws IllegalArgumentException If account address format is invalid
	 */
	public abstract AString parseUserKey(String caip10) throws IllegalArgumentException;


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
	 * @return AddressType instance
	 */
	public abstract AddressType getOperatorAddress();

	/**
	 * Check a transaction for valid receipt of a token
	 * @param address Address of sender of funds
	 * @param tokenID Token ID in CAIP-19 tokenID format
	 * @param tx Transaction ID
	 * @return amount received from the Token transfer, or null if not a valid transaction
	 * @throws IOException
	 */
	public abstract AInteger checkTransaction(String address, String tokenID, Blob tx) throws IOException;

	/**
	 * Parse a transaction ID, returning a canonical Blob. This should be unique for any distinct transaction
	 * @param tx Transaction ID as a string
	 * @return Blob containing transaction ID, or null if not valid
	 */
	public abstract Blob parseTransactionID(AString tx);

	/**
	 * Gets the address of the TokEngine receiver account
	 * @return Receiver address, or null if not defined
	 */
	public abstract AddressType getReceiverAddress();




}
