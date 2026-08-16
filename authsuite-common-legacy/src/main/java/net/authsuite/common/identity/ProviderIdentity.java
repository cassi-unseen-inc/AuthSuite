package net.authsuite.common.identity;

import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.provider.ProviderId;

import java.util.Collections;
import java.util.HashMap;
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
public final class ProviderIdentity {

    private final ProviderId provider;
    private final String providerAccountId;
    private final String username;
    private final UUID providerProfileUuid;
    private final UUID minecraftUUID;
    private final Map<String, String> metadata;

    public ProviderIdentity(ProviderId provider, String providerAccountId, String username,
                            UUID providerProfileUuid, UUID minecraftUUID, Map<String, String> metadata) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        if (minecraftUUID == null) {
            minecraftUUID = CanonicalUuid.from(provider.providerId(), providerAccountId);
        }
        if (metadata == null) {
            metadata = Collections.emptyMap();
        } else {
            metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
        }
        this.provider = provider;
        this.providerAccountId = providerAccountId;
        this.username = username;
        this.providerProfileUuid = providerProfileUuid;
        this.minecraftUUID = minecraftUUID;
        this.metadata = metadata;
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

    public UUID minecraftUUID() {
        return minecraftUUID;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    /** Whether this identity's provider is still enabled in config. */
    public boolean providerActive(AuthProvider provider) {
        return provider != null && provider.isEnabled() && provider.providerId().equals(provider.providerId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderIdentity)) {
            return false;
        }
        ProviderIdentity that = (ProviderIdentity) o;
        return provider.equals(that.provider)
                && providerAccountId.equals(that.providerAccountId)
                && username.equals(that.username)
                && Objects.equals(providerProfileUuid, that.providerProfileUuid)
                && minecraftUUID.equals(that.minecraftUUID)
                && metadata.equals(that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providerAccountId, username, providerProfileUuid, minecraftUUID, metadata);
    }

    @Override
    public String toString() {
        return provider.shortcode() + ":" + providerAccountId;
    }
}