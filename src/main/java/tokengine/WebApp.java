package tokengine;

import static j2html.TagCreator.a;
import static j2html.TagCreator.article;
import static j2html.TagCreator.aside;
import static j2html.TagCreator.attrs;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.footer;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.h4;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.link;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.title;
import static j2html.TagCreator.tr;

import java.util.ArrayList;

import convex.core.util.Utils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import j2html.tags.DomContent;
import tokengine.adapter.AAdapter;

public class WebApp  {

	protected final Engine engine;

	public WebApp(Engine engine) {
		this.engine=engine;
	}

	public void addRoutes(Javalin javalin) {
		javalin.get("/index.html", this::indexPage);
		javalin.get("/", this::indexPage);
		javalin.get("/404.html", this::missingPage);
	}
	
	private void indexPage(Context ctx) {
		DomContent content= html(
				makeHeader("Tokengine Server"),
				body(
					h1("TokEngine Server"),
					p("Version: "+Utils.getVersion()),
					aside(article(
							h4("Useful links: "),
							p(a("API Explorer").withHref("swagger")),
							p(a("Tokengine Docs").withHref("https://docs.convex.world/docs/products/tokengine"))
					)),
					makeNetworkTable(),

					footer(p("This is the default web page for a Tokengine running a REST API"))
				)
			);
		ctx.result(content.render());
		ctx.header("Content-Type", "text/html");
		ctx.status(200);
	}
	
	private DomContent makeNetworkTable() {
		ArrayList<AAdapter<?>> handlers = engine.getAdapters();
		DomContent div= div(
			h4("Available networks"),	
			table(attrs("#handlers"),
				thead(tr(
			        td("Alias"),
			        td("Chain ID"),
			        td("Description"),
			        td("Operator Address")
			    )),
				tbody(
					handlers.stream().map(handler -> {
						String alias=handler.getAlias();
						return tr(
							td(alias),
		            		td(Utils.toString(handler.getChainID())),
							td(Utils.toString(handler.getDescription())),
							td(Utils.toString(handler.getOperatorAddress()))
						);
					}).toArray(DomContent[]::new)
				)
				),
			h4("Audit Logging"),
			engine.kafka==null?p("Not configured"):p(
				engine.kafka.getURI().toString()
			)
	    );
		return div;
	}

	protected void missingPage(Context ctx) { 
		String type=ctx.header("Accept");
		if ((type!=null)&&type.contains("html")) {
			ctx.header("Content-Type", "text/html");	
			DomContent content= html(
				makeHeader("404: Not Found: "+ctx.path()),
				body(
					h1("404: not found: "+ctx.path()),
					p("This is not the page you are looking for."),
					a("Go back to index").withHref("/index.html")
				)
			);
			ctx.result(content.render());
		} else {
			ctx.result("404 Not found: "+ctx.path());
		}
		ctx.status(404);
	}
	
	private DomContent makeHeader(String title) {
		return head(
				title(title),
		        link().withRel("stylesheet").withHref("/css/pico.min.css")
		);
	}
}
