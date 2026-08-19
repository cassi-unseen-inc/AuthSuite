package net.authsuite.common.login;

import net.authsuite.common.provider.AuthResolver;

import java.util.Objects;

/**
 * Provider preference and lifecycle state belonging to ONE login attempt /
 * connection (post-audit §5).
 * <p>
 * Two simultaneous connections using the same username NEVER share an attempt;
 * each attempt owns its preference, and a terminal transition on one attempt can
 * never clear another attempt's preference.
 */
public final class LoginAttempt {

    public enum State {
        /** Hello received, awaiting the client's provider preference. */
        PENDING_PREFERENCE,
        /** Provider resolution in progress (authenticator thread). */
        AUTHENTICATING,
        /** Authenticated and about to join. */
        SUCCESS,
        /** Authentication failed. */
        FAILED,
        /** The connection was dropped before a terminal verdict. */
        DISCONNECTED,
        /** Provider resolution exceeded the timeout. */
        TIMED_OUT
    }

    /** The platform login listener/connection owning this attempt (never used as an identity key). */
    private final Object connection;
    /** The claimed hello username (attacker-controlled; never an identity key). */
    private final String username;
    private volatile AuthResolver.PreferenceHint preference;
    private volatile State state;

    public LoginAttempt(Object connection, String username) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.username = username;
        this.state = State.PENDING_PREFERENCE;
    }

    public Object connection() {
        return connection;
    }

    public String username() {
        return username;
    }

    public AuthResolver.PreferenceHint preference() {
        return preference;
    }

    public void setPreference(AuthResolver.PreferenceHint preference) {
        this.preference = preference;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }
}