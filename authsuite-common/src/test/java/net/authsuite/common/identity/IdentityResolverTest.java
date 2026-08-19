package net.authsuite.common.identity;

import net.authsuite.common.config.ShortcodeRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthAttempt;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.provider.AuthenticatedProfile;
import net.authsuite.common.provider.ProviderId;
import net.authsuite.common.provider.ProviderManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Central resolver semantics (post-audit §7): unqualified usernames resolve to a
 * single identity, several identities make them AMBIGUOUS, and provider-qualified
 * usernames stay unambiguous even when names collide across providers.
 */
class IdentityResolverTest {

    private final ShortcodeRegistry shortcodes = new ShortcodeRegistry(AuthSuiteLogger.noop());
    private final ProviderManager manager = new ProviderManager(shortcodes, AuthSuiteLogger.noop());
    private final IdentityRegistry registry = new IdentityRegistry(AuthSuiteLogger.noop());
    private final IdentityResolver resolver = new IdentityResolver(registry, manager, AuthSuiteLogger.noop());

    private final ProviderId ma = provider("microsoft", "MA", "microsoft.com");
    private final ProviderId eb = provider("elyby", "EB", "ely.by");

    private ProviderId provider(String id, String shortcode, String domain) {
        ProviderId pid = new ProviderId(id, shortcode, domain, id);
        shortcodes.register(pid);
        manager.register(new StubProvider(pid), 0);
        return pid;
    }

    private void register(String providerId, String account, String username) {
        UUID canonical = CanonicalUuid.from(providerId, account);
        registry.register(new HybridIdentity(providerId, account, username, canonical, "s-" + account, null),
                "s-" + account, false);
    }

    @Test
    void singleUsernameResolves() {
        register("microsoft", "A1", "alice");
        IdentityResolver.UsernameResult result = resolver.resolveByUsername("alice");
        assertEquals(IdentityResolver.UsernameResolution.FOUND, result.status());
        assertTrue(result.identity().isPresent());
    }

    @Test
    void collidingUsernameIsAmbiguous() {
        register("microsoft", "A1", "cassi__confused");
        register("elyby", "B2", "cassi__confused");
        IdentityResolver.UsernameResult result = resolver.resolveByUsername("cassi__confused");
        assertEquals(IdentityResolver.UsernameResolution.AMBIGUOUS, result.status());
        assertFalse(result.identity().isPresent());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void unknownUsernameIsNotFound() {
        IdentityResolver.UsernameResult result = resolver.resolveByUsername("ghost");
        assertEquals(IdentityResolver.UsernameResolution.NOT_FOUND, result.status());
    }

    @Test
    void qualifiedUsernameIsUnambiguous() {
        register("microsoft", "A1", "cassi__confused");
        register("elyby", "B2", "cassi__confused");
        assertTrue(resolver.resolveByQualifiedUsername("MA", "cassi__confused").isPresent());
        assertTrue(resolver.resolveByQualifiedUsername("microsoft", "cassi__confused").isPresent());
        assertTrue(resolver.resolveByQualifiedUsername("EB", "cassi__confused").isPresent());
        IdentityRegistry.RegisteredIdentity maIdentity =
                resolver.resolveByQualifiedUsername("MA", "cassi__confused").get();
        assertEquals("microsoft", maIdentity.identity().providerId());
        IdentityRegistry.RegisteredIdentity ebIdentity =
                resolver.resolveByQualifiedUsername("EB", "cassi__confused").get();
        assertEquals("elyby", ebIdentity.identity().providerId());
    }

    @Test
    void qualifiedUsernameWithWrongProviderIsEmpty() {
        register("microsoft", "A1", "alice");
        assertTrue(resolver.resolveByQualifiedUsername("EB", "alice").isEmpty());
        assertTrue(resolver.resolveByQualifiedUsername("UNKNOWN", "alice").isEmpty());
    }

    @Test
    void identityTokenQualifiedAndBare() {
        register("microsoft", "A1", "cassi__confused");
        register("elyby", "B2", "cassi__confused");
        register("microsoft", "C3", "solo");
        assertTrue(resolver.resolveIdentityToken("MA:cassi__confused").isPresent());
        assertTrue(resolver.resolveIdentityToken("EB:cassi__confused").isPresent());
        // Bare name with a single identity resolves; ambiguous one does not.
        assertTrue(resolver.resolveIdentityToken("solo").isPresent());
        assertFalse(resolver.resolveIdentityToken("cassi__confused").isPresent());
        assertFalse(resolver.resolveIdentityToken("  ").isPresent());
    }

    @Test
    void resolveByUuidAndProviderAccount() {
        register("microsoft", "A1", "alice");
        UUID canonical = CanonicalUuid.from("microsoft", "A1");
        assertTrue(resolver.resolveByUuid(canonical).isPresent());
        assertTrue(resolver.resolveByProviderAccount("microsoft", "A1").isPresent());
        assertTrue(resolver.resolveByProviderAccount("microsoft", "nope").isEmpty());
    }

    /** Minimal provider stub for resolver wiring. */
    private static final class StubProvider implements AuthProvider {
        private final ProviderId pid;

        StubProvider(ProviderId pid) {
            this.pid = pid;
        }

        @Override
        public String providerId() {
            return pid.providerId();
        }

        @Override
        public String shortcode() {
            return pid.shortcode();
        }

        @Override
        public String domain() {
            return pid.domain();
        }

        @Override
        public CompletableFuture<AuthenticatedProfile> authenticate(AuthAttempt attempt) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }

        @Override
        public CompletableFuture<ResolverResult> resolvePlayer(String username) {
            return CompletableFuture.completedFuture(new ResolverResult(username, username));
        }

        @Override
        public CompletableFuture<SessionValidity> validateSession(String providerAccountId) {
            return CompletableFuture.completedFuture(SessionValidity.VALID);
        }

        @Override
        public CompletableFuture<Void> disconnect(String providerAccountId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}