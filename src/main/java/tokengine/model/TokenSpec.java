package tokengine.model;
import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class TokenSpec {
	public String type;
	public String network;
	public String token;
}
