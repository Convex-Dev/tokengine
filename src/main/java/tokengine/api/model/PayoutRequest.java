package tokengine.api.model;

import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class PayoutRequest {
	public TokenSpec source;
	public TokenSpec destination;
	public String quantity;
}
