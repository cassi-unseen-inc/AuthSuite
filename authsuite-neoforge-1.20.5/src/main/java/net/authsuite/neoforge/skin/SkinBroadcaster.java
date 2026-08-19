package net.authsuite.neoforge.skin;

import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinResource;
import net.authsuite.neoforge.network.PlayerSkinDirectivePayload;
import net.authsuite.neoforge.ops.OpsRouter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and broadcasts authoritative skin directives (spec §4).
 * <p>
 * The server is authoritative for {@code playerUUID -> provider skin}; clients
 * validate every field against the provider policy before rendering.
 */
public final class SkinBroadcaster {

    private final OpsRouter opsRouter;
    private final IdentityRegistry identityRegistry;
    private final AuthSuiteLogger log;
    private final Map<UUID, SkinDirective> directives = new ConcurrentHashMap<>();

    public SkinBroadcaster(OpsRouter opsRouter, IdentityRegistry identityRegistry, AuthSuiteLogger log) {
        this.opsRouter = opsRouter;
        this.identityRegistry = identityRegistry;
        this.log = log;
    }

    /** Called at login resolution to seed the authoritative directive. */
    public void cache(SkinDirective directive) {
        if (directive != null) {
            directives.put(directive.playerUUID(), directive);
        }
    }

    public SkinDirective directiveFor(UUID canonicalUuid) {
        SkinDirective cached = directives.get(canonicalUuid);
        if (cached != null) {
            return cached;
        }
        return identityRegistry.byUuid(canonicalUuid)
                .map(reg -> buildFromIdentity(reg.identity()))
                .orElse(null);
    }

    /**
     * Bidirectional skin sync (post-audit §9) for a freshly logged-in player:
     * the newcomer's directive is sent to every already-online player (so their
     * client renders the newcomer's provider skin) and every known directive is
     * sent to the newcomer (so they render everyone else's skins). All directives
     * are keyed by canonical UUID; the client remains the security authority and
     * re-validates every field against {@link net.authsuite.common.skin.SkinPolicy}.
     */
    public void broadcast(ServerPlayer player, HybridIdentity identity) {
        SkinDirective directive = directives.get(player.getUUID());
        if (directive == null) {
            directive = buildFromIdentity(identity);
            if (directive == null) {
                return;
            }
            directives.put(player.getUUID(), directive);
        }
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && !other.getUUID().equals(player.getUUID())) {
                other.connection.send(PlayerSkinDirectivePayload.of(directive));
            }
        }
        for (Map.Entry<UUID, SkinDirective> entry : directives.entrySet()) {
            if (!entry.getKey().equals(player.getUUID())) {
                player.connection.send(PlayerSkinDirectivePayload.of(entry.getValue()));
            }
        }
        player.connection.send(PlayerSkinDirectivePayload.of(directive));
        log.debug("Bidirectionally synced skin directives on {} join (revision {})",
                player.getGameProfile().getName(), directive.revision());
    }

    public void remove(UUID canonicalUuid) {
        directives.remove(canonicalUuid);
    }

    private SkinDirective buildFromIdentity(HybridIdentity identity) {
        // Default directive: no skin/cape resources. External providers usually seed
        // a richer directive at login via LoginProfileBuilder.buildDirective.
        return new SkinDirective(identity.minecraftUUID(), identity.providerId(),
                null, null, "classic", 0);
    }

    private SkinResource safe(SkinResource resource) {
        return resource;
    }
}