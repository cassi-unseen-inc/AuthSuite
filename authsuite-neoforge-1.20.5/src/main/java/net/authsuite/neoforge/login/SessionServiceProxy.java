package net.authsuite.neoforge.login;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Intercepts the server's {@link MinecraftSessionService} and routes
 * <em>hasJoinedServer</em> through the {@link AuthResolver}, preserving the native
 * Authlib key exchange (encryption, challenge) while replacing only the identity
 * verification step.
 * <p>
 * The rest of the session-service surface (joinServer, fetchProfile, textures)
 * is passed through to the original implementation unchanged.
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
        if ("getServicesKey".equals(name)) {
            return Optional.empty();
        }
        if ("fillProfileProperties".equals(name)) {
            return null;
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
     * Called on the "User Authenticator" thread spawned by
     * {@code ServerLoginPacketListenerImpl#handleKey}. Runs the AuthResolver chain
     * with a bounded timeout and builds a canonical {@code GameProfile}.
     */
    private Object handleHasJoinedServer(Object[] args) throws Exception {
        String username = (String) args[0];
        String serverId = (String) args[1];
        InetAddress address = extractAddress(args);
        log.debug("hasJoined intercepted for '{}'", username);

        long timeout = Math.max(1_000L, config.authTimeoutMs());
        AuthResolver.Resolution resolution = profileBuilder.resolveBlocking(username, serverId, address, timeout);
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