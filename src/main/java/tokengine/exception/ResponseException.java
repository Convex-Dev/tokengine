package tokengine.exception;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

/**
 * Exception type representing a failed HTTP response
 */
@SuppressWarnings("serial")
public class ResponseException extends RuntimeException {

	public ResponseException(String message, SimpleHttpResponse resp) {
		super(message);
	}

}
