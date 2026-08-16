package net.authsuite.common.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Spec §9: the canonical Minecraft UUID must be deterministically derived from the
 * immutable {@code providerId + providerAccountId} pair, never from a client
 * supplied username or UUID.
 */
class CanonicalUuidTest {

    @Test
    void isDeterministic() {
        UUID a = CanonicalUuid.from("littleskins", "1000001");
        UUID b = CanonicalUuid.from("littleskins", "1000001");
        assertEquals(a, b);
    }

    @Test
    void differsAcrossProviders() {
        assertNotEquals(
                CanonicalUuid.from("littleskins", "1000001"),
                CanonicalUuid.from("elyby", "1000001"));
    }

    @Test
    void differsAcrossAccounts() {
        assertNotEquals(
                CanonicalUuid.from("littleskins", "1000001"),
                CanonicalUuid.from("littleskins", "1000002"));
    }
}