package tokengine.api.model;

import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class BalanceRequest {
	public TokenSpec source;
	public String address;
}
