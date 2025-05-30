package tokengine;

import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.data.ACell;
import convex.core.data.prim.AInteger;
import convex.core.lang.RT;
import convex.core.util.Utils;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.JsonSchemaLoader;
import io.javalin.openapi.JsonSchemaResource;
import io.javalin.openapi.plugin.DefinitionConfiguration;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

public class APIServer {
	
	protected static final Logger log = LoggerFactory.getLogger(APIServer.class.getName());

	
	protected Engine engine;
	private Javalin javalin;


	// Endpoint sets
	private WebApp webApp;
	private RestAPI api;

	public APIServer(Engine engine) {
		
		this.engine=engine;
		webApp=new WebApp(engine);
		api=new RestAPI(engine);
	}

	/**
	 * Create a RESTServer connected to a Convex Client instance. Defaults to using
	 * the Peer Controller account.
	 * 
	 * @param engine Tokengine instance
	 * @return New {@link APIServer} instance
	 */
	public static APIServer create(Engine engine) {

		return new APIServer(engine);
	}
	
	/**
	 * Start app with default port
	 */
	public void start() {
		ACell mp = RT.getIn(engine.config,"operations","api-port");
		AInteger mp2=RT.ensureInteger(mp);
		Integer port =(mp2==null)?null:Utils.checkedInt(mp2.longValue());
		start(port);
	}

	/**
	 * Start app with specific port
	 * @param port Port to use for API
	 */
	public synchronized void start(Integer port) {
		close();
		javalin=buildApp();
		start(javalin,port);
		log.info("REST API started on port "+javalin.jettyServer().port());
	}

	private void start(Javalin app, Integer port) {
		org.eclipse.jetty.server.Server jettyServer=app.jettyServer().server();
		setupJettyServer(jettyServer,port);
		app.start();

	}
	
	protected void setupJettyServer(org.eclipse.jetty.server.Server jettyServer, Integer port) {
		if (port==null) port=8080;
		ServerConnector connector = new ServerConnector(jettyServer);
		connector.setPort(port);
		jettyServer.addConnector(connector);
	}

	private Javalin buildApp() {
		Javalin app = Javalin.create(config -> {
			config.bundledPlugins.enableCors(cors -> {
				cors.addRule(corsConfig -> {
					// ?? corsConfig.allowCredentials=true;
					
					// replacement for enableCorsForAllOrigins()
					corsConfig.anyHost();
				});
			});
			
			addOpenApiPlugins(config);

			config.staticFiles.add(staticFiles -> {
				staticFiles.hostedPath = "/";
				staticFiles.location = Location.CLASSPATH; // Specify resources from classpath
				staticFiles.directory = "/tokengine/pub"; // Resource location in classpath
				staticFiles.precompress = false; // if the files should be pre-compressed and cached in memory
													// (optimization)
				staticFiles.aliasCheck = null; // you can configure this to enable symlinks (=
												// ContextHandler.ApproveAliases())
				staticFiles.skipFileFunction = req -> false; // you can use this to skip certain files in the dir, based
																// on the HttpServletRequest
			});
			
			config.useVirtualThreads=true;
		});

		app.exception(Exception.class, (e, ctx) -> {
			e.printStackTrace();
			String message = "Unexpected error: " + e;
			ctx.result(message);
			ctx.status(500);
		});
		
		app.options("/*", ctx-> {
			ctx.status(204); // No context#
			ctx.removeHeader("Content-type");
			ctx.header("access-control-allow-headers", "content-type");
			ctx.header("access-control-allow-methods", "GET,HEAD,PUT,PATCH,POST,DELETE");
			ctx.header("access-control-allow-origin", "*");
			ctx.header("vary","Origin, Access-Control-Request-Headers");
		});
		
		// Header to every response
		app.afterMatched(ctx->{
			// Reflect CORS origin
			String origin = ctx.req().getHeader("Origin");
			if (origin!=null) {
				ctx.header("access-control-allow-origin", "*");
			} else {
				ctx.header("access-control-allow-origin", "*");
			}
		});

		addAPIRoutes(app);	
		return app;
	}
	
	protected void addOpenApiPlugins(JavalinConfig config) {
		String docsPath="openapi-plugin/openapi-tokengine.json";
		
		config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
            pluginConfig
            .withDocumentationPath(docsPath)
            .withDefinitionConfiguration((version, definition) -> {
            	DefinitionConfiguration def=definition;
                def=def.withInfo(
                		info -> {
							info.setTitle("TokEngine REST API");
							info.setVersion("0.1.0");
		                });
            });
		}));

		config.registerPlugin(new SwaggerPlugin(swaggerConfiguration->{
			swaggerConfiguration.setDocumentationPath(docsPath);
		}));
		
		for (JsonSchemaResource generatedJsonSchema : new JsonSchemaLoader().loadGeneratedSchemes()) {
	        System.out.println(generatedJsonSchema.getName());
	    }
	}
	

	private void addAPIRoutes(Javalin app) {
		
		api.addRoutes(app);
		webApp.addRoutes(app);
	}
	

	


	public void close() {
		// TODO Auto-generated method stub
		
	}

}
