package tokengine.api.model;

import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class PayoutRequest {
	public TokenSpec destination;
	public String quantity;
}
