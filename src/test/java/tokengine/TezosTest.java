package tokengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import convex.core.data.Blob;
import convex.core.data.Blobs;
import tokengine.util.Base58;

public class TezosTest {

	
	@Test public void testBalance() {
		
	}
	
	@Test public void testHelpers() {
		Blob b=Blobs.createRandom(30);
		byte[] bs=b.getBytes();
		
		String base58=Base58.encode(bs);
		byte[] bs2=Base58.decode(base58);
		
		assertEquals(b,Blob.wrap(bs2));
	}
	
	@Test public void testAddress() {
		String addr="tz1MJx9vhaNRSimcuXPK2rW4fLccQnDAnVKJ";
		byte[] bs=Base58.decode(addr.substring(3));
		assertEquals(24,bs.length);
	}
}
