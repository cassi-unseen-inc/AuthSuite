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
public final class ProviderId {

    private final String providerId;
    private final String shortcode;
    private final String domain;
    private final String displayName;

    public ProviderId(String providerId, String shortcode, String domain, String displayName) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(shortcode, "shortcode");
        Objects.requireNonNull(domain, "domain");
        if (providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        this.providerId = providerId;
        this.shortcode = shortcode;
        this.domain = domain;
        this.displayName = displayName;
    }

    public String providerId() {
        return providerId;
    }

    public String shortcode() {
        return shortcode;
    }

    public String domain() {
        return domain;
    }

    public String displayName() {
        return displayName;
    }

    public ProviderId withAccount(String providerAccountId) {
        return this;
    }

    /** Sentinel for unclassifiable provider failures; never used as a real provider. */
    public static ProviderId unknown() {
        return new ProviderId("?", "?", "?", "unknown");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderId)) {
            return false;
        }
        ProviderId that = (ProviderId) o;
        return providerId.equals(that.providerId)
                && shortcode.equals(that.shortcode)
                && domain.equals(that.domain)
                && displayName.equals(that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, shortcode, domain, displayName);
    }

    @Override
    public String toString() {
        return shortcode + ":" + providerId;
    }
}