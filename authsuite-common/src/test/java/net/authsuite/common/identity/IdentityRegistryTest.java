package net.authsuite.common.identity;

import net.authsuite.common.log.AuthSuiteLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity registry invariants (post-audit §4-§6): only registered identities
 * resolve, the canonical UUID is claimed atomically, usernames resolve to sets of
 * canonical UUIDs (a username is not globally unique), and release uses
 * compare-and-remove semantics that never damage another identity's indexes.
 */
class IdentityRegistryTest {

    private final IdentityRegistry registry = new IdentityRegistry(AuthSuiteLogger.noop());

    private HybridIdentity identity(String provider, String account, String username, UUID canonical) {
        return new HybridIdentity(provider, account, username, canonical, "session-" + account, null);
    }

    @Test
    void registerAndLookupByUuid() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s1", false));
        assertTrue(registry.byUuid(uuid).isPresent());
    }

    @Test
    void lookupByProviderKey() {
        UUID uuid = CanonicalUuid.from("elyby", "2000002");
        registry.register(identity("elyby", "2000002", "bob", uuid), "s1", false);
        assertTrue(registry.byProviderKey("elyby", "2000002").isPresent());
        assertFalse(registry.byProviderKey("elyby", "wrong").isPresent());
    }

    @Test
    void sameUsernameAcrossProvidersResolvesToSet() {
        UUID ma = CanonicalUuid.from("microsoft", "A1");
        UUID eb = CanonicalUuid.from("elyby", "B2");
        assertTrue(registry.register(identity("microsoft", "A1", "cassi__confused", ma), "s1", false));
        assertTrue(registry.register(identity("elyby", "B2", "cassi__confused", eb), "s2", false));

        Set<UUID> candidates = registry.usernames("cassi__confused");
        assertEquals(Set.of(ma, eb), candidates);
        // Ambiguous bare username must not arbitrarily select one identity.
        assertTrue(registry.byUsername("cassi__confused").isEmpty());
    }

    @Test
    void releaseRemovesOnlyOwnUsernameEntry() {
        UUID ma = CanonicalUuid.from("microsoft", "A1");
        UUID eb = CanonicalUuid.from("elyby", "B2");
        registry.register(identity("microsoft", "A1", "cassi__confused", ma), "s1", false);
        registry.register(identity("elyby", "B2", "cassi__confused", eb), "s2", false);

        registry.release(ma);

        // B's username entry must survive A's release.
        assertEquals(Set.of(eb), registry.usernames("cassi__confused"));
        assertTrue(registry.byUuid(eb).isPresent());
        assertFalse(registry.byUuid(ma).isPresent());
        assertFalse(registry.byProviderKey("microsoft", "A1").isPresent());
        assertTrue(registry.byProviderKey("elyby", "B2").isPresent());
    }

    @Test
    void releaseOfUnknownUuidIsNoop() {
        registry.release(UUID.randomUUID());
        assertEquals(0, registry.size());
    }

    @Test
    void duplicateCanonicalRejectedAtomically() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s1", false));
        // A second registration for the same canonical UUID is rejected and must
        // not clobber the existing indexes.
        assertFalse(registry.register(identity("littleskins", "1000001", "alice2", uuid), "s2", false));
        assertEquals(uuid, registry.byUuid(uuid).get().identity().minecraftUUID());
        assertTrue(registry.byUsername("alice").isPresent());
        // release allows a new session
        registry.release(uuid);
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s2", false));
    }

    @Test
    void releaseExistingReplacesSession() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s1", false));
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s2", true));
        assertEquals("s2", registry.byUuid(uuid).get().sessionId());
    }

    @Test
    void releaseRemovesAllLookups() {
        UUID uuid = CanonicalUuid.from("elyby", "2000002");
        registry.register(identity("elyby", "2000002", "bob", uuid), "s1", false);
        registry.release(uuid);
        assertFalse(registry.byUuid(uuid).isPresent());
        assertFalse(registry.byProviderKey("elyby", "2000002").isPresent());
        assertFalse(registry.byUsername("bob").isPresent());
        assertTrue(registry.usernames("bob").isEmpty());
    }

    @Test
    void unknownIdentityDoesNotResolve() {
        assertFalse(registry.byUuid(UUID.randomUUID()).isPresent());
        assertFalse(registry.byUsername("ghost").isPresent());
        assertEquals(Optional.empty(), registry.byProviderKey("littleskins", "nope"));
        assertTrue(registry.usernames("ghost").isEmpty());
    }

    @Test
    void byUsernameSingleIsUnambiguous() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        registry.register(identity("littleskins", "1000001", "Alice", uuid), "s1", false);
        assertTrue(registry.byUsername("alice").isPresent());
        assertEquals(uuid, registry.byUsername("ALICE").get().identity().minecraftUUID());
    }

    @Test
    void linkPreservesCompareAndRemoveSafety() {
        UUID a = CanonicalUuid.from("littleskins", "1000001");
        UUID b = CanonicalUuid.from("littleskins", "1000002");
        registry.register(identity("littleskins", "1000001", "alice", a), "s1", false);
        assertTrue(registry.linkIdentities(a, b));
        assertFalse(registry.byUuid(a).isPresent());
        assertTrue(registry.byUuid(b).isPresent());
        assertEquals(b, registry.byProviderKey("littleskins", "1000001").get().identity().minecraftUUID());
    }
}