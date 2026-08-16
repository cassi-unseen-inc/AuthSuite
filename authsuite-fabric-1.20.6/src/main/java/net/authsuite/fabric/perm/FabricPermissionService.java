package net.authsuite.fabric.perm;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.perm.PermissionService;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.fabric.ops.OpsRouter;
import net.authsuite.common.log.AuthSuiteLogger;

/**
 * Fabric implementation of {@link PermissionService}. Permission evaluation is
 * based exclusively on the server's authenticated identity registry, never on
 * client-supplied identity data (spec §11).
 */
public final class FabricPermissionService implements PermissionService {

    private final IdentityRegistry identityRegistry;
    private final ProviderManager providerManager;
    private final AuthSuiteConfig config;
    private final AuthSuiteLogger log;

    public FabricPermissionService(IdentityRegistry identityRegistry, ProviderManager providerManager,
                                AuthSuiteConfig config, AuthSuiteLogger log) {
        this.identityRegistry = identityRegistry;
        this.providerManager = providerManager;
        this.config = config;
        this.log = log;
    }

    @Override
    public boolean hasPermission(HybridIdentity identity, String node) {
        if (identity == null || node == null) {
            return false;
        }
        String p = node.trim();
        if (p.equalsIgnoreCase("authsuite.provider:" + identity.providerId())) {
            return true;
        }
        if (p.equalsIgnoreCase("authsuite.identity:" + identity.providerAccountId())) {
            return true;
        }
        if (p.equalsIgnoreCase("authsuite.authenticated")) {
            return identityRegistry.byProviderKey(identity.providerId(), identity.providerAccountId()).isPresent();
        }
        // Generic fallback: a node like "authsuite.<provider>" grants membership in that provider.
        return p.equalsIgnoreCase("authsuite." + identity.providerId());
    }

    @Override
    public boolean hasPermission(String providerId, String providerAccountId, String node) {
        return identityRegistry.byProviderKey(providerId, providerAccountId)
                .map(reg -> hasPermission(reg.identity(), node))
                .orElse(false);
    }

    @Override
    public int opLevel(HybridIdentity identity) {
        if (identity == null) {
            return 0;
        }
        OpsRouter router = router();
        return router == null ? 0 : router.effectiveLevel(profileOf(identity));
    }

    private OpsRouter router() {
        return net.authsuite.fabric.server.FabricServer.get() != null
                ? net.authsuite.fabric.server.FabricServer.get().opsRouter()
                : null;
    }

    private com.mojang.authlib.GameProfile profileOf(HybridIdentity identity) {
        return new com.mojang.authlib.GameProfile(identity.minecraftUUID(), identity.username());
    }
}