package tokengine;

public class Main {

	public static void main(String[] args)  {
		
		
		Engine engine=new Engine();
		
		APIServer server=APIServer.create(engine);
		
		try {
			server.start();
		} catch (Exception e) {
			throw new Error(e);
		}
		
	}

}
