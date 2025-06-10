package tokengine.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import convex.core.data.Maps;
import convex.core.data.Vectors;
import convex.core.lang.RT;
import convex.core.util.JSONUtils;
import convex.java.HTTPClients;

public class Kafka {
	
	private URI uri;

	public Kafka(URI topicURI) {
		this.uri=topicURI;
	}
	
	protected static final Logger log=LoggerFactory.getLogger(Kafka.class);

	ContentType CONTENT_TYPE=ContentType.create("application/vnd.kafka.json.v2+json");
	
	@SuppressWarnings("unchecked")
	public CompletableFuture<SimpleHttpResponse> log(ACell value) {
		
		AMap<AString,ACell> record=Maps.of("value",value);
		ACell key=RT.getIn(value, "id");
		if (key!=null) {
			record=(AMap<AString, ACell>) RT.assocIn(record,key,"key");
		}
		
		AMap<AString,ACell> recs=Maps.of("records",Vectors.of(record));
		String data=JSONUtils.toString(recs);
		// System.err.println(data);
		
		try {
			SimpleHttpRequest request=SimpleHttpRequest.create(Method.POST, uri);
			request.setHeader("Accept", "application/vnd.kafka.v2+json");
			request.setBody(data, CONTENT_TYPE);
			CompletableFuture<SimpleHttpResponse> future=HTTPClients.execute(request);
			future.whenCompleteAsync((r,e)->{
				if (e!=null) {
					log.warn("Kafka send failed for "+data,e);
				}
				int code=r.getCode();
				if (code>=300) {
					log.warn("Kafka post failed with code "+code);
					log.warn("Payload: "+r.getBodyText());
				}
			});
			return future;
		} catch (Exception e) {
			log.warn("Kafka post failed for "+data,e);
			return null;
		}
	}
	
	public static void main(String[] args) throws URISyntaxException {
		Kafka k=new Kafka(new URI("https://kfk.walledchannel.net/topics/audit"));
		k.log(JSONUtils.parse("{\"test\":true,\"id\":\"12456\"}"));
	}
}
