package net.authsuite.common.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * The canonical in-engine identity (spec §9, HybridIdentity equivalent).
 * <p>
 * Resolution chain: AuthProvider -> {@link ProviderIdentity} -> HybridIdentity ->
 * canonical GameProfile. Only the independently authenticated provider identity
 * participates in canonical identity generation.
 */
public final class HybridIdentity {

    private final String providerId;
    private final String providerAccountId;
    private final String username;
    private final UUID minecraftUUID;
    private final String sessionId;
    private final String providerMetadata;

    public HybridIdentity(String providerId, String providerAccountId, String username,
                          UUID minecraftUUID, String sessionId, String providerMetadata) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(minecraftUUID, "minecraftUUID");
        this.providerId = providerId;
        this.providerAccountId = providerAccountId;
        this.username = username;
        this.minecraftUUID = minecraftUUID;
        this.sessionId = sessionId;
        this.providerMetadata = providerMetadata;
    }

    public String providerId() {
        return providerId;
    }

    public String providerAccountId() {
        return providerAccountId;
    }

    public String username() {
        return username;
    }

    public UUID minecraftUUID() {
        return minecraftUUID;
    }

    public String sessionId() {
        return sessionId;
    }

    public String providerMetadata() {
        return providerMetadata;
    }

    public static HybridIdentity from(ProviderIdentity identity, String sessionId, String providerMetadata) {
        return new HybridIdentity(
                identity.provider().providerId(),
                identity.providerAccountId(),
                identity.username(),
                identity.minecraftUUID(),
                sessionId,
                providerMetadata);
    }

    /** Human-safe log label; never contains secrets. */
    public String logLabel() {
        return providerId + ":" + providerAccountId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HybridIdentity)) {
            return false;
        }
        HybridIdentity that = (HybridIdentity) o;
        return providerId.equals(that.providerId)
                && providerAccountId.equals(that.providerAccountId)
                && username.equals(that.username)
                && minecraftUUID.equals(that.minecraftUUID)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(providerMetadata, that.providerMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, providerAccountId, username, minecraftUUID, sessionId, providerMetadata);
    }

    @Override
    public String toString() {
        return logLabel();
    }
}