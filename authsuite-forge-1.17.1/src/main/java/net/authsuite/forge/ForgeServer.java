package net.authsuite.forge;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.config.ConfigLoader;
import net.authsuite.common.config.ProviderConfig;
import net.authsuite.common.config.ShortcodeRegistry;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthlibProvider;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderHttpClient;
import net.authsuite.common.provider.ProviderId;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.forge.api.ForgeAuthSuiteAPI;
import net.authsuite.forge.command.AuthSuiteCommands;
import net.authsuite.forge.command.OpCommandInterceptor;
import net.authsuite.forge.login.SessionServiceProxy;
import net.authsuite.forge.network.ForgeNetwork;
import net.authsuite.forge.ops.OpsRouter;
import net.authsuite.forge.perm.ForgePermissionService;
import net.authsuite.forge.playerdata.PlayerDataRouter;
import net.authsuite.forge.skin.SkinBroadcaster;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import net.minecraftforge.fmlserverevents.FMLServerStartingEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Forge platform bootstrap.
 * <p>
 * Owns the server lifecycle and the server-scoped AuthSuite context. The login
 * handler is wired by replacing the server's {@code MinecraftSessionService} with a
 * {@link SessionServiceProxy}; this preserves the native Authlib key exchange and
 * online encryption exactly while moving <em>only</em> the hasJoined verification
 * behind the {@link AuthResolver}.
 */
public final class ForgeServer {

    private static final Object LOCK = new Object();
    private static volatile ForgeServer instance;

    private final AuthSuiteLogger log;
    private final ConfigLoader configLoader;
    private final ProviderManager providerManager;
    private final ShortcodeRegistry shortcodes;
    private final IdentityRegistry identityRegistry;
    private final ForgePermissionService permissionService;
    private final AuthResolver resolver;
    private final ProviderHttpClient httpClient;
    private final AuthSuiteConfig config;
    private final ConcurrentMap<String, AuthResolver.PreferenceHint> pendingPreferences = new ConcurrentHashMap<>();

    private ForgeAuthSuiteAPI api;
    private OpsRouter opsRouter;
    private PlayerDataRouter playerDataRouter;
    private SkinBroadcaster skinBroadcaster;
    private volatile net.minecraft.server.MinecraftServer server;
    private SessionServiceProxy sessionServiceProxy;

    private ForgeServer(AuthSuiteLogger log) {
        this.log = log;
        this.configLoader = new ConfigLoader(log);
        this.shortcodes = new ShortcodeRegistry(log);
        this.identityRegistry = new IdentityRegistry(log);
        this.httpClient = new ProviderHttpClient(log);

        Path configDir = FMLPaths.CONFIGDIR.get();
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
        this.permissionService = new ForgePermissionService(identityRegistry, providerManager, config, log);
    }

    public static void init(IEventBus modEventBus, AuthSuiteLogger log) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new ForgeServer(log);
            }
        }
        MinecraftForge.EVENT_BUS.addListener(instance::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(instance::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(instance::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(instance::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(instance::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(instance::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(instance::onServerStopped);
        ForgeNetwork.registerClientHandlers(modEventBus);
    }

    public static ForgeServer get() {
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

    private void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        this.server = event.getServer();

        if (config.enforceOnlineMode() && !server.usesAuthentication()) {
            log.error("online-mode is disabled. AuthSuite strictly requires online-mode=true to preserve "
                    + "native online encryption and identity verification. Refusing to start.");
            throw new IllegalStateException(
                    "AuthSuite requires online-mode=true (set server.properties online-mode=true).");
        }
        if (!config.onlineMode() && config.enforceOnlineMode()) {
            log.error("AuthSuite config 'online-mode' is false but 'enforce-online-mode' is true. Refusing to start.");
            throw new IllegalStateException("AuthSuite requires online-mode=true in config.yaml.");
        }

        this.opsRouter = new OpsRouter(config, server, identityRegistry, log);
        this.playerDataRouter = new PlayerDataRouter(config, server, identityRegistry, log);
        this.api = new ForgeAuthSuiteAPI(identityRegistry, providerManager, permissionService, opsRouter, log);
        this.skinBroadcaster = new SkinBroadcaster(opsRouter, identityRegistry, log);

        wrapSessionService(server);
        if (sessionServiceProxy != null) {
            sessionServiceProxy.profileBuilder().setSkinBroadcaster(skinBroadcaster);
        }
        log.info("AuthSuite server context ready");
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        // Commands are registered via RegisterCommandsEvent.
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        OpCommandInterceptor.register(event.getDispatcher());
        AuthSuiteCommands.register(event.getDispatcher(), this);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            UUID canonical = player.getUUID();
            identityRegistry.byUuid(canonical).ifPresent(reg -> {
                log.info("Player {} logged in as {}", player.getGameProfile().getName(), reg.identity().logLabel());
                skinBroadcaster.broadcast(player, reg.identity());
            });
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            identityRegistry.byUuid(player.getUUID()).ifPresent(reg ->
                    providerManager.byId(reg.identity().providerId())
                            .ifPresent(p -> p.disconnect(reg.identity().providerAccountId())));
            identityRegistry.release(player.getUUID());
        }
    }

    private void onServerStopping(FMLServerStoppingEvent event) {
        if (opsRouter != null) {
            opsRouter.saveAll(log);
        }
        if (playerDataRouter != null) {
            playerDataRouter.saveAll();
        }
    }

    private void onServerStopped(FMLServerStoppedEvent event) {
        this.server = null;
    }

    // ---- session service wrapping ----

    private void wrapSessionService(net.minecraft.server.MinecraftServer server) {
        try {
            Field sessionField = null;
            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (MinecraftSessionService.class.isAssignableFrom(f.getType())) {
                    sessionField = f;
                    break;
                }
            }
            if (sessionField == null) {
                log.warn("Could not locate the server's session service field; login interception disabled.");
                return;
            }
            MinecraftSessionService original = (MinecraftSessionService) sessionField.get(server);
            this.sessionServiceProxy = new SessionServiceProxy(resolver, identityRegistry, config, providerManager, log);
            setFieldForce(server, sessionField, sessionServiceProxy.wrap(original));
            log.info("Wrapped server session service for AuthSuite provider resolution");
        } catch (Exception e) {
            log.error("Failed to wrap the server session service: " + e.getMessage(), e);
        }
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

    public ForgePermissionService permissions() {
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

    public ForgeAuthSuiteAPI api() {
        return api;
    }

    public SkinBroadcaster skinBroadcaster() {
        return skinBroadcaster;
    }

    public AuthSuiteLogger log() {
        return log;
    }

    public net.minecraft.server.MinecraftServer minecraftServer() {
        return server;
    }

    public void recordPreference(String key, AuthResolver.PreferenceHint preference) {
        if (preference == null) {
            pendingPreferences.remove(key);
        } else {
            pendingPreferences.put(key, preference);
        }
    }

    public AuthResolver.PreferenceHint pendingPreference(String key) {
        return pendingPreferences.get(key);
    }

    public Map<String, AuthResolver.PreferenceHint> pendingPreferences() {
        return pendingPreferences;
    }
}