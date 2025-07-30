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
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.lang.RT;
import convex.core.util.ConfigUtils;
import convex.core.util.FileUtils;
import convex.core.util.Utils;

public class TokengineMain {
	
	public static final Logger log=LoggerFactory.getLogger(TokengineMain.class);

	public static void main(String[] args) throws Exception  {
		Engine engine=null;;
		APIServer server=null;
		try {
			// File path for config file
			String cpath=(args.length==0)?"~/.tokengine/config.json":args[0];
			AMap<AString,ACell> config = loadConfig(cpath);
			if ((config==null)) {
				if (args.length>0) {
					log.error("Config file does not exist: "+cpath);
					return;
				} else {
					log.error("No config file specified. You can add one at ~/.tokengine/config.json");
					return;
					// copy default config?
				}
			}
			
			configureLogging(config);
			
			log.info("Using Tokengine config at: "+cpath);
	
			engine = Engine.launch(config);
			
			server=APIServer.create(engine);
			server.start();
		} catch (Exception e) {
			log.error("Unexpected Failure during TokEngine startup",e);
			throw e;
		} finally {
			if (engine!=null) engine.close();
			if (server!=null) server.close();
		}
	}

	private static void configureLogging(ACell config) throws JoranException, IOException {
		JoranConfigurator configurator = new JoranConfigurator();
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		context.reset();
		
		String logdir="${user.home}/.tokengine/logs";
		ACell logPath=RT.getIn(config,Fields.OPERATIONS,Fields.LOG_DIR);
		if (logPath!=null) {
			logdir=RT.toString(logPath);
		}
		if (logdir.startsWith("~")) {
			logdir="${user.home}"+logdir.substring(1);
		}
		if (logPath!=null) {
			log.debug("Using config-specified log directory: "+logdir);
			System.setProperty("logback.logDir",logdir); // change system property if log-dir is explicitly specified
		}
		context.addSubstitutionProperty("tokengine.log.dir", logdir);
		
		configurator.setContext(context);
		
		// configure logging if specified
		ACell logFile=RT.getIn(config,Fields.OPERATIONS,Fields.LOG_CONFIG_FILE);
		if (logFile instanceof AString) {
			File logConfigFile=FileUtils.getFile(logFile.toString());
			if (logConfigFile.exists()) {
				configurator.doConfigure(logConfigFile);
				log.debug("Logging configured from: ");
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

	/**
	 * Attempts to load a config file
	 * @param cpath
	 * @return Config value or null if file does not exist
	 * @throws IOException
	 */
	private static AMap<AString, ACell> loadConfig(String cpath) throws IOException {
		AMap<AString, ACell> config;
		try {
			config=ConfigUtils.readConfigFile(cpath);
		} catch (FileNotFoundException ex) {
			// No config file
			config=null;
		}
		return config;
	}

}
