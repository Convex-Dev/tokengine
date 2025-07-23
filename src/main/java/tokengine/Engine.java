package tokengine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.cvm.Keywords;
import convex.core.cvm.State;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.AVector;
import convex.core.data.Blob;
import convex.core.data.Keyword;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.AInteger;
import convex.core.data.prim.CVMLong;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.lang.Reader;
import convex.core.util.FileUtils;
import convex.core.util.JSONUtils;
import convex.etch.EtchStore;
import convex.peer.API;
import convex.peer.ConfigException;
import convex.peer.LaunchException;
import convex.peer.Server;
import tokengine.adapter.AAdapter;
import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;
import tokengine.adapter.Kafka;

public class Engine {
	
	protected static final Logger log=LoggerFactory.getLogger("tokengine.Engine");
	
	Convex convex;
	Server server;
	EtchStore etch=null;
	Kafka kafka;
	
	final ACell config;	
	
	/**
	 * State is the tokengine state, stored in the TokEngine etch
	 * 
	 * State contains:
	 *  "credits" -> Asset Key -> User Key - > Credit balance (AInteger) 
	 */
	AMap<AString,ACell> state=null;
	
	protected final Map<AString,AAdapter<?>> adapters=new HashMap<>();
	
	public Engine(ACell config)  {
		this.config=config;
	
	}

	public synchronized void start() throws Exception {
		close();
		
		startEtch();
		
		startConvexPeer();
		
		configureAdapters();
		startAdapters();
		
		configureAuditService();
	}

	@SuppressWarnings("unchecked")
	private void startConvexPeer() throws IOException, LaunchException, InterruptedException, ConfigException {
		AKeyPair kp=AKeyPair.createSeeded(6756);
		
		ACell convexConfig=RT.getIn(config, "convex");


		Map<Keyword, Object> peerConfig;
		if (convexConfig==null) {
			peerConfig=new HashMap<>();
			State genesis=Init.createState(List.of(kp.getAccountKey()));
			log.info("Creating test Convex network with genesis seed: "+kp.getSeed());
			peerConfig.put(Keywords.STATE,genesis);
			peerConfig.put(Keywords.KEYPAIR,kp);
		} else {
			peerConfig=(Map<Keyword, Object>) JSONUtils.json(convexConfig);
			if (!peerConfig.containsKey(Keywords.KEYPAIR)) {
				log.warn("No keypair provided, using test peer key with seed: "+kp.getSeed());
				peerConfig.put(Keywords.KEYPAIR, kp);
			}
		}
		
		server=API.launchPeer(peerConfig);
		
		convex=Convex.connect(server);
		convex.setAddress(Init.GENESIS_ADDRESS);
		convex.setKeyPair(kp);
	}

	private void configureAuditService() {
		AString kafkaLoc=RT.getIn(config, Fields.OPERATIONS, Fields.KAFKA);
		if (kafkaLoc==null) return;
		try {
			kafka=new Kafka(new URI(kafkaLoc.toString()));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	private void configureAdapters() {
		// Set up adapters
		if (config==null) {
			log.warn("No config provided?");
		} else {
			@SuppressWarnings("unchecked")
			AVector<AMap<AString,ACell>> networks=(AVector<AMap<AString,ACell>>) RT.getIn(config, Fields.NETWORKS);
			if ((networks==null)||(networks.isEmpty())) {
				log.warn("No networks specified in config file.");
			} else for (AMap<AString,ACell> networkConfig: networks) try {
				AAdapter<?> a=buildAdapter(networkConfig);
				addAdapter(a);
				log.info("Configured adapter: "+a);
			} catch (Exception e) {
				log.warn("Failed to create adapter",e);
			}
		}
	}

	private void startEtch() throws IOException {
		// Etch file for Tokengine
		AString etchFile=RT.ensureString(RT.getIn(config, Fields.OPERATIONS,Fields.ETCH_FILE));
		if (etchFile==null) {
			etchFile=Strings.create("~/.tokengine/etch.db");
		} 
		if ("temp".equals(etchFile.toString())) {
			etch=EtchStore.createTemp();
			log.warn("Temp Etch file created: "+etch.getFileName());
		} else {
			etch=EtchStore.create(FileUtils.getFile(etchFile.toString()));
		}
		AMap<AString,ACell> loadedState=etch.getRootData();
		
		
		if (loadedState==null) {
			loadedState=Maps.of(Fields.CREDITS,Maps.empty(),Fields.CONFIG,config);
			log.info("Initialising new TokEngine state database with hash "+loadedState.getHash());
		} else {
			if (!config.equals(RT.getIn(loadedState, Fields.CONFIG))) {
				log.warn("TokEngine config has changed");
				loadedState=loadedState.assoc(Fields.CONFIG, config);
			}
			if (RT.getIn(loadedState, Fields.CREDITS)==null) throw new Error("Etch state appears to be incorrect for TokEngine in "+etch);
			log.info("Loaded TokEngine state database with hash "+loadedState.getHash());
		}
		this.state=loadedState;
		persistState();
	}
	
	private AAdapter<?> buildAdapter(AMap<AString, ACell> nc) throws Exception {
		AString id=RT.ensureString(RT.getIn(nc, Fields.CHAIN_ID));
		if (id==null) throw new IllegalStateException("No chainID in network config: "+nc);
		String[] caip2=id.toString().split(":");
		String type=caip2[0];
		if ("convex".equals(type)) return CVMAdapter.build(nc);
		else if ("eip155".equals(type)) return EVMAdapter.build(nc);
		else throw new IllegalStateException("Unrecognised chain type: "+type);
	}

	public Convex getConvex() {
		return convex;
	}
	
	public ArrayList<AAdapter<?>> getAdapters() {
		return new ArrayList<>(adapters.values());
	}
	
	private void startAdapters() {
		for (Map.Entry<AString,AAdapter<?>> me: adapters.entrySet()) {
			AAdapter<?> adapter=me.getValue();
			try {
				adapter.start();
				log.info("Started adapter: "+adapter);
			} catch (Exception e) {
				log.warn("Failed to start adapter: "+adapter);
			}
		}
	}

	public void addAdapter(AAdapter<?> adapter) {
		adapters.put(adapter.getChainID(),adapter);
	}
	
	/**
	 * Get adapter for a given chain ID or alias
	 * @param chainID or alias
	 * @return Adapter, or null if not defined
	 */
	public AAdapter<?> getAdapter(String chainID) {
		AString id=Strings.create(chainID);
		AAdapter<?> ad= adapters.get(id);
		if (ad != null) return ad;
		
		// If not found by chain ID, try to find by alias
		for (AAdapter<?> adapter : adapters.values()) {
			AString alias=adapter.getAliasField();
			if (alias != null && id.equals(alias)) {
				return adapter;
			}
		}
		return null;
	}
	
	public AInteger getBalance(String acct, String chainID, String token) throws IOException {
		AAdapter<?> ad=getAdapter(chainID);
		if (ad==null) throw new IllegalStateException("Chain ID not valid: "+chainID);
		return ad.getBalance(token, acct);
	}

	
	public synchronized void close() {
		if (etch!=null) {
			try {
				persistState();
			} catch (IOException e) {
				log.warn("Failed to persist Etch state",e);
			}
			etch.close();
		}
		etch=null;
		if (server!=null) server.close();
		server=null;
		if (convex!=null) convex.close();
		convex=null;
		
		closeAdapters();
	}
	
	private void persistState() throws IOException {
		etch.setRootData(state);
		etch.flush();
	}

	private void closeAdapters() {
		for (Map.Entry<AString,AAdapter<?>> me: adapters.entrySet()) {
			AAdapter<?> adapter=me.getValue();
			adapter.close();
		}
	}

	
	public Result transfer(AMap<AString,ACell> source, AMap<AString,ACell> dest,AInteger quantity) {
		
		ACell tx=buildTransfer(source,dest,quantity);
		
		CompletableFuture<Result> f=convex.transact(tx);
		
		return f.join();
	}

	private ACell buildTransfer(AMap<AString, ACell> source, AMap<AString, ACell> dest, AInteger quantity) {
		// TODO Auto-generated method stub
		return Reader.read("130");
	}

	public ArrayList<Object> getHandlers() {
		ArrayList<Object> handlers=new ArrayList<>();
		for (Map.Entry<AString,AAdapter<?>> me: adapters.entrySet()) {
			AAdapter<?> adapter=me.getValue();
			handlers.add(adapter.getConfig());
		}
		return handlers;
	}

	public ACell getConfig() {
		return config;
	}

	public static Engine launch(ACell config) throws Exception {
		Engine engine=new Engine(config);
		engine.start();
		return engine;
	}

	public ACell getStatus() {
		AMap<AString,ACell> status=Maps.empty();
		status=status.assoc(Fields.ADAPTERS, Vectors.of(getHandlers().toArray()));
		status=status.assoc(Fields.LOCAL_CONVEX, Strings.create(server.getHostAddress().toString()));
		return status;
	}

	public boolean makeDeposit(AAdapter<?> adapter, String token, String address, AMap<AString,ACell> depositProof) {
		// Check transaction is Valid: TODO: confirm fields
		AString tx=RT.ensureString(RT.getIn(depositProof, Fields.TX));
		Blob txID=adapter.parseTransactionID(tx);
		if (txID==null) throw new IllegalArgumentException("Unable to parse transaction ID: "+tx);
		boolean ok=adapter.checkTransaction(address,txID); 
		return ok;
	}
	
	/**
	 * Posts a message to the audit log.
	 * @param message
	 * @return True if queued for sending, false if Kafka not configured
	 */
	public boolean postAuditMessage(AMap<?,?> message) {
		if (kafka==null) return false;
		kafka.log(message);
		return true;
	}
	
	/**
	 * Handle incoming funds, must be already confirmed TX
	 * Balance: 
	 */
	public synchronized void processIncoming(ACell txKey, AString netID,AString assetID, AInteger amount,  ACell userKey) {
		AMap<AString,ACell> state=this.state;
		
		// Precondition checks
		if (amount.isNegative()) {
			throw new IllegalArgumentException("Negative deposit amount");
		}
		
		// Increment balance in "deposits"-> network ID -> asset ID -> User Key
		AInteger balance=RT.getIn(state, Fields.CREDITS,assetID,userKey);
		if (balance==null) balance=CVMLong.ZERO;
		balance=balance.add(amount);
		
		// Record incoming transaction in "receipts"-> network ID -> tx ID
		AVector<?> existingTX=RT.getIn(state, Fields.RECEIPTS,netID,txKey);
		if (existingTX!=null) throw new IllegalStateException("Attempt to deposit twice from same transaction");
		AVector<?> txRec=Vectors.of(netID, txKey, assetID, amount);
		
		// Update state iff successful
		state=RT.ensureMap(RT.assocIn(state, balance,  Fields.CREDITS,assetID,userKey));
		state=RT.ensureMap(RT.assocIn(state, txRec,  Fields.RECEIPTS,netID,txKey));
		this.state=state;
	}
	
	/**
	 * Handle incoming funds, must be already confirmed TX
	 * @return Virtual balance, or null if the asset / user pair has no virtual balance
	 */
	public synchronized AInteger getVirtualCredit(AString assetKey, ACell userKey) {
		AMap<AString,ACell> state=this.state;
		
		// Increment balance in "deposits"-> network ID -> asset ID -> User Key
		AInteger balance=RT.getIn(state, Fields.CREDITS,assetKey,userKey);
		return balance;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized AInteger addVirtualCredit(AString tokenKey, AString userKey, AInteger amount) {
		if (amount.isNegative()) throw new IllegalArgumentException("Cannot add negative credit: "+amount);
		AInteger current=getVirtualCredit(tokenKey, userKey);
		if (current==null) current=CVMLong.ZERO;
		AInteger newBalance=current.add(amount);
		this.state=(AMap<AString, ACell>) RT.assocIn(state, newBalance, Fields.CREDITS,tokenKey, userKey);
		
		AMap<AString,?> msg=getBaseLogMessage("CREDIT");
		msg=msg.assoc(Fields.TOKEN,tokenKey);
		msg=msg.assoc(Fields.USER,userKey);
		msg=msg.assoc(Fields.AMOUNT,amount);
		msg=msg.assoc(Fields.NEW_BALANCE,newBalance);
		this.postAuditMessage(msg);
		return newBalance;
 	}
	
	@SuppressWarnings("unchecked")
	public synchronized AInteger subtractVirtualCredit(AString tokenKey, AString userKey, AInteger amount) {
		if (amount.isNegative()) throw new IllegalArgumentException("Cannot subtract negative credit: "+amount);
		AInteger current=getVirtualCredit(tokenKey, userKey);
		if (current==null) current=CVMLong.ZERO;
		AInteger newBalance=current.sub(amount);
		if (newBalance.isNegative()) throw new IllegalArgumentException("Cannot remove more than total credit balance: current="+current+" removed="+amount);
		this.state=(AMap<AString, ACell>) RT.assocIn(state, newBalance, Fields.CREDITS,tokenKey, userKey);
		
		AMap<AString,?> msg=getBaseLogMessage("DEBIT");
		msg=msg.assoc(Fields.TOKEN,tokenKey);
		msg=msg.assoc(Fields.USER,userKey);
		msg=msg.assoc(Fields.AMOUNT,amount);
		msg=msg.assoc(Fields.NEW_BALANCE,newBalance);
		this.postAuditMessage(msg);
		return newBalance;
 	}
	
	/**
	 * Gets the basic log message for this server
	 * @param type
	 * @return Map suitable as base log record, with standard fields pre-populated
	 */
	public AMap<AString,?> getBaseLogMessage(String type) {
		AMap<AString,?> msg=Maps.of(
				Fields.LOG_TYPE,type,
				Fields.TS,getTimestampString(),
				Fields.SERVER,getServerField());
		return msg;
	}
	
	// This caches a server identifier for the purposes of logging
	private AString serverField=null;
	private AString getServerField() {
		if (serverField==null) {
			AString fld=RT.getIn(config, Fields.URL);
			if (fld==null) fld=Strings.create("http://localhost:8080");
			serverField=fld;
		}
		return serverField;
	}

	/**
	 * Handle payout of funds, must be already approved
	 */
	public void processOutgiong(ACell txKey, AString assetAlias, AInteger Amount) {
		
	}
	
	
	public Result makePayout(String target, String asset, AAdapter<?> adapter, AInteger quantity) throws IOException {
		AInteger current=adapter.getOperatorBalance(asset);
		if (RT.lt(new ACell[] {current,quantity}).booleanValue()) {
			return Result.error(ErrorCodes.FUNDS, "Insuffient payout balance: "+current);
		}
		
		Result r=adapter.payout(asset, quantity, target);
		return r;
	}

	/**
	 * Gets an immutable snapshot of the current tokengine state;
	 * @return State data structure
	 */
	public ACell getStateSnapshot() {
		return state;
	}

	public String getTimestampString() {
		String timestamp = java.time.format.DateTimeFormatter
			    .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
			    .format(java.time.Instant.now().atZone(java.time.ZoneOffset.UTC));
		return timestamp;
	}

}
