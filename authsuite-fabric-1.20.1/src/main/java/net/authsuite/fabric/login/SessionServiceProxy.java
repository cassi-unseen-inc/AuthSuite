package net.authsuite.fabric.login;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.login.LoginAttempt;
import net.authsuite.common.login.LoginAttemptStore;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderManager;

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

    /**
     * The preference for THIS connection's login attempt, inherited by the
     * authenticator thread via {@link LoginAttemptStore}. Never keyed by username.
     */
    private AuthResolver.PreferenceHint currentPreference() {
        LoginAttempt attempt = LoginAttemptStore.current();
        return attempt == null ? null : attempt.preference();
    }

    private void finishAttempt(LoginAttempt.State state) {
        LoginAttempt attempt = LoginAttemptStore.current();
        if (attempt != null) {
            LoginAttemptStore.finish(attempt, state);
        } else {
            LoginAttemptStore.clearPushed();
        }
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
        try {
            AuthResolver.Resolution resolution = profileBuilder.resolveBlocking(username, serverId, address, timeout,
                    currentPreference());
            if (resolution == null || resolution.profile() == null) {
                log.info("Login rejected for '{}': no provider validated the session", username);
                finishAttempt(LoginAttempt.State.FAILED);
                return null;
            }
            finishAttempt(LoginAttempt.State.SUCCESS);
            return profileBuilder.buildProfileResult(resolution, username, serverId);
        } catch (Exception e) {
            finishAttempt(LoginAttempt.State.FAILED);
            throw e;
        }
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