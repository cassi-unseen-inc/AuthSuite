package net.authsuite.common.provider;

import java.util.Objects;

/**
 * Immutable identity of an authentication provider.
 * <p>
 * The {@link #providerId()} plus {@link #providerAccountId()} pair forms the
 * immutable primary key for a {@link net.authsuite.common.identity.ProviderIdentity}.
 * A provider is never created from untrusted client input; client-supplied
 * identifiers only ever select a provider that is already configured.
 */
public record ProviderId(String providerId, String shortcode, String domain, String displayName) {

    public ProviderId {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(shortcode, "shortcode");
        Objects.requireNonNull(domain, "domain");
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
    }

    public ProviderId withAccount(String providerAccountId) {
        return this;
    }

    /** Sentinel for unclassifiable provider failures; never used as a real provider. */
    public static ProviderId unknown() {
        return new ProviderId("?", "?", "?", "unknown");
    }

    @Override
    public String toString() {
        return shortcode + ":" + providerId;
    }
}