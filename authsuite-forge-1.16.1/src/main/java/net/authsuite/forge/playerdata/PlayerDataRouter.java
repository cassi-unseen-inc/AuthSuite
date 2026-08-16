package net.authsuite.forge.playerdata;

import com.mojang.datafixers.DataFixer;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.PlayerData;
import net.minecraft.world.storage.SaveFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes player data to per-provider storage (spec §11). Microsoft identities use
 * the vanilla world {@code playerdata} untouched; external provider identities are
 * isolated under {@code /config/<provider>/playerdata/}.
 */
public final class PlayerDataRouter {

    public static final String PROVIDER_VANILLA = "microsoft";

    private final AuthSuiteConfig config;
    private final MinecraftServer server;
    private final IdentityRegistry identityRegistry;
    private final AuthSuiteLogger log;
    private final Map<String, PlayerData> storages = new ConcurrentHashMap<>();

    public PlayerDataRouter(AuthSuiteConfig config, MinecraftServer server, IdentityRegistry identityRegistry, AuthSuiteLogger log) {
        this.config = config;
        this.server = server;
        this.identityRegistry = identityRegistry;
        this.log = log;
    }

    public String providerOf(PlayerEntity player) {
        if (player == null || player.getUUID() == null) {
            return PROVIDER_VANILLA;
        }
        return identityRegistry.byUuid(player.getUUID())
                .map(reg -> reg.identity().providerId())
                .orElse(PROVIDER_VANILLA);
    }

    public Optional<CompoundNBT> load(PlayerEntity player) {
        String providerId = providerOf(player);
        if (PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            // Not routed: the vanilla path (singleplayer-owner special case) is
            // handled inside PlayerList and must not be re-entered here.
            return Optional.empty();
        }
        return Optional.ofNullable(storageFor(providerId).load(player));
    }

    public void save(PlayerEntity player) {
        String providerId = providerOf(player);
        if (PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            return;
        }
        storageFor(providerId).save(player);
    }

    public PlayerData storageFor(String providerId) {
        return storages.computeIfAbsent(providerId.toLowerCase(Locale.ROOT), id -> {
            Path base = config.baseConfigDir() != null ? config.baseConfigDir() : java.nio.file.Paths.get("config", "authsuite");
            Path dir = base.resolve(id);
            try {
                SaveFormat.LevelSave access =
                        SaveFormat.createDefault(dir).createAccess("playerdata");
                return access.createPlayerStorage();
            } catch (IOException e) {
                log.warn("Failed to init playerdata storage for provider {}: {}", id, e.getMessage());
                // Fall back to a scratch storage under the same directory.
                try {
                    SaveFormat.LevelSave access =
                            SaveFormat.createDefault(dir).createAccess("fallback");
                    return access.createPlayerStorage();
                } catch (IOException ex) {
                    throw new IllegalStateException("Unable to create playerdata storage for " + id, ex);
                }
            }
        });
    }

    public void saveAll() {
        // PlayerData has no batch save; individual saves happen on logout.
        log.debug("PlayerDataRouter: {} provider stores active", storages.size());
    }
}