package net.authsuite.common.identity;

import net.authsuite.common.log.AuthSuiteLogger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side identity registry. Binds a validated {@link ProviderIdentity} to a
 * canonical Minecraft identity.
 * <p>
 * Registration requires a validated {@link HybridIdentity}; authorization requires
 * a registered authenticated identity (spec §12). At most one active game session
 * may exist per canonical identity unless a future policy states otherwise.
 */
public final class IdentityRegistry {

    private final AuthSuiteLogger log;
    private final Map<UUID, RegisteredIdentity> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byProviderKey = new ConcurrentHashMap<>();
    private final Map<String, UUID> byUsername = new ConcurrentHashMap<>();

    public IdentityRegistry(AuthSuiteLogger log) {
        this.log = log;
    }

    public static final class RegisteredIdentity {
        private final HybridIdentity identity;
        private final String sessionId;
        private final long joinedAt;

        public RegisteredIdentity(HybridIdentity identity, String sessionId, long joinedAt) {
            this.identity = identity;
            this.sessionId = sessionId;
            this.joinedAt = joinedAt;
        }

        public HybridIdentity identity() {
            return identity;
        }

        public String sessionId() {
            return sessionId;
        }

        public long joinedAt() {
            return joinedAt;
        }
    }

    public Optional<RegisteredIdentity> byUuid(UUID minecraftUuid) {
        return Optional.ofNullable(byUuid.get(minecraftUuid));
    }

    public Optional<RegisteredIdentity> byProviderKey(String providerId, String providerAccountId) {
        UUID uuid = byProviderKey.get(providerId + "\u0000" + providerAccountId);
        return uuid == null ? Optional.empty() : byUuid(uuid);
    }

    /** Lookup by in-game name (case-insensitive); used by admin op/link commands. */
    public Optional<RegisteredIdentity> byUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        UUID uuid = byUsername.get(username.toLowerCase(java.util.Locale.ROOT));
        return uuid == null ? Optional.empty() : byUuid(uuid);
    }

    /**
     * Registers a valid identity for a session. Rejects when there is already an
     * active (not yet released) session for the same canonical identity.
     */
    public boolean register(HybridIdentity identity, String sessionId, boolean releaseExisting) {
        UUID canonical = identity.minecraftUUID();
        RegisteredIdentity existing = byUuid.get(canonical);
        if (existing != null) {
            if (!releaseExisting) {
                log.info("Refusing duplicate session for canonical identity {} (session already active)", canonical);
                return false;
            }
            byUuid.remove(canonical);
            byProviderKey.remove(existing.identity().providerId() + "\u0000" + existing.identity().providerAccountId());
        }
        RegisteredIdentity registered = new RegisteredIdentity(identity, sessionId, System.currentTimeMillis());
        byUuid.put(canonical, registered);
        byProviderKey.put(identity.providerId() + "\u0000" + identity.providerAccountId(), canonical);
        byUsername.put(identity.username().toLowerCase(java.util.Locale.ROOT), canonical);
        return true;
    }

    /** Release a session; used on disconnect and identity re-link. */
    public void release(UUID minecraftUuid) {
        RegisteredIdentity removed = byUuid.remove(minecraftUuid);
        if (removed != null) {
            byProviderKey.remove(removed.identity().providerId() + "\u0000" + removed.identity().providerAccountId());
            byUsername.remove(removed.identity().username().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Admin operation: explicitly link two provider identities (spec §9). */
    public boolean linkIdentities(UUID fromUuid, UUID toUuid) {
        RegisteredIdentity from = byUuid.remove(fromUuid);
        if (from == null) {
            log.warn("Cannot link identity: {} not registered", fromUuid);
            return false;
        }
        byProviderKey.remove(from.identity().providerId() + "\u0000" + from.identity().providerAccountId());
        HybridIdentity linked = new HybridIdentity(
                from.identity().providerId(),
                from.identity().providerAccountId(),
                from.identity().username(),
                toUuid,
                from.identity().sessionId(),
                from.identity().providerMetadata());
        byUuid.put(toUuid, from);
        byProviderKey.put(linked.providerId() + "\u0000" + linked.providerAccountId(), toUuid);
        byUsername.put(linked.username().toLowerCase(java.util.Locale.ROOT), toUuid);
        return true;
    }

    public int size() {
        return byUuid.size();
    }
}