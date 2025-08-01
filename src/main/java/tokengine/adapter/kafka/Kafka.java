package tokengine.adapter.kafka;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.lang.RT;
import convex.core.util.JSONUtils;
import convex.java.HTTPClients;
import tokengine.Engine;
import tokengine.Fields;

public class Kafka {
	protected static final Logger log=LoggerFactory.getLogger(Kafka.class);
	
	private URI uri=null; // URI can be null if audit logging disabled / unavailable

	/**
	 * Create a kafka logging instance
	 * @param kafkaLoc
	 */
	public Kafka(AString kafkaLoc) {
		try {
			this.uri=(kafkaLoc==null)?null:new URI(kafkaLoc.toString());
		} catch (URISyntaxException e) {
			log.warn("Unable to parse Kafka URL: "+kafkaLoc,e);
		}
	}

	/** Content type for Kafka logs */
	ContentType CONTENT_TYPE=ContentType.create("application/vnd.kafka.json.v2+json");
	
	/**
	 * We use a single thread executor to ensure log messages get sent in the order they are submitted
	 */
	ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * Log a value to Kafka. Can be any JSON value
	 * @param value
	 * @return true if successfully submitted
	 */
	public boolean log(AString key, AMap<AString,ACell> value) {
		executor.submit(()->{
			if (uri==null) return; // TODO: maybe print one warning?
			try {
				doLog(key,value);
			} catch (Exception e) {
				log.warn("Failed to queue audit log message to Kafka",e);
			}
		});
		return true;
	}
	
	public CompletableFuture<SimpleHttpResponse> doLog(AString key,AMap<AString,ACell> value) {
		// Construct Kafka message with one record
		AMap<AString,ACell> record=Maps.of("value",value);
		
		// Populate key if available
		if (key==null) key=RT.getIn(value, Fields.KEY);
		if (key==null) key=Engine.getRequest();
		if (key!=null) {
			record=record.assoc(Fields.KEY, key);
			value=value.assoc(Fields.KEY,key);
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
					return;
				}
				int code=r.getCode();
				if (code>=300) {
					log.warn("Kafka post failed with code "+code);
					log.warn("Payload: "+r.getBodyText());
				}
				// System.err.println(data);
			});
			return future;
		} catch (Exception e) {
			log.warn("Kafka post failed for "+data,e);
			return null;
		}
	}
	
	public static void main(String[] args) throws URISyntaxException {
		Kafka k=new Kafka(Strings.create("https://kfk.walledchannel.net/topics/test"));
		k.log(Fields.TEST,Maps.of("test",true,"id","12456"));
	}

	public URI getURI() {
		return uri;
	}
	
	public void close() {
		executor.shutdown();
		uri=null;
	}
}
