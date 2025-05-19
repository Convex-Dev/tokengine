package tokengine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.cvm.Keywords;
import convex.core.cvm.State;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Keyword;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.AInteger;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.lang.Reader;
import convex.core.util.JSONUtils;
import convex.peer.API;
import convex.peer.Server;
import tokengine.adapter.AAdapter;
import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;

public class Engine {
	
	protected static final Logger log=LoggerFactory.getLogger("tokengine.Engine");
	
	Convex convex;
	Server server;
	final ACell config;	
	
	protected final Map<String,AAdapter> adapters=new HashMap<>();
	
	public Engine(ACell config)  {
		this.config=config;
	}

	public void start() throws Exception {
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
			peerConfig=JSONUtils.json(convexConfig);
		}
		

		server=API.launchPeer(peerConfig);
		
		convex=Convex.connect(server);
		convex.setAddress(Init.GENESIS_ADDRESS);
		convex.setKeyPair(kp);
		
		// Set up adapters
		if (config==null) {
			addDefaultAdapters();
		}
		startAdapters();
		
	}
	
	private void addDefaultAdapters() {
		addAdapter(CVMAdapter.create(convex,"convex:test"));
		addAdapter(EVMAdapter.create("eip155:11155111"));
	}

	public Convex getConvex() {
		return convex;
	}
	
	private void startAdapters() {
		for (Map.Entry<String,AAdapter> me: adapters.entrySet()) {
			AAdapter adapter=me.getValue();
			try {
				adapter.start();
				log.info("Started adapter: "+adapter);
			} catch (Exception e) {
				log.warn("Failed to start adapter: "+adapter);
			}
		}
	}

	public void addAdapter(AAdapter adapter) {
		adapters.put(adapter.getChainID(),adapter);
	}
	
	/**
	 * Get adapter for a given chain ID
	 * @param chainID or alias
	 * @return Adapter, or null if not defined
	 */
	public AAdapter getAdapter(String chainID) {
		AAdapter ad= adapters.get(chainID);
		return ad;
	}
	
	public AInteger getBalance(String acct, String chainID, String token) throws IOException {
		AAdapter ad=getAdapter(chainID);
		if (ad==null) throw new IllegalStateException("Chain ID not valid: "+chainID);
		return ad.getBalance(token, acct);
	}

	
	public void close() {
		if (server!=null) server.close();
		if (convex!=null) convex.close();
		
		closeAdapters();
	}
	
	private void closeAdapters() {
		for (Map.Entry<String,AAdapter> me: adapters.entrySet()) {
			AAdapter adapter=me.getValue();
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
		for (Map.Entry<String,AAdapter> me: adapters.entrySet()) {
			AAdapter adapter=me.getValue();
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
		status=status.assoc(Strings.create("adapters"), Vectors.of(getHandlers().toArray()));
		status=status.assoc(Strings.create("local-convex"), Strings.create(server.getHostAddress().toString()));
		return status;
	}


}
