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
public record HybridIdentity(
        String providerId,
        String providerAccountId,
        String username,
        UUID minecraftUUID,
        String sessionId,
        String providerMetadata) {

    public HybridIdentity {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerAccountId, "providerAccountId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(minecraftUUID, "minecraftUUID");
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
}