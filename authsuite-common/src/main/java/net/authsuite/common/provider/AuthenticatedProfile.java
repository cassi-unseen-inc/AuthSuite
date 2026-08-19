package net.authsuite.common.provider;

import java.time.Instant;
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
public record AuthenticatedProfile(
        ProviderId provider,
        String providerAccountId,
        String username,
        UUID providerProfileUuid,
        Map<String, String> textures,
        Map<String, String> textureSignatures,
        long expiresAtMs) {

    public AuthenticatedProfile {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        if (providerAccountId.isBlank()) {
            throw new IllegalArgumentException("providerAccountId must not be blank");
        }
        textures = textures == null ? Map.of() : Map.copyOf(textures);
        textureSignatures = textureSignatures == null ? Map.of() : Map.copyOf(textureSignatures);
        if (expiresAtMs <= 0) {
            throw new IllegalArgumentException("expiresAtMs must be positive");
        }
    }

    /** Convenience constructor without provider property signatures (legacy behavior). */
    public AuthenticatedProfile(
            ProviderId provider, String providerAccountId, String username, UUID providerProfileUuid,
            Map<String, String> textures, long expiresAtMs) {
        this(provider, providerAccountId, username, providerProfileUuid, textures, Map.of(), expiresAtMs);
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

    public static AuthenticatedProfile withTtlSigned(
            ProviderId provider, String accountId, String username, UUID profileUuid,
            Map<String, String> textures, Map<String, String> textureSignatures, long ttlMillis) {
        return new AuthenticatedProfile(provider, accountId, username, profileUuid, textures,
                textureSignatures, System.currentTimeMillis() + ttlMillis);
    }

    public static long nowPlus(long ttlMillis) {
        return System.currentTimeMillis() + ttlMillis;
    }
}