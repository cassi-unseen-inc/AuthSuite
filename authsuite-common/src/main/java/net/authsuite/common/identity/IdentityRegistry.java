package net.authsuite.common.identity;

import net.authsuite.common.log.AuthSuiteLogger;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side identity registry. Binds a validated {@link ProviderIdentity} to a
 * canonical Minecraft identity.
 * <p>
 * Indexing model (spec §10, post-audit):
 * <ul>
 *   <li>{@code UUID} (canonical) {@code ->} {@link RegisteredIdentity}</li>
 *   <li>{@code provider + account id} {@code ->} canonical UUID</li>
 *   <li>{@code username} {@code ->} {@code Set<UUID>} — a username is NOT globally
 *       unique; different providers may legitimately host the same name.</li>
 * </ul>
 * Registration atomically claims the canonical UUID and only then updates the
 * secondary indexes. Release uses compare-and-remove semantics so releasing one
 * identity never destroys another identity's index entries.
 */
public final class IdentityRegistry {

    private final AuthSuiteLogger log;
    private final Map<UUID, RegisteredIdentity> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byProviderKey = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byUsername = new ConcurrentHashMap<>();

    public IdentityRegistry(AuthSuiteLogger log) {
        this.log = log;
    }

    public record RegisteredIdentity(HybridIdentity identity, String sessionId, long joinedAt) {
    }

    public Optional<RegisteredIdentity> byUuid(UUID minecraftUuid) {
        return minecraftUuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(minecraftUuid));
    }

    public Optional<RegisteredIdentity> byProviderKey(String providerId, String providerAccountId) {
        if (providerId == null || providerAccountId == null) {
            return Optional.empty();
        }
        UUID uuid = byProviderKey.get(providerId + "\u0000" + providerAccountId);
        return uuid == null ? Optional.empty() : byUuid(uuid);
    }

    /**
     * All canonical identities registered under a username (case-insensitive).
     * A username may legitimately resolve to several identities across providers,
     * so callers must treat the result as a set.
     */
    public Set<UUID> usernames(String username) {
        if (username == null) {
            return Set.of();
        }
        Set<UUID> set = byUsername.get(username.toLowerCase(java.util.Locale.ROOT));
        return set == null ? Set.of() : Set.copyOf(set);
    }

    /** All registered usernames (lowercased) for suggestion/completion surfaces. */
    public Set<String> usernames() {
        return Set.copyOf(byUsername.keySet());
    }

    /**
     * Legacy single-identity username lookup. Returns the identity only when the
     * username maps to exactly one active identity; an ambiguous username (several
     * providers share the name) or an unknown one resolves to empty rather than
     * selecting arbitrarily. New code should prefer {@link #usernames} and the
     * {@link net.authsuite.common.identity.IdentityResolver}.
     */
    public Optional<RegisteredIdentity> byUsername(String username) {
        Set<UUID> candidates = usernames(username);
        if (candidates.size() != 1) {
            return Optional.empty();
        }
        return byUuid(candidates.iterator().next());
    }

    /**
     * Atomically registers a valid identity for a session.
     * <p>
     * The canonical UUID is claimed first; only on a successful claim are the
     * secondary indexes updated. When {@code releaseExisting} is {@code false},
     * an already-active canonical identity rejects the registration (duplicate
     * session). When {@code releaseExisting} is {@code true}, the previous active
     * session for the same canonical identity is released first.
     */
    public synchronized boolean register(HybridIdentity identity, String sessionId, boolean releaseExisting) {
        UUID canonical = identity.minecraftUUID();
        if (!releaseExisting && byUuid.containsKey(canonical)) {
            log.info("Refusing duplicate session for canonical identity {} (session already active)", canonical);
            return false;
        }
        RegisteredIdentity previous = byUuid.remove(canonical);
        if (previous != null) {
            removeSecondaryIndexes(previous);
        }
        RegisteredIdentity registered = new RegisteredIdentity(identity, sessionId, System.currentTimeMillis());
        byUuid.put(canonical, registered);
        addSecondaryIndexes(identity, canonical);
        return true;
    }

    /**
     * Releases a session (disconnect, identity re-link). The canonical identity is
     * removed and every secondary index entry for that identity is removed using
     * compare-and-remove semantics: a username set never drops identities other
     * than the released canonical UUID, and the provider key is only removed when
     * it still points at the released identity.
     */
    public synchronized void release(UUID minecraftUuid) {
        if (minecraftUuid == null) {
            return;
        }
        RegisteredIdentity removed = byUuid.remove(minecraftUuid);
        if (removed != null) {
            removeSecondaryIndexes(removed);
        }
    }

    /** Admin operation: explicitly link two provider identities (spec §9). */
    public synchronized boolean linkIdentities(UUID fromUuid, UUID toUuid) {
        RegisteredIdentity from = byUuid.remove(fromUuid);
        if (from == null) {
            log.warn("Cannot link identity: {} not registered", fromUuid);
            return false;
        }
        removeSecondaryIndexes(from);
        HybridIdentity linked = new HybridIdentity(
                from.identity().providerId(),
                from.identity().providerAccountId(),
                from.identity().username(),
                toUuid,
                from.identity().sessionId(),
                from.identity().providerMetadata());
        RegisteredIdentity registered = new RegisteredIdentity(linked, from.sessionId(), from.joinedAt());
        byUuid.put(toUuid, registered);
        addSecondaryIndexes(linked, toUuid);
        return true;
    }

    public int size() {
        return byUuid.size();
    }

    private void addSecondaryIndexes(HybridIdentity identity, UUID canonical) {
        byProviderKey.put(identity.providerId() + "\u0000" + identity.providerAccountId(), canonical);
        String usernameKey = identity.username().toLowerCase(java.util.Locale.ROOT);
        byUsername.compute(usernameKey, (k, set) -> {
            Set<UUID> updated = set == null ? new HashSet<>() : new HashSet<>(set);
            updated.add(canonical);
            return updated;
        });
    }

    private void removeSecondaryIndexes(RegisteredIdentity removed) {
        HybridIdentity identity = removed.identity();
        byProviderKey.remove(identity.providerId() + "\u0000" + identity.providerAccountId(),
                identity.minecraftUUID());
        String usernameKey = identity.username().toLowerCase(java.util.Locale.ROOT);
        byUsername.computeIfPresent(usernameKey, (k, set) -> {
            Set<UUID> updated = new HashSet<>(set);
            updated.remove(identity.minecraftUUID());
            return updated.isEmpty() ? null : updated;
        });
    }
}