package net.authsuite.common.skin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative skin directive sent server -> client (spec §4,
 * {@code PlayerSkinDirectivePacket}).
 * <p>
 * The server is authoritative for {@code playerUUID -> provider skin}. Clients
 * validate every field against the provider policy before rendering.
 */
public record SkinDirective(
        UUID playerUUID,
        String providerId,
        SkinResource skinResource,
        SkinResource capeResource,
        String modelType,
        long revision) {

    public SkinDirective {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(providerId, "providerId");
        if (modelType == null) {
            modelType = "classic";
        }
        if (revision < 0) {
            revision = 0;
        }
    }

    public static SkinDirective copyWithRev(SkinDirective directive, long revision) {
        return new SkinDirective(directive.playerUUID(), directive.providerId(),
                directive.skinResource(), directive.capeResource(), directive.modelType(), revision);
    }

    public Map<String, String> toMetadata() {
        return Map.of(
                "provider", providerId,
                "model", modelType == null ? "classic" : modelType,
                "revision", Long.toString(revision));
    }
}