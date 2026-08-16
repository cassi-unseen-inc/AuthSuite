package net.authsuite.common.perm;

import net.authsuite.common.identity.HybridIdentity;

/**
 * Permission evaluation for qualified identities (spec §11).
 * <p>
 * Principle separation: Authentication != Identity != Authorization. Permission
 * evaluation uses the server's authenticated identity registry, never
 * client-supplied identity data. Client provider preference has no direct
 * authorization semantics.
 */
public interface PermissionService {

    /**
     * Resolve a permission node against a registered authenticated identity.
     * Supports qualified nodes like {@code authsuite.provider:LS} or
     * {@code authsuite.identity:12345} as well as plain nodes.
     */
    boolean hasPermission(HybridIdentity identity, String node);

    /** Whether the identity qualifies for the given permission family. */
    boolean hasPermission(String providerId, String providerAccountId, String node);

    /** Op level for a provider identity (0..4). External providers never merge into vanilla ops.json. */
    int opLevel(HybridIdentity identity);
}