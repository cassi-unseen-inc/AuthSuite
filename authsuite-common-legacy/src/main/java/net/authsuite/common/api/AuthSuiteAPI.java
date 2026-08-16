package net.authsuite.common.api;

import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.perm.PermissionService;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.skin.SkinDirective;

import java.util.Optional;
import java.util.UUID;

/**
 * Public mod API (spec §11, {@code HybridAuthAPI} equivalent).
 * <p>
 * Exposes: getIdentity, getProvider, getCanonicalUUID, resolveIdentity,
 * hasPermission, getPlayerData. This interface is implemented by each platform
 * module and registered into the platform service callback.
 */
public interface AuthSuiteAPI {

    Optional<HybridIdentity> getIdentity(UUID canonicalUuid);

    Optional<AuthProvider> getProvider(String providerId);

    Optional<UUID> getCanonicalUUID(String providerId, String providerAccountId);

    Optional<HybridIdentity> resolveIdentity(String providerId, String providerAccountId);

    boolean hasPermission(HybridIdentity identity, String node);

    SkinDirective getServerSkinDirective(UUID canonicalUuid);

    IdentityRegistry identityRegistry();

    PermissionService permissions();
}