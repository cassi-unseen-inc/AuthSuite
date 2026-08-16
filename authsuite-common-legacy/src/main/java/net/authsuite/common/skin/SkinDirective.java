package net.authsuite.common.skin;

import java.util.HashMap;
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
public final class SkinDirective {

    private final UUID playerUUID;
    private final String providerId;
    private final SkinResource skinResource;
    private final SkinResource capeResource;
    private final String modelType;
    private final long revision;

    public SkinDirective(UUID playerUUID, String providerId, SkinResource skinResource,
                         SkinResource capeResource, String modelType, long revision) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(providerId, "providerId");
        if (modelType == null) {
            modelType = "classic";
        }
        if (revision < 0) {
            revision = 0;
        }
        this.playerUUID = playerUUID;
        this.providerId = providerId;
        this.skinResource = skinResource;
        this.capeResource = capeResource;
        this.modelType = modelType;
        this.revision = revision;
    }

    public UUID playerUUID() {
        return playerUUID;
    }

    public String providerId() {
        return providerId;
    }

    public SkinResource skinResource() {
        return skinResource;
    }

    public SkinResource capeResource() {
        return capeResource;
    }

    public String modelType() {
        return modelType;
    }

    public long revision() {
        return revision;
    }

    public static SkinDirective copyWithRev(SkinDirective directive, long revision) {
        return new SkinDirective(directive.playerUUID(), directive.providerId(),
                directive.skinResource(), directive.capeResource(), directive.modelType(), revision);
    }

    public Map<String, String> toMetadata() {
        Map<String, String> out = new HashMap<>();
        out.put("provider", providerId);
        out.put("model", modelType == null ? "classic" : modelType);
        out.put("revision", Long.toString(revision));
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkinDirective)) {
            return false;
        }
        SkinDirective that = (SkinDirective) o;
        return revision == that.revision
                && playerUUID.equals(that.playerUUID)
                && providerId.equals(that.providerId)
                && Objects.equals(skinResource, that.skinResource)
                && Objects.equals(capeResource, that.capeResource)
                && Objects.equals(modelType, that.modelType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUUID, providerId, skinResource, capeResource, modelType, revision);
    }

    @Override
    public String toString() {
        return "SkinDirective[playerUUID=" + playerUUID + ", providerId=" + providerId + "]";
    }
}