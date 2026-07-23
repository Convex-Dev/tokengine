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
import io.javalin.config.RoutesConfig;
import io.javalin.http.staticfiles.Location;
import io.javalin.openapi.JsonSchemaLoader;
import io.javalin.openapi.JsonSchemaResource;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import tokengine.api.RestAPI;

public class APIServer {

	protected static final Logger log = LoggerFactory.getLogger(APIServer.class.getName());

	/** Port used for the API if none is configured */
	public static final int DEFAULT_PORT = 8080;

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
		javalin=buildApp(port);
		javalin.start();
		log.info("REST API started on port "+javalin.port());
	}

	private Javalin buildApp(Integer port) {
		int bindPort=(port==null)?DEFAULT_PORT:port;
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
				staticFiles.aliasCheck = null; // you can configure this to enable symlinks (=
												// ContextHandler.ApproveAliases())
				staticFiles.skipFileFunction = req -> false; // you can use this to skip certain files in the dir, based
																// on the HttpServletRequest
			});

			config.concurrency.useVirtualThreads=true;

			config.jetty.addConnector((jettyServer, httpConfig) -> {
				ServerConnector connector = new ServerConnector(jettyServer);
				connector.setPort(bindPort);
				return connector;
			});

			addHandlers(config.routes);
		});

		return app;
	}

	private void addHandlers(RoutesConfig routes) {
		routes.exception(Exception.class, (e, ctx) -> {
			e.printStackTrace();
			String message = "Unexpected error: " + e;
			ctx.result(message);
			ctx.status(500);
		});

		routes.options("/*", ctx-> {
			ctx.status(204); // No context#
			ctx.removeHeader("Content-type");
			ctx.header("access-control-allow-headers", "content-type");
			ctx.header("access-control-allow-methods", "GET,HEAD,PUT,PATCH,POST,DELETE");
			ctx.header("access-control-allow-origin", "*");
			ctx.header("vary","Origin, Access-Control-Request-Headers");
		});

		// Header to every response
		routes.afterMatched(ctx->{
			// Reflect CORS origin
			String origin = ctx.req().getHeader("Origin");
			if (origin!=null) {
				ctx.header("access-control-allow-origin", "*");
			} else {
				ctx.header("access-control-allow-origin", "*");
			}
		});

		addAPIRoutes(routes);
	}
	
	protected void addOpenApiPlugins(JavalinConfig config) {
		String docsPath="/openapi";

		config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
            pluginConfig
            .withDocumentationPath(docsPath)
            .withDefinitionConfiguration((version, definition) -> {
                definition.info(info -> {
					info.title("TokEngine REST API");
					info.version("0.1.0");
                });
            });
		}));

		config.registerPlugin(new SwaggerPlugin(swaggerConfiguration->{
			swaggerConfiguration.documentationPath = docsPath;
		}));
		
		for (JsonSchemaResource generatedJsonSchema : new JsonSchemaLoader().loadGeneratedSchemes()) {
	        System.out.println(generatedJsonSchema.getName());
	    }
	}
	

	private void addAPIRoutes(RoutesConfig routes) {

		api.addRoutes(routes);
		webApp.addRoutes(routes);
	}
	

	


	public void close() {
		// TODO Auto-generated method stub
		
	}

}
