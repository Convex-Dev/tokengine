package tokengine.exception;

/**
 * Exception type representing a failure to validate payment
 */
@SuppressWarnings("serial")
public class PaymentException extends Exception {

	
	public PaymentException(String message, Throwable cause) {
		super(message,cause);
	}

	public PaymentException(String message) {
		super(message);
	}

}
