package net.authsuite.common.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.authsuite.common.config.ProviderConfig;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.security.LogSanitizer;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A Mojang-Authlib protocol-compatible auth provider (the same protocol
 * alternative-auth and authlib-injector speak). The connecting client registers a
 * {@code hasJoined} hash with the provider's session server during the native
 * Authlib key exchange; the server independently re-validates that hash by calling
 * the provider's {@code checkUrl}.
 * <p>
 * This is the driver used for LittleSkins (LS) and Ely.by (EB), and also works for
 * Microsoft/Mojang (MA) through the canonical sessionserver endpoint.
 */
public final class AuthlibProvider implements AuthProvider {

    private final ProviderConfig config;
    private final ProviderId providerId;
    private final ProviderHttpClient http;
    private final AuthSuiteLogger log;

    public AuthlibProvider(ProviderConfig config, ProviderHttpClient http, AuthSuiteLogger log) {
        this.config = config;
        this.providerId = new ProviderId(config.id(), config.shortcode(), config.domain(), config.id());
        this.http = http;
        this.log = log;
    }

    public ProviderConfig config() {
        return config;
    }

    @Override
    public String providerId() {
        return providerId.providerId();
    }

    @Override
    public String shortcode() {
        return providerId.shortcode();
    }

    @Override
    public String domain() {
        return providerId.domain();
    }

    @Override
    public boolean isEnabled() {
        return config.enabled();
    }

    @Override
    public CompletableFuture<AuthenticatedProfile> authenticate(AuthAttempt attempt) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (config.checkUrl() == null || config.checkUrl().isBlank()) {
            return CompletableFuture.failedFuture(new ProviderFailureException(
                    ProviderFailure.invalidAttempt(providerId, "no check_url configured")));
        }
        Map<String, String> params = new HashMap<>();
        params.put("username", attempt.username());
        params.put("serverId", attempt.serverId());
        if (config.sendIp() && attempt.clientAddress() != null) {
            params.put("ip", attempt.clientAddress().getHostAddress());
        }
        URI url = ProviderHttpClient.joinQuery(URI.create(config.checkUrl()), params);
        log.debug("auth {} querying provider {}", attempt.username(), providerId.shortcode());
        return http.get(url, 10_000, 1_048_576)
                .thenApply(body -> parseResponse(attempt, body))
                .exceptionally(ex -> unwrap(ex, attempt));
    }

    private AuthenticatedProfile parseResponse(AuthAttempt attempt, String body) {
        if (body == null || body.isEmpty()) {
            throw new ProviderFailureException(ProviderFailure.accountNotFound(providerId, "empty response"));
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            log.debug("provider {} returned malformed JSON", providerId.shortcode());
            throw new ProviderFailureException(ProviderFailure.authFailed(providerId, "malformed response"));
        }
        if (!element.isJsonObject()) {
            throw new ProviderFailureException(ProviderFailure.accountNotFound(providerId, "not a profile object"));
        }
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has("id")) {
            throw new ProviderFailureException(ProviderFailure.accountNotFound(providerId, "no id in response"));
        }
        String rawId = obj.get("id").getAsString();
        String accountId = normalizeAccountId(rawId);
        String playerName = obj.has("name") ? obj.get("name").getAsString() : attempt.username();
        UUID profileUuid = parseUuid(rawId);
        Map<String, String> textures = new HashMap<>();
        if (obj.has("properties") && obj.get("properties").isJsonArray()) {
            JsonArray props = obj.getAsJsonArray("properties");
            for (JsonElement prop : props) {
                if (prop.isJsonObject()) {
                    JsonObject po = prop.getAsJsonObject();
                    String name = po.has("name") ? po.get("name").getAsString() : null;
                    String value = po.has("value") ? po.get("value").getAsString() : null;
                    if (name != null && value != null) {
                        textures.put(name, value);
                    }
                }
            }
        }
        log.debug("auth {} succeeded via {}", attempt.username(), providerId.shortcode());
        return AuthenticatedProfile.withTtl(providerId, accountId, playerName, profileUuid,
                Map.copyOf(textures), 300_000);
    }

    private AuthenticatedProfile unwrap(Throwable ex, AuthAttempt attempt) {
        while (ex instanceof CompletionException && ex.getCause() != null) {
            ex = ex.getCause();
        }
        if (ex instanceof ProviderFailureException pfe) {
            throw pfe;
        }
        Throwable cause = ex;
        if (cause instanceof ProviderHttpClient.HttpException httpEx) {
            if (httpEx.statusCode() == 429 || httpEx.statusCode() == 420) {
                throw new ProviderFailureException(ProviderFailure.rateLimited(providerId, "rate limited"));
            }
            if (httpEx.statusCode() >= 500) {
                throw new ProviderFailureException(ProviderFailure.unavailable(providerId, "provider error"));
            }
            throw new ProviderFailureException(ProviderFailure.authFailed(providerId, "http " + httpEx.statusCode()));
        }
        log.warn("auth {} provider {} unexpected: {}", attempt.username(), providerId.shortcode(),
                LogSanitizer.sanitizeMessage(cause));
        throw new ProviderFailureException(ProviderFailure.unavailable(providerId, "io failure"));
    }

    private String normalizeAccountId(String uuid) {
        return uuid.replace("-", "").toLowerCase();
    }

    private UUID parseUuid(String id) {
        String raw = id.replace("-", "");
        if (raw.length() != 32) {
            return UUID.nameUUIDFromBytes((providerId.providerId() + ":" + id).getBytes());
        }
        StringBuilder sb = new StringBuilder(raw);
        sb.insert(20, '-').insert(16, '-').insert(12, '-').insert(8, '-');
        return UUID.fromString(sb.toString());
    }

    @Override
    public CompletableFuture<ResolverResult> resolvePlayer(String username) {
        if (config.profilesUrl() == null || config.profilesUrl().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        URI url = ProviderHttpClient.joinQuery(URI.create(config.profilesUrl()),
                Map.of("username", username));
        return http.get(url, 10_000, 1_048_576)
                .thenApply(body -> parseResolver(body))
                .exceptionally(ex -> {
                    Throwable c = unwrapInner(ex);
                    log.debug("resolvePlayer {} on {} failed: {}", username, providerId.shortcode(),
                            LogSanitizer.sanitizeMessage(c));
                    return null;
                });
    }

    private ResolverResult parseResolver(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element.isJsonObject()) {
                String id = element.getAsJsonObject().has("id")
                        ? element.getAsJsonObject().get("id").getAsString()
                        : null;
                String name = element.getAsJsonObject().has("name")
                        ? element.getAsJsonObject().get("name").getAsString()
                        : null;
                return id == null ? null : new ResolverResult(normalizeAccountId(id), name);
            }
            if (element.isJsonArray()) {
                JsonArray arr = element.getAsJsonArray();
                if (arr.isEmpty()) {
                    return null;
                }
                JsonObject first = arr.get(0).getAsJsonObject();
                String id = first.has("id") ? first.get("id").getAsString() : null;
                String name = first.has("name") ? first.get("name").getAsString() : null;
                return id == null ? null : new ResolverResult(normalizeAccountId(id), name);
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<SessionValidity> validateSession(String providerAccountId) {
        // Stateless hasJoined-style providers have no independent session endpoint;
        // re-authentication is the validation path. Marking VALID avoids blocking.
        return CompletableFuture.completedFuture(SessionValidity.VALID);
    }

    @Override
    public CompletableFuture<Void> disconnect(String providerAccountId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String toString() {
        return providerId.toString();
    }

    static Throwable unwrapInner(Throwable ex) {
        Throwable c = ex;
        while (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }
}