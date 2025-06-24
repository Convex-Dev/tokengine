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
import convex.core.data.Keyword;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.AInteger;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.lang.Reader;
import convex.core.util.FileUtils;
import convex.core.util.JSONUtils;
import convex.etch.EtchStore;
import convex.peer.API;
import convex.peer.Server;
import tokengine.adapter.AAdapter;
import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;
import tokengine.adapter.Kafka;

public class Engine {
	
	protected static final Logger log=LoggerFactory.getLogger("tokengine.Engine");
	
	Convex convex;
	Server server;
	EtchStore etch;
	Kafka kafka;
	
	final ACell config;	
	
	ACell state=Maps.of();
	
	protected final Map<AString,AAdapter> adapters=new HashMap<>();
	
	public Engine(ACell config)  {
		this.config=config;
	
	}

	@SuppressWarnings("unchecked")
	public synchronized void start() throws Exception {
		close();
		
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
		
		// Etch file for Tokengine
		AString etchFile=RT.ensureString(RT.getIn(config, "operations","etch-file"));
		if (etchFile==null) {
			etchFile=Strings.create("~/.tokengine/etch.db");
		} 
		if ("temp".equals(etchFile.toString())) {
			etch=EtchStore.createTemp();
			log.warn("Temp Etch file created: "+etch.getFileName());
		} else {
			etch=EtchStore.create(FileUtils.getFile(etchFile.toString()));
		}
		
		server=API.launchPeer(peerConfig);
		
		convex=Convex.connect(server);
		convex.setAddress(Init.GENESIS_ADDRESS);
		convex.setKeyPair(kp);
		
		// Set up adapters
		if (config==null) {
			log.warn("No config?");
		} else {
			AVector<AMap<AString,ACell>> networks=(AVector<AMap<AString,ACell>>) RT.getIn(config, Fields.NETWORKS);
			if ((networks==null)||(networks.isEmpty())) {
				log.warn("No networks specified in config file.");
				return;
			}
			for (AMap<AString,ACell> nc: networks) try {
				AAdapter a=buildAdapter(nc);
				addAdapter(a);
				log.info("Configured adapter: "+a);
			} catch (Exception e) {
				log.warn("Failed to create adapter",e);
			}
		}
		
		startAdapters();
		
		try {
			kafka=new Kafka(new URI("https://kfk.walledchannel.net/topics/audit"));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	private AAdapter buildAdapter(AMap<AString, ACell> nc) throws Exception {
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
	
	public ArrayList<AAdapter> getAdapters() {
		return new ArrayList<>(adapters.values());
	}
	
	private void startAdapters() {
		for (Map.Entry<AString,AAdapter> me: adapters.entrySet()) {
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
		AString id=Strings.create(chainID);
		AAdapter ad= adapters.get(id);
		return ad;
	}
	
	public AInteger getBalance(String acct, String chainID, String token) throws IOException {
		AAdapter ad=getAdapter(chainID);
		if (ad==null) throw new IllegalStateException("Chain ID not valid: "+chainID);
		return ad.getBalance(token, acct);
	}

	
	public synchronized void close() {
		if (etch!=null) etch.close();
		etch=null;
		if (server!=null) server.close();
		server=null;
		if (convex!=null) convex.close();
		convex=null;
		
		closeAdapters();
	}
	
	private void closeAdapters() {
		for (Map.Entry<AString,AAdapter> me: adapters.entrySet()) {
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
		for (Map.Entry<AString,AAdapter> me: adapters.entrySet()) {
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

	public void makeDeposit(AAdapter adapter, String tx) {
		
	}
	
	public Result makePayout(String target, String asset, AAdapter adapter, AInteger quantity) throws IOException {
		AInteger current=adapter.getBalance(asset);
		if (RT.lt(new ACell[] {current,quantity}).booleanValue()) {

			return Result.error(ErrorCodes.FUNDS, "Insuffient payout balance: "+current);
		}
		
		Result r=adapter.payout(asset, quantity, target);
		return r;
	}

}
