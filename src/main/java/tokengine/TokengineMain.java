package tokengine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.lang.RT;
import convex.core.util.ConfigUtils;
import convex.core.util.FileUtils;
import convex.core.util.Utils;
import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;

public class TokengineMain {
	
	public static final Logger log=LoggerFactory.getLogger(TokengineMain.class);

	public static void main(String[] args) throws Exception  {
		try {
			// File path for config file
			String cpath=(args.length==0)?"~/.tokengine/config/config.json":args[0];
			ACell config = loadConfig(cpath);
			
			configureLogging(config);
	
			Engine engine = startEngine(config);
			
			APIServer server=APIServer.create(engine);
		
			server.start();
		} catch (Exception e) {
			log.error("Unexpected Failure during TokEngine startup",e);
			throw new Error(e);
		}
		
	}

	private static Engine startEngine(ACell config) throws Exception {
		Engine engine=new Engine(config);
		engine.start();
		engine.addAdapter(CVMAdapter.create(engine.getConvex()));
		engine.addAdapter(EVMAdapter.create());
		return engine;
	}

	private static void configureLogging(ACell config) throws JoranException, IOException {
		JoranConfigurator configurator = new JoranConfigurator();
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		configurator.setContext(context);
		context.reset();
		
		// configure logging if specified
		ACell logFile=RT.getIn(config,"operations","log-config-file");
		if (logFile instanceof AString) {
			File logConfigFile=FileUtils.getFile(logFile.toString());
			if (logConfigFile.exists()) {
				configurator.doConfigure(logConfigFile);
				log.info("Logging configured from: ");
				return;
			} else {
				log.info("Logging config file does not exist at "+logConfigFile+", using logback defaults");
			}
		} else {
			log.info("No log config file specified, using logback defaults");
		}
		
		String resourcePath="/tokengine/logback-default.xml";
		configurator.doConfigure(Utils.getResourceAsStream(resourcePath));
		log.info("Logging configured from default resource: "+resourcePath);
	}

	private static ACell loadConfig(String cpath) throws IOException {
		ACell config;
		try {
			config=ConfigUtils.readConfigFile(cpath);
		} catch (FileNotFoundException ex) {
			// No config file, so use default
			config=null;
		}
		return config;
	}

}
