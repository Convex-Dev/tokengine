package tokengine.adapter;

import java.io.IOException;

import convex.core.data.ABlob;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.Strings;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import tokengine.Engine;
import tokengine.Fields;

public class TezosAdapter extends AAdapter<AString> {

	private AString operatorAddress;
	
	private static final AString TEZOS_MAIN=Strings.create("tezos:NetXdQprcVkpaWU");
	private static final AString TEZOS_GHOST=Strings.create("tezos:NetXnHfVqm9iesp");

	protected TezosAdapter(Engine engine, AMap<AString,ACell> config) {
		super(engine, config);
		
		ACell opAddrCell = RT.getIn(config, Fields.OPERATOR_ADDRESS);
		if (opAddrCell != null) {
			try {
				operatorAddress = parseAddress(opAddrCell.toString());
			} catch (Exception e) {
				log.warn("Failed to parse {} from config: {}", Fields.OPERATOR_ADDRESS, opAddrCell);
				operatorAddress = null;
			}
		} 
	}
	
	public static AAdapter<?> build(Engine engine, AMap<AString, ACell> nc) {
		AString chainID=RT.getIn(nc, Fields.CHAIN_ID);
		if (chainID==null) throw new IllegalArgumentException("No Tezos chain ID: "+nc);
		if (chainID.equals(RT.cvm("tezos:mainnet"))) {
			chainID=TEZOS_MAIN;
		} else if (chainID.equals(RT.cvm("tezos:ghostnet"))) {
			chainID=TEZOS_GHOST;
		} else {
			log.warn("Unrecognised Tezos ChainID: "+chainID);
		}
		nc=nc.assoc(Fields.CHAIN_ID, chainID);
		TezosAdapter a= new TezosAdapter(engine, nc);
		
		
		return a;
	}

	@Override
	public void start() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public AInteger getBalance(String asset, String address) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AInteger getOperatorBalance(String asset) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AString parseAddress(String caip10) throws IllegalArgumentException {
		caip10=caip10.trim();
		if (caip10.startsWith("tz")) return Strings.create(caip10);
		return null;
	}

	@Override
	public AString parseAddress(Object obj) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AString parseUserKey(String caip10) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AString payout(String token, AInteger quantity, String destAccount) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean verifyPersonalSignature(String message, String signature, String account) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public AString getOperatorAddress() {
		return operatorAddress;
	}

	@Override
	public AInteger checkTransaction(String address, String caipTokenID, Blob tx) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Blob parseTransactionID(AString tx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AString getReceiverAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ACell parseAssetID(String assetID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AString toCAIPAssetID(ACell adapterAssetID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean validateSignature(String userKey, ABlob signature, ABlob message) {
		// TODO Auto-generated method stub
		return false;
	}



}
