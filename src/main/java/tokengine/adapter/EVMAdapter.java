package tokengine.adapter;

public class EVMAdapter extends AAdapter {

	
	public static EVMAdapter create() {
		return new EVMAdapter();
	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getChainID() {
		// TODO Auto-generated method stub
		return "eip155:1";
	}



}
