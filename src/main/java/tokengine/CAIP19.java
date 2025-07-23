package tokengine;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class CAIP19 {
	
	public static String urlDecode(String value) {
	    return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
	
	public static String urlEncode(String value) {
	    return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
