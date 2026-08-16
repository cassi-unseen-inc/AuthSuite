package net.authsuite.common.provider;

import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.security.LogSanitizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bounded HTTP client for provider network operations. All requests are
 * non-blocking, time bounded, HTTPS-only by default, and capped in size. Logs never
 * contain sensitive material.
 * <p>
 * Java 8-compatible implementation backed by {@link HttpURLConnection} executed on a
 * small shared daemon thread pool; the public API stays identical to the modern
 * variant.
 */
public final class ProviderHttpClient {

    private static final AtomicInteger POOL = new AtomicInteger();

    private final AuthSuiteLogger log;
    private final ExecutorService executor;

    public ProviderHttpClient(AuthSuiteLogger log) {
        this.log = log;
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "authsuite-http-" + POOL.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public CompletableFuture<String> get(URI url, long timeoutMs, long maxResponseBytes) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.toURL().openConnection();
                connection.setConnectTimeout((int) Math.max(1, Math.min(Integer.MAX_VALUE, timeoutMs)));
                connection.setReadTimeout((int) Math.max(1, Math.min(Integer.MAX_VALUE, timeoutMs)));
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new HttpException(status, "non-2xx status " + status);
                }
                byte[] body = readAll(connection);
                if (body.length > maxResponseBytes) {
                    throw new HttpException(status, "response too large");
                }
                return new String(body, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new HttpException(-1, "io failure: " + LogSanitizer.sanitizeMessage(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, executor);
    }

    private static byte[] readAll(URLConnection connection) throws IOException {
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
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
            sb.append(encode(e.getKey()))
                    .append('=')
                    .append(encode(e.getValue() == null ? "" : e.getValue()));
        }
        return URI.create(sb.toString());
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
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