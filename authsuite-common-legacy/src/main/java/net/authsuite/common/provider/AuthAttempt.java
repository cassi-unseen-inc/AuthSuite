package net.authsuite.common.provider;

import java.net.InetAddress;
import java.util.Objects;

/**
 * A single attempt to authenticate a connecting player against one provider.
 * <p>
 * <strong>Trust model:</strong> everything here arrives from the network and is
 * therefore attacker-controlled. The {@code username} is the claimed player name;
 * the {@code serverId} is the session hash computed by the native Authlib key
 * exchange. An {@code AuthProvider} MUST validate independently and MUST NOT trust
 * the username to prove account ownership.
 */
public final class AuthAttempt {

    private final String username;
    private final String serverId;
    private final InetAddress clientAddress;
    private final boolean explicitTarget;

    public AuthAttempt(String username, String serverId, InetAddress clientAddress, boolean explicitTarget) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(serverId, "serverId");
        this.username = username;
        this.serverId = serverId;
        this.clientAddress = clientAddress;
        this.explicitTarget = explicitTarget;
    }

    public String username() {
        return username;
    }

    public String serverId() {
        return serverId;
    }

    public InetAddress clientAddress() {
        return clientAddress;
    }

    public boolean explicitTarget() {
        return explicitTarget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthAttempt)) {
            return false;
        }
        AuthAttempt that = (AuthAttempt) o;
        return explicitTarget == that.explicitTarget
                && username.equals(that.username)
                && serverId.equals(that.serverId)
                && Objects.equals(clientAddress, that.clientAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, serverId, clientAddress, explicitTarget);
    }

    @Override
    public String toString() {
        return "auth(user=" + username + ")";
    }
}