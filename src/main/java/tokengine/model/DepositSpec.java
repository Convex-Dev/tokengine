package tokengine.model;
import io.javalin.openapi.OpenApiByFields;

@OpenApiByFields
public class DepositSpec {
	public String tx;
	public String msg;
	public String sig;
}
