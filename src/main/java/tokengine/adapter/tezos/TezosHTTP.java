package tokengine.adapter.tezos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.data.ACell;
import convex.core.util.JSON;
import tokengine.exception.ResponseException;

/**
 * Utility class for making HTTP calls to Tezos APIs and returning parsed JSON as ACell objects.
 * Similar to the pattern used in tokengine.client.Client.
 */
public class TezosHTTP {
    
    private static final Logger log = LoggerFactory.getLogger(TezosHTTP.class.getName());
    
    private final HttpClient httpClient;
    private final String apiUrl;
    
    public TezosHTTP(String apiUrl) {
        this.apiUrl = apiUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Makes an HTTP API call to the Tezos API and returns the parsed JSON as an ACell
     * @param endpoint The API endpoint (without base URL)
     * @return CompletableFuture containing the parsed JSON response as ACell
     */
    public CompletableFuture<ACell> makeApiCall(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + endpoint))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> {
                    if (response.statusCode() != 200) {
                        throw new ResponseException("API call failed with status: " + response.statusCode(),response);
                    }
                    
                    try {
                        return JSON.parse(response.body());
                    } catch (Exception e) {
                        log.error("Failed to parse JSON response: {}", e.getMessage());
                        throw new RuntimeException("Failed to parse JSON response", e);
                    }
                });
                
        } catch (Exception e) {
            CompletableFuture<ACell> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * Gets account information from the Tezos API
     * @param address The Tezos address to query
     * @return CompletableFuture containing account data as ACell
     */
    public CompletableFuture<ACell> getAccountInfo(String address) {
        return makeApiCall("/v1/accounts/" + address);
    }
    
    /**
     * Gets token balances for an account
     * @param address The Tezos address to query
     * @return CompletableFuture containing token balances as ACell
     */
    public CompletableFuture<ACell> getTokenBalances(String address) {
        return makeApiCall("/v1/accounts/" + address + "/token_balances");
    }
    
    /**
     * Gets transaction information from the Tezos API
     * @param txHash The transaction hash to query
     * @return CompletableFuture containing transaction data as ACell
     */
    public CompletableFuture<ACell> getTransactionInfo(String txHash) {
        return makeApiCall("/v1/operations/transactions/" + txHash);
    }
    
    /**
     * Closes the HTTP client
     */
    public void close() {
        // HttpClient doesn't need explicit closing in modern Java
        // This method is provided for consistency with other resource management patterns
    }

	public CompletableFuture<ACell> getTokenBalance(String address, String contractAddress) {
        return makeApiCall("/v1/tokens/balances?account=" + address + "&token.contract="+contractAddress);
    }
} 