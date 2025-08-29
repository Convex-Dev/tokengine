package tokengine.exception;

/**
 * Exception type representing a failed HTTP response
 */
@SuppressWarnings("serial")
public class ResponseException extends RuntimeException {

	Object response;
	
	public ResponseException(String message, Object response) {
		super(message);
		this.response=response;
	}

	public Object getResponse() {
		return response;
	}
}
