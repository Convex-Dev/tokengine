package tokengine.api.model;

import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class DepositRequest {
	public TokenSpec source;
	public DepositSpec deposit;
}
