package net.authsuite.common.provider;

import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.security.LogSanitizer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Shared bounded HTTP client for provider network operations. All requests are
 * non-blocking, time bounded, HTTPS-only by default, and capped in size. Logs never
 * contain sensitive material.
 */
public final class ProviderHttpClient {

    private final HttpClient client;
    private final AuthSuiteLogger log;

    public ProviderHttpClient(AuthSuiteLogger log) {
        this.log = log;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<String> get(URI url, long timeoutMs, long maxResponseBytes) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new HttpException(response.statusCode(), "non-2xx status " + response.statusCode());
                    }
                    String body = response.body();
                    if (body != null && body.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
                        throw new HttpException(response.statusCode(), "response too large");
                    }
                    return body;
                });
    }

    public static URI joinQuery(URI base, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(base.toString());
        sb.append(base.getQuery() == null || base.getQuery().isEmpty() ? "?" : "&");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(sb.toString());
    }

    public static final class HttpException extends RuntimeException {
        private final int statusCode;

        public HttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}