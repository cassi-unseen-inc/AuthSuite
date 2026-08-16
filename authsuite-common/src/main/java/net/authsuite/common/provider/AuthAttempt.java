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
public record AuthAttempt(
        String username,
        String serverId,
        InetAddress clientAddress,
        boolean explicitTarget) {

    public AuthAttempt {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(serverId, "serverId");
    }

    @Override
    public String toString() {
        return "auth(user=" + username + ")";
    }
}