package tokengine;

import static j2html.TagCreator.head;
import static j2html.TagCreator.link;
import static j2html.TagCreator.title;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

import convex.api.ContentTypes;
import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.SourceCodes;
import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.Format;
import convex.core.data.Keyword;
import convex.core.data.Strings;
import convex.core.lang.RT;
import convex.java.JSON;
import io.javalin.Javalin;
import io.javalin.http.Context;
import j2html.tags.DomContent;

public abstract class ATokengineAPI  {

	/**
	 * Make a generic HTTP header
	 * @param title
	 * @return
	 */
	protected DomContent makeHeader(String title) {
		return head(
			title(title), 
			link().withRel("stylesheet").withHref("/css/pico.min.css")
		);
	}
	
	public void prepareResult(Context ctx, Result r) {
		if (r.getSource()==null) {
			r=r.withSource(SourceCodes.SERVER);
		}
		
		int status=statusForResult(r);
		ctx.status(status);
		
		String type = calcResponseContentType(ctx);
		
		if (type.equals(ContentTypes.JSON)) {
			ctx.contentType(ContentTypes.JSON);
			HashMap<String, Object> resultJSON = r.toJSON();
			ctx.result(JSON.toPrettyString(resultJSON));
		} else if (type.equals(ContentTypes.CVX)) {
			ctx.contentType(ContentTypes.CVX);
			AString rs=RT.print(r);
			if (rs==null) {
				rs=RT.print(Result.error(ErrorCodes.LIMIT, Strings.PRINT_EXCEEDED).withSource(SourceCodes.PEER));
				ctx.status(403); // Forbidden because of result size
			}
			ctx.result(rs.toString());
		} else if (type.equals(ContentTypes.CVX_RAW)) {
			ctx.contentType(ContentTypes.CVX_RAW);
			Blob b=Format.encodeMultiCell(r, true);
			ctx.result(b.getBytes());
		} else {
			ctx.contentType(ContentTypes.TEXT);
			ctx.status(415); // unsupported media type for "Accept" header
			ctx.result("Unsupported content type: "+type);
		}
	}
	
	protected String calcResponseContentType(Context ctx) {
		Enumeration<String> accepts=ctx.req().getHeaders("Accept");
		String type=ContentTypes.JSON;
		// TODO: look at quality weights perhaps
		if (accepts!=null) {
			for (String a:Collections.list(accepts)) {
				if (a.contains(ContentTypes.CVX_RAW)) {
					type=ContentTypes.CVX_RAW;
					break;
				}
				if (a.contains(ContentTypes.CVX)) {
					type=ContentTypes.CVX;
				}
			}
		}
		return type;
	}
	
	public int statusForResult(Result r) {
		if (!r.isError()) {
			return 200;
		}
		Keyword source=r.getSource();
		ACell error=r.getErrorCode();
		if (SourceCodes.CVM.equals(source)) {
			return 200;
		} else if (SourceCodes.CODE.equals(source)) {
			return 200;
		} else if (SourceCodes.PEER.equals(source)) {
			if (ErrorCodes.SIGNATURE.equals(error)) return 403; // Forbidden
			if (ErrorCodes.FUNDS.equals(error)) return 402; // payment required
		}
		if (ErrorCodes.FORMAT.equals(error)) return 400; // bad request
		if (ErrorCodes.TIMEOUT.equals(error)) return 408; // timeout
		int status = 422;
		return status;
	}

	public abstract void addRoutes(Javalin javalin);
}
