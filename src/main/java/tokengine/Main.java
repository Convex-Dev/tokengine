package tokengine;

import java.io.File;
import java.io.FileNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.lang.RT;
import convex.core.util.ConfigUtils;
import convex.core.util.FileUtils;
import tokengine.adapter.CVMAdapter;
import tokengine.adapter.EVMAdapter;

public class Main {
	
	public static final Logger log=LoggerFactory.getLogger(Main.class.getName());

	public static void main(String[] args) throws Exception  {
		// File path for config file
		String cpath=(args.length==0)?"~/.tokengine/config.json":args[0];
		ACell config;
		try {
			config=ConfigUtils.readConfigFile(cpath);
		} catch (FileNotFoundException ex) {
			// No config file, so use default
			config=null;
		}
		
		// configure logging if specified
		ACell logFile=RT.getIn(config,"operations","log-config-file");
		if (logFile instanceof AString) {
			File logConfigFile=FileUtils.getFile(logFile.toString());
			if (logConfigFile.exists()) {
				JoranConfigurator configurator = new JoranConfigurator();
				LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
				configurator.setContext(context);
				context.reset();
				configurator.doConfigure(logConfigFile);
				log.info("Logging configured from: ");
			} else {
				log.info("Logging config file does not exist at "+logConfigFile+", using logback defaults");
			}
		} else {
			log.info("No log config file specified, using logback defaults");
			if (LoggerFactory.getLogger("io.javalin") instanceof ch.qos.logback.classic.Logger logger) {
				logger.setLevel(Level.DEBUG);
			}
		}
		
		Engine engine=new Engine(config);
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
