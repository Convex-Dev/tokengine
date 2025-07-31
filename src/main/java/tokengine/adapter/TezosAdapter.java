package tokengine.adapter;

import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AString;
import tokengine.Engine;

public abstract class TezosAdapter extends AAdapter<AString> {

	protected TezosAdapter(Engine engine, AMap<AString,ACell> config) {
		super(engine, config);
	}

}
