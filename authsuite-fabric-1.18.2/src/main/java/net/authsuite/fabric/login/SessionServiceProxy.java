package net.authsuite.fabric.login;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.fabric.server.FabricServer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Intercepts the server's {@link MinecraftSessionService} and routes
 * <em>hasJoinedServer</em> through the {@link AuthResolver} (spec §2). Preserves
 * the native Authlib key exchange; only identity verification is replaced.
 */
public final class SessionServiceProxy implements InvocationHandler {

    private final AuthResolver resolver;
    private final IdentityRegistry identityRegistry;
    private final AuthSuiteConfig config;
    private final ProviderManager providerManager;
    private final AuthSuiteLogger log;
    private final LoginProfileBuilder profileBuilder;

    private MinecraftSessionService original;

    public SessionServiceProxy(
            AuthResolver resolver,
            IdentityRegistry identityRegistry,
            AuthSuiteConfig config,
            ProviderManager providerManager,
            AuthSuiteLogger log) {
        this.resolver = resolver;
        this.identityRegistry = identityRegistry;
        this.config = config;
        this.providerManager = providerManager;
        this.log = log;
        this.profileBuilder = new LoginProfileBuilder(identityRegistry, providerManager, resolver, log);
    }

    private AuthResolver.PreferenceHint pendingPreference(String username) {
        FabricServer server = FabricServer.get();
        return server != null ? server.pendingPreference(username) : null;
    }

    public MinecraftSessionService wrap(MinecraftSessionService original) {
        if (original == null || Proxy.isProxyClass(original.getClass())) {
            return original;
        }
        this.original = original;
        return (MinecraftSessionService) Proxy.newProxyInstance(
                MinecraftSessionService.class.getClassLoader(),
                new Class<?>[]{MinecraftSessionService.class},
                this);
    }

    public LoginProfileBuilder profileBuilder() {
        return profileBuilder;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(method, args);
        }
        String name = method.getName();
        if ("hasJoinedServer".equals(name)) {
            return handleHasJoinedServer(args);
        }
        return method.invoke(original, args);
    }

    private Object handleObjectMethod(Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxyEquals(args);
            case "hashCode" -> System.identityHashCode(this);
            case "toString" -> "SessionServiceProxy";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private boolean proxyEquals(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return false;
        }
        return Proxy.isProxyClass(args[0].getClass())
                && Proxy.getInvocationHandler(args[0]) == this;
    }

    /**
     * Called on the "User Authenticator" thread spawned by the login listener.
     * Runs the AuthResolver chain with a bounded timeout and builds a canonical
     * {@code GameProfile}.
     * <p>
     * Authlib 4.0.43 (1.20.1) signature:
     * {@code GameProfile hasJoinedServer(GameProfile profile, String serverId, InetAddress address)}.
     */
    private Object handleHasJoinedServer(Object[] args) throws Exception {
        if (args == null || args.length < 2 || !(args[0] instanceof com.mojang.authlib.GameProfile profile)) {
            log.warn("Unexpected hasJoinedServer signature; login interception skipped");
            return null;
        }
        String username = profile.getName();
        String serverId = (String) args[1];
        InetAddress address = extractAddress(args);
        log.debug("hasJoined intercepted for '{}'", username);

        long timeout = Math.max(1_000L, config.authTimeoutMs());
        AuthResolver.Resolution resolution = profileBuilder.resolveBlocking(username, serverId, address, timeout,
                pendingPreference(username));
        if (resolution == null || resolution.profile() == null) {
            log.info("Login rejected for '{}': no provider validated the session", username);
            return null;
        }
        return profileBuilder.buildProfileResult(resolution, username, serverId);
    }

    private InetAddress extractAddress(Object[] args) {
        if (args == null || args.length < 3 || args[2] == null) {
            return null;
        }
        if (args[2] instanceof InetAddress inetAddress) {
            return inetAddress;
        }
        if (args[2] instanceof InetSocketAddress socketAddress && socketAddress.getAddress() != null) {
            return socketAddress.getAddress();
        }
        return null;
    }
}