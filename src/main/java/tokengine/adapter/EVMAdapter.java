package tokengine.adapter;

import java.io.IOException;
import java.math.BigInteger;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.contracts.eip20.generated.ERC20;
import org.web3j.crypto.Credentials;

import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.data.Blob;
import convex.core.data.prim.AInteger;

public class EVMAdapter extends AAdapter {

	protected EVMAdapter(String chainID) {
		super(chainID);
	}

	Web3j web3;
	
	public static EVMAdapter create(String chainID) {
		return new EVMAdapter(chainID);
	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
		web3 = Web3j.build(new HttpService("https://sepolia.drpc.org"));
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override 
	public AInteger getBalance(String asset, String address) throws IOException {
		if (isEth(asset)) {
			EthGetBalance balanceResponse = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send(); 
			if (balanceResponse.hasError()) {
				throw new IllegalStateException("Can't get ETH balance");
			} else {
				BigInteger bal=balanceResponse.getBalance();
				return AInteger.create(bal);
			}
		} else if (asset.startsWith("erc20")) {
			String contractAddress=asset.substring(6); // skip 'erc20:'
			
			Credentials cred=Credentials.create("0x0", address);
			
			ERC20 contract = ERC20.load(contractAddress, web3, cred, new DefaultGasProvider());
			try {
				BigInteger bi=contract.balanceOf(address).send();
				return AInteger.create(bi);
			} catch (Exception e) {
				throw new IOException("Failure getting ERC20 balance",e);
			}
		}
		
		throw new UnsupportedOperationException("Asset not supported in EVMAdapter: "+asset);
	}

	private boolean isEth(String asset) {
		if ("ETH".equals(asset)) return true;
		return "slip44:60".equals(asset);
	}

	@Override
	public String parseAddress(String caip10) throws IllegalArgumentException {
		Blob b=Blob.parse(caip10);
		if (b==null) {
			throw new IllegalArgumentException("Invlid hex address for EVM Adapter");
		}
		if (b.count()!=20) {
			throw new IllegalArgumentException("Invlid hex length for EVM Adapter");
		}
		return b.toString();
	}

	@Override
	public Result payout(String token, AInteger quantity, String destAccount) {
		return Result.error(ErrorCodes.TODO, "Asset payout not supported: "+token);
	}
}


	
