package net.authsuite.common.provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The result of a successful third-party authentication.
 * <p>
 * The {@code providerAccountId} field MUST come from the provider's validated
 * authentication response - never from client-supplied data. The canonical
 * Minecraft UUID is derived from {@code providerId + providerAccountId} and is
 * computed separately (see {@link net.authsuite.common.identity.CanonicalUuid}).
 */
public final class AuthenticatedProfile {

    private final ProviderId provider;
    private final String providerAccountId;
    private final String username;
    private final UUID providerProfileUuid;
    private final Map<String, String> textures;
    private final long expiresAtMs;

    public AuthenticatedProfile(ProviderId provider, String providerAccountId, String username,
                                UUID providerProfileUuid, Map<String, String> textures, long expiresAtMs) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        if (providerAccountId.trim().isEmpty()) {
            throw new IllegalArgumentException("providerAccountId must not be blank");
        }
        if (expiresAtMs <= 0) {
            throw new IllegalArgumentException("expiresAtMs must be positive");
        }
        this.provider = provider;
        this.providerAccountId = providerAccountId;
        this.username = username;
        this.providerProfileUuid = providerProfileUuid;
        this.textures = textures == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(textures));
        this.expiresAtMs = expiresAtMs;
    }

    public ProviderId provider() {
        return provider;
    }

    public String providerAccountId() {
        return providerAccountId;
    }

    public String username() {
        return username;
    }

    public UUID providerProfileUuid() {
        return providerProfileUuid;
    }

    public Map<String, String> textures() {
        return textures;
    }

    public long expiresAtMs() {
        return expiresAtMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMs;
    }

    /** Sanitized description safe for logging. Contains no secret material. */
    public String safeDescription() {
        return provider.shortcode() + ":" + providerAccountId + "@" + username;
    }

    public static AuthenticatedProfile withTtl(
            ProviderId provider, String accountId, String username, UUID profileUuid,
            Map<String, String> textures, long ttlMillis) {
        return new AuthenticatedProfile(provider, accountId, username, profileUuid, textures,
                System.currentTimeMillis() + ttlMillis);
    }

    public static long nowPlus(long ttlMillis) {
        return System.currentTimeMillis() + ttlMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthenticatedProfile)) {
            return false;
        }
        AuthenticatedProfile that = (AuthenticatedProfile) o;
        return expiresAtMs == that.expiresAtMs
                && provider.equals(that.provider)
                && providerAccountId.equals(that.providerAccountId)
                && username.equals(that.username)
                && Objects.equals(providerProfileUuid, that.providerProfileUuid)
                && textures.equals(that.textures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providerAccountId, username, providerProfileUuid, textures, expiresAtMs);
    }

    @Override
    public String toString() {
        return safeDescription();
    }
}