package net.authsuite.common.identity;

import net.authsuite.common.log.AuthSuiteLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity registry invariants (spec §12): only registered identities resolve,
 * duplicate active sessions are rejected, and name lookups work for admin tools.
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
    void lookupByUsernameCaseInsensitive() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        registry.register(identity("littleskins", "1000001", "Alice", uuid), "s1", false);
        assertTrue(registry.byUsername("alice").isPresent());
        assertEquals(uuid, registry.byUsername("ALICE").get().identity().minecraftUUID());
    }

    @Test
    void duplicateSessionRejected() {
        UUID uuid = CanonicalUuid.from("littleskins", "1000001");
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s1", false));
        assertFalse(registry.register(identity("littleskins", "1000001", "alice", uuid), "s2", false));
        // release allows a new session
        registry.release(uuid);
        assertTrue(registry.register(identity("littleskins", "1000001", "alice", uuid), "s2", false));
    }

    @Test
    void releaseRemovesAllLookups() {
        UUID uuid = CanonicalUuid.from("elyby", "2000002");
        registry.register(identity("elyby", "2000002", "bob", uuid), "s1", false);
        registry.release(uuid);
        assertFalse(registry.byUuid(uuid).isPresent());
        assertFalse(registry.byProviderKey("elyby", "2000002").isPresent());
        assertFalse(registry.byUsername("bob").isPresent());
    }

    @Test
    void unknownIdentityDoesNotResolve() {
        assertFalse(registry.byUuid(UUID.randomUUID()).isPresent());
        assertFalse(registry.byUsername("ghost").isPresent());
        assertEquals(Optional.empty(), registry.byProviderKey("littleskins", "nope"));
    }
}