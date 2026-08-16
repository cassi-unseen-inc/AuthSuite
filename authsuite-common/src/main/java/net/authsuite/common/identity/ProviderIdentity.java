package net.authsuite.common.identity;

import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.provider.ProviderId;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A provider-validated identity record. Created exclusively from an
 * {@link net.authsuite.common.provider.AuthenticatedProfile} returned by a provider.
 * <p>
 * The {@code providerId + providerAccountId} pair is the immutable primary key;
 * the canonical {@code minecraftUUID} is derived from it (spec §9).
 */
public record ProviderIdentity(
        ProviderId provider,
        String providerAccountId,
        String username,
        UUID providerProfileUuid,
        UUID minecraftUUID,
        Map<String, String> metadata) {

    public ProviderIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        if (minecraftUUID == null) {
            minecraftUUID = CanonicalUuid.from(provider.providerId(), providerAccountId);
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Whether this identity's provider is still enabled in config. */
    public boolean providerActive(AuthProvider provider) {
        return provider != null && provider.isEnabled() && provider.providerId().equals(provider.providerId());
    }

    @Override
    public String toString() {
        return provider.shortcode() + ":" + providerAccountId;
    }
}