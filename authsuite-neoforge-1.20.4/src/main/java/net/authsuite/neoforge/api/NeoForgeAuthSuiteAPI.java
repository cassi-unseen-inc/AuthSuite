package net.authsuite.neoforge.api;

import net.authsuite.common.api.AuthSuiteAPI;
import net.authsuite.common.identity.CanonicalUuid;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.perm.PermissionService;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.neoforge.ops.OpsRouter;

import java.util.Optional;
import java.util.UUID;

/**
 * NeoForge implementation of the public {@link AuthSuiteAPI}. Serves identity,
 * provider, canonical UUID and skin-directive lookups backed by the server context.
 */
public final class NeoForgeAuthSuiteAPI implements AuthSuiteAPI {

    private final IdentityRegistry identityRegistry;
    private final ProviderManager providerManager;
    private final PermissionService permissionService;
    private final OpsRouter opsRouter;
    private final AuthSuiteLogger log;

    public NeoForgeAuthSuiteAPI(IdentityRegistry identityRegistry,
                                ProviderManager providerManager,
                                PermissionService permissionService,
                                OpsRouter opsRouter,
                                AuthSuiteLogger log) {
        this.identityRegistry = identityRegistry;
        this.providerManager = providerManager;
        this.permissionService = permissionService;
        this.opsRouter = opsRouter;
        this.log = log;
    }

    @Override
    public Optional<HybridIdentity> getIdentity(UUID canonicalUuid) {
        return identityRegistry.byUuid(canonicalUuid).map(reg -> reg.identity());
    }

    @Override
    public Optional<AuthProvider> getProvider(String providerId) {
        return providerManager.byId(providerId);
    }

    @Override
    public Optional<UUID> getCanonicalUUID(String providerId, String providerAccountId) {
        try {
            return Optional.of(CanonicalUuid.from(providerId, providerAccountId));
        } catch (RuntimeException e) {
            log.warn("getCanonicalUUID failed for {}: {}", providerId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<HybridIdentity> resolveIdentity(String providerId, String providerAccountId) {
        return identityRegistry.byProviderKey(providerId, providerAccountId).map(reg -> reg.identity());
    }

    @Override
    public boolean hasPermission(HybridIdentity identity, String node) {
        return permissionService.hasPermission(identity, node);
    }

    @Override
    public SkinDirective getServerSkinDirective(UUID canonicalUuid) {
        return net.authsuite.neoforge.NeoForgeServer.get() != null
                ? net.authsuite.neoforge.NeoForgeServer.get().skinBroadcaster().directiveFor(canonicalUuid)
                : null;
    }

    @Override
    public IdentityRegistry identityRegistry() {
        return identityRegistry;
    }

    @Override
    public PermissionService permissions() {
        return permissionService;
    }
}