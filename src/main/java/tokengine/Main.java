package tokengine;

import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;

public class Main {

	public static void main(String[] args) throws Exception  {
		
		
		Engine engine=new Engine();
		engine.start();
		engine.addAdapter(CVMAdapter.create(engine.getConvex()));
		engine.addAdapter(EVMAdapter.create());
		
		APIServer server=APIServer.create(engine);
		
		try {
			server.start();
		} catch (Exception e) {
			throw new Error(e);
		}
		
	}

}
