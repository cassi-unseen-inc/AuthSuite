package net.authsuite.common.login;

import net.authsuite.common.log.AuthSuiteLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-connection store of {@link LoginAttempt}s (post-audit §5).
 * <p>
 * Attempts are keyed by the platform login listener instance (the per-connection
 * scope), never by username. The {@code InheritableThreadLocal} bridges the
 * preference from the netty thread that receives it to the "User Authenticator"
 * thread that runs {@code hasJoinedServer}: a mixin pushes the attempt right
 * before the authenticator {@code Thread} is constructed and clears it right
 * after construction, so the child thread inherits the value while the netty
 * thread never leaks it to unrelated connections.
 */
public final class LoginAttemptStore {

    private static final Map<Object, LoginAttempt> BY_CONNECTION = new ConcurrentHashMap<>();
    private static final ThreadLocal<LoginAttempt> CURRENT = new InheritableThreadLocal<>();
    private static final AuthSuiteLogger LOG = AuthSuiteLogger.noop();

    private LoginAttemptStore() {
    }

    /** Bind an attempt to its login listener (netty thread, on preference receipt). */
    public static void bind(Object connection, LoginAttempt attempt) {
        if (connection == null || attempt == null) {
            return;
        }
        BY_CONNECTION.put(connection, attempt);
    }

    /** The attempt bound to a login listener, if any. */
    public static LoginAttempt forConnection(Object connection) {
        return connection == null ? null : BY_CONNECTION.get(connection);
    }

    /**
     * Remove and return the attempt bound to a login listener. Compare-and-remove:
     * only removes the entry for that exact listener, never another connection's.
     */
    public static LoginAttempt remove(Object connection) {
        return connection == null ? null : BY_CONNECTION.remove(connection);
    }

    /** Push the attempt into the inheritable thread-local right before the authenticator thread is constructed. */
    public static void push(LoginAttempt attempt) {
        CURRENT.set(attempt);
    }

    /** Clear the thread-local after the authenticator thread has been constructed. */
    public static void clearPushed() {
        CURRENT.remove();
    }

    /** The attempt inherited by the authenticator thread (netty thread's push), or null. */
    public static LoginAttempt current() {
        return CURRENT.get();
    }

    /** Terminal-state bookkeeping: mark, unbind from its connection, and clear the thread-local. */
    public static void finish(LoginAttempt attempt, LoginAttempt.State terminalState) {
        if (attempt != null) {
            attempt.setState(terminalState);
            remove(attempt.connection());
        }
        CURRENT.remove();
    }

    public static void wireLogger(AuthSuiteLogger log) {
        // Retained for future diagnostics; LOG is intentionally no-op today.
    }
}