package net.authsuite.forge.ops;

import com.mojang.authlib.GameProfile;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.minecraft.network.play.server.SEntityStatusPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.OpEntry;
import net.minecraft.server.management.OpList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes op operations and op-level reads to the correct store.
 * <p>
 * Microsoft (MA) identities keep the vanilla {@code ops.json} untouched; external
 * provider identities are isolated in {@code /config/<provider>/ops.json} keyed by
 * their canonical UUID (spec §11).
 */
public final class OpsRouter {

    public static final String PROVIDER_VANILLA = "microsoft";

    private final AuthSuiteConfig config;
    private final MinecraftServer server;
    private final IdentityRegistry identityRegistry;
    private final AuthSuiteLogger log;
    private final Map<String, OpList> providerOps = new ConcurrentHashMap<>();

    public OpsRouter(AuthSuiteConfig config, MinecraftServer server, IdentityRegistry identityRegistry, AuthSuiteLogger log) {
        this.config = config;
        this.server = server;
        this.identityRegistry = identityRegistry;
        this.log = log;
    }

    /** Provider id of the profile's canonical identity, or {@link #PROVIDER_VANILLA}. */
    public String providerOf(GameProfile profile) {
        if (profile == null || profile.getId() == null) {
            return PROVIDER_VANILLA;
        }
        return identityRegistry.byUuid(profile.getId())
                .map(reg -> reg.identity().providerId())
                .orElse(PROVIDER_VANILLA);
    }

    // ---- writes ----

    /** Persist an op entry, routing to the vanilla list (MA) or a provider list. */
    public void op(GameProfile profile, int level, boolean bypassesPlayerLimit) {
        opFor(providerOf(profile), profile, level, bypassesPlayerLimit);
    }

    public void opFor(String providerId, GameProfile profile, int level, boolean bypassesPlayerLimit) {
        if (PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            server.getPlayerList().op(profile);
            return;
        }
        OpList list = listFor(providerId);
        list.add(new OpEntry(profile, level, bypassesPlayerLimit));
        log.info("Granted level {} operator {} in provider {}", level, profile.getName(), providerId);
        refreshPermissions(profile);
    }

    public void deop(GameProfile profile) {
        deopFor(providerOf(profile), profile);
    }

    public void deopFor(String providerId, GameProfile profile) {
        if (PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            server.getPlayerList().deop(profile);
            return;
        }
        OpList list = listFor(providerId);
        list.remove(profile);
        log.info("Removed operator {} from provider {}", profile.getName(), providerId);
        refreshPermissions(profile);
    }

    // ---- reads ----

    /** Effective op level for a profile (0..4); vanilla path honored for MA. */
    public int effectiveLevel(GameProfile profile) {
        String providerId = providerOf(profile);
        if (!PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            OpEntry entry = listFor(providerId).get(profile);
            return entry == null ? 0 : entry.getLevel();
        }
        // MA (or unknown) -> vanilla semantics (singleplayer owner, allow-commands, etc.).
        return server.getProfilePermissions(profile);
    }

    public boolean isOp(GameProfile profile) {
        String providerId = providerOf(profile);
        if (!PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            return listFor(providerId).get(profile) != null;
        }
        return server.getPlayerList().isOp(profile);
    }

    public String[] opNames() {
        return server.getPlayerList().getOpNames();
    }

    // ---- stores ----

    public OpList listFor(String providerId) {
        return providerOps.computeIfAbsent(providerId.toLowerCase(Locale.ROOT), id -> {
            Path base = config.baseConfigDir() != null ? config.baseConfigDir() : java.nio.file.Paths.get("config", "authsuite");
            File file = base.resolve(id).resolve("ops.json").toFile();
            OpList list = new OpList(file);
            try {
                if (file.isFile()) {
                    list.load();
                }
            } catch (IOException e) {
                log.warn("Failed to load ops.json for provider {}: {}", id, e.getMessage());
            }
            return list;
        });
    }

    public void saveAll(AuthSuiteLogger log) {
        for (Map.Entry<String, OpList> e : providerOps.entrySet()) {
            try {
                e.getValue().save();
            } catch (IOException ex) {
                log.warn("Failed to save ops.json for provider {}: {}", e.getKey(), ex.getMessage());
            }
        }
    }

    private void refreshPermissions(GameProfile profile) {
        Optional.ofNullable(server.getPlayerList().getPlayer(profile.getId())).ifPresent(player -> {
            int level = Math.max(0, Math.min(effectiveLevel(profile), 4));
            player.connection.send(new SEntityStatusPacket(player, (byte) (24 + level)));
            server.getCommands().sendCommands(player);
        });
    }
}