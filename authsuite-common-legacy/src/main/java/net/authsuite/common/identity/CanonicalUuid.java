package net.authsuite.common.identity;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic canonical UUID generation (spec §9):
 * {@code uuid = UUID.nameUUIDFromBytes(("AuthSuite:" + providerId + ":" + providerAccountId).getBytes(UTF_8))}
 * <p>
 * This is a type-5 SHA-1-derived UUID. It is deterministic per
 * (providerId, providerAccountId) and independent of username, shortcode, and any
 * client-supplied value. Only independently authenticated provider account ids may
 * feed this mapping.
 */
public final class CanonicalUuid {

    private CanonicalUuid() {
    }

    /** Computes the canonical Minecraft UUID for a provider identity key. */
    public static UUID from(String providerId, String providerAccountId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        if (providerId.trim().isEmpty() || providerAccountId.trim().isEmpty()) {
            throw new IllegalArgumentException("canonical UUID requires non-blank providerId and accountId");
        }
        String seed = "AuthSuite:" + providerId + ":" + providerAccountId;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}