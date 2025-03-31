package tokengine.model;

import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class TransferRequest {
	public TokenSpec source;
	public TokenSpec destination;
	public String quantity;
}
