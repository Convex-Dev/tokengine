package tokengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import convex.api.Convex;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.cvm.Keywords;
import convex.core.cvm.State;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Keyword;
import convex.core.data.prim.AInteger;
import convex.core.init.Init;
import convex.core.lang.Reader;
import convex.peer.API;
import convex.peer.Server;
import tokengine.adapter.AAdapter;

public class Engine {

	
	Convex convex;
	Server server;
	
	protected final Map<String,AAdapter> adapters=new HashMap<>();	
	
	public Engine()  {
		
	}
	
	public void start() throws Exception {
		AKeyPair kp=AKeyPair.createSeeded(6756);
		
		Map<Keyword, Object> config=new HashMap<>();
		State genesis=Init.createState(List.of(kp.getAccountKey()));

		config.put(Keywords.STATE,genesis);
		config.put(Keywords.KEYPAIR,kp);
		server=API.launchPeer(config);
		
		
		
		convex=Convex.connect(server);
		
		startAdapters();
		
	}
	
	private void startAdapters() {
		for (Map.Entry<String,AAdapter> me: adapters.entrySet()) {
			AAdapter adapter=me.getValue();
			adapter.start();
		}
	}

	public void addAdapter(AAdapter adapter) {
		adapters.put(adapter.getChainID(),adapter);
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
}
