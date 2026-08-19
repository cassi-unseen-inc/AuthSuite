package net.authsuite.fabric.server;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.config.ConfigLoader;
import net.authsuite.common.config.ProviderConfig;
import net.authsuite.common.config.ShortcodeRegistry;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.identity.IdentityResolver;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthlibProvider;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderHttpClient;
import net.authsuite.common.provider.ProviderId;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.fabric.api.FabricAuthSuiteAPI;
import net.authsuite.fabric.command.AuthSuiteCommands;
import net.authsuite.fabric.command.OpCommands;
import net.authsuite.fabric.login.SessionServiceProxy;
import net.authsuite.fabric.network.FabricNetwork;
import net.authsuite.fabric.ops.OpsRouter;
import net.authsuite.fabric.perm.FabricPermissionService;
import net.authsuite.fabric.playerdata.PlayerDataRouter;
import net.authsuite.fabric.skin.SkinBroadcaster;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Fabric platform bootstrap.
 * <p>
 * Mirrors the NeoForge server context: owns the server lifecycle and the
 * server-scoped AuthSuite wiring. Login interception replaces the <em>Services</em>
 * session service with a {@link SessionServiceProxy} (same reflection technique).
 */
public final class FabricServer {

    private static final Object LOCK = new Object();
    private static volatile FabricServer instance;

    private final AuthSuiteLogger log;
    private final ConfigLoader configLoader;
    private final ProviderManager providerManager;
    private final ShortcodeRegistry shortcodes;
    private final IdentityRegistry identityRegistry;
    private final FabricPermissionService permissionService;
    private final AuthResolver resolver;
    private final ProviderHttpClient httpClient;
    private final AuthSuiteConfig config;
    private final IdentityResolver identityResolver;

    private FabricAuthSuiteAPI api;
    private OpsRouter opsRouter;
    private PlayerDataRouter playerDataRouter;
    private SkinBroadcaster skinBroadcaster;
    private volatile MinecraftServer server;
    private SessionServiceProxy sessionServiceProxy;

    private FabricServer(AuthSuiteLogger log) {
        this.log = log;
        this.configLoader = new ConfigLoader(log);
        this.shortcodes = new ShortcodeRegistry(log);
        this.identityRegistry = new IdentityRegistry(log);
        this.httpClient = new ProviderHttpClient(log);

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("authsuite").resolve("config.yaml");
        this.config = configLoader.load(configFile);
        try {
            configLoader.writeDefaultsIfAbsent(configFile);
        } catch (IOException e) {
            log.error("Failed to write default config: " + e.getMessage(), e);
        }
        this.providerManager = new ProviderManager(shortcodes, log);
        wireProviders();
        this.resolver = new AuthResolver(providerManager, config, log);
        this.permissionService = new FabricPermissionService(identityRegistry, providerManager, config, log);
        this.identityResolver = new IdentityResolver(identityRegistry, providerManager, log);
    }

    public static void init(AuthSuiteLogger log) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new FabricServer(log);
            }
        }
        ServerLifecycleEvents.SERVER_STARTING.register(instance::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(instance::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(instance::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(instance::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> instance.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> instance.onPlayerLeave(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            OpCommands.register(dispatcher, instance);
            AuthSuiteCommands.register(dispatcher, instance);
        });
        FabricNetwork.init();
        log.info("AuthSuite Fabric initialized");
    }

    public static FabricServer get() {
        return instance;
    }

    private void wireProviders() {
        for (ProviderConfig providerConfig : config.providers()) {
            if (!providerConfig.enabled()) {
                continue;
            }
            try {
                providerConfig.validate();
            } catch (NullPointerException e) {
                this.log.warn("Skipping invalid provider '{}': " + e.getMessage(), providerConfig.id());
                continue;
            }
            AuthlibProvider provider = new AuthlibProvider(providerConfig, httpClient, log);
            ProviderId pid = new ProviderId(providerConfig.id(), providerConfig.shortcode(), providerConfig.domain(), providerConfig.id());
            shortcodes.register(pid);
            providerManager.register(provider, providerConfig.priority());
            log.info("Registered provider '{}' ({})", providerConfig.id(), providerConfig.shortcode());
        }
        providerManager.setPriorityChain(new ArrayList<>(config.priority()));
    }

    // ---- lifecycle events ----

    private void onServerStarting(MinecraftServer server) {
        this.server = server;

        if (config.enforceOnlineMode() && !server.usesAuthentication()) {
            log.error("online-mode is disabled. AuthSuite strictly requires online-mode=true.");
            throw new IllegalStateException(
                    "AuthSuite requires online-mode=true (set server.properties online-mode=true).");
        }
        if (!config.onlineMode() && config.enforceOnlineMode()) {
            log.error("AuthSuite config 'online-mode' is false but 'enforce-online-mode' is true. Refusing to start.");
            throw new IllegalStateException("AuthSuite requires online-mode=true in config.yaml.");
        }

        this.opsRouter = new OpsRouter(config, server, identityRegistry, log);
        this.playerDataRouter = new PlayerDataRouter(config, server, identityRegistry, log);
        this.api = new FabricAuthSuiteAPI(identityRegistry, providerManager, permissionService, opsRouter, log);
        this.skinBroadcaster = new SkinBroadcaster(opsRouter, identityRegistry, log);

        wrapSessionService(server);
        if (sessionServiceProxy != null) {
            sessionServiceProxy.profileBuilder().setSkinBroadcaster(skinBroadcaster);
        }
        log.info("AuthSuite server context ready");
    }

    private void onServerStarted(MinecraftServer server) {
    }

    private void onServerStopping(MinecraftServer server) {
        if (opsRouter != null) {
            opsRouter.saveAll(log);
        }
        if (playerDataRouter != null) {
            playerDataRouter.saveAll();
        }
    }

    private void onServerStopped(MinecraftServer server) {
        this.server = null;
    }

    private void onPlayerJoin(ServerPlayer player) {
        UUID canonical = player.getUUID();
        identityRegistry.byUuid(canonical).ifPresent(reg -> {
            log.info("Player {} logged in as {}", player.getGameProfile().getName(), reg.identity().logLabel());
            skinBroadcaster.broadcast(player, reg.identity());
        });
    }

    private void onPlayerLeave(ServerPlayer player) {
        identityRegistry.byUuid(player.getUUID()).ifPresent(reg ->
                providerManager.byId(reg.identity().providerId())
                        .ifPresent(p -> p.disconnect(reg.identity().providerAccountId())));
        identityRegistry.release(player.getUUID());
    }

    // ---- session service wrapping (identical reflection technique) ----

    private void wrapSessionService(MinecraftServer server) {
        try {
            Field servicesField = null;
            for (Field f : MinecraftServer.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object value = f.get(server);
                if (value != null && value.getClass().getName().equals("net.minecraft.server.Services")) {
                    servicesField = f;
                    break;
                }
            }
            if (servicesField == null) {
                log.warn("Could not locate the server's Services record; login interception disabled.");
                return;
            }
            Object original = servicesField.get(server);
            Object wrapped = rebuildServices(original);
            setFieldForce(server, servicesField, wrapped);
            log.info("Wrapped server session service for AuthSuite provider resolution");
        } catch (Exception e) {
            log.error("Failed to wrap the server session service: " + e.getMessage(), e);
        }
    }

    private Object rebuildServices(Object original) throws Exception {
        RecordComponent[] components = original.getClass().getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            Object value = components[i].getAccessor().invoke(original);
            if (value instanceof MinecraftSessionService sessionService) {
                this.sessionServiceProxy = new SessionServiceProxy(resolver, identityRegistry, config, providerManager, log);
                args[i] = sessionServiceProxy.wrap(sessionService);
            } else if (value instanceof ServicesKeySet) {
                args[i] = ServicesKeySet.EMPTY;
            } else {
                args[i] = value;
            }
        }
        Constructor<?> ctor = original.getClass().getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static void setFieldForce(Object target, Field field, Object value) throws Exception {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            if ((field.getModifiers() & java.lang.reflect.Modifier.FINAL) != 0) {
                sun.misc.Unsafe unsafe = getUnsafe();
                long offset = unsafe.objectFieldOffset(field);
                unsafe.putObject(target, offset, value);
            } else {
                throw e;
            }
        }
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    // ---- accessors ----

    public AuthResolver resolver() {
        return resolver;
    }

    public ProviderManager providerManager() {
        return providerManager;
    }

    public IdentityRegistry identityRegistry() {
        return identityRegistry;
    }

    public FabricPermissionService permissions() {
        return permissionService;
    }

    public AuthSuiteConfig config() {
        return config;
    }

    public OpsRouter opsRouter() {
        return opsRouter;
    }

    public PlayerDataRouter playerDataRouter() {
        return playerDataRouter;
    }

    public FabricAuthSuiteAPI api() {
        return api;
    }

    public SkinBroadcaster skinBroadcaster() {
        return skinBroadcaster;
    }

    public AuthSuiteLogger log() {
        return log;
    }

    public MinecraftServer minecraftServer() {
        return server;
    }

    public IdentityResolver identityResolver() {
        return identityResolver;
    }
}