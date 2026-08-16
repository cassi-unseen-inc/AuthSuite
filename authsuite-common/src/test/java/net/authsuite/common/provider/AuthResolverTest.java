package net.authsuite.common.provider;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.config.ProviderConfig;
import net.authsuite.common.config.ShortcodeRegistry;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthProvider.ResolverResult;
import net.authsuite.common.provider.AuthProvider.SessionValidity;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuthResolver chain semantics: priority-ordered fallthrough for unqualified
 * logins, account-not-found allowing the next provider, and resolution stopping at
 * the first success.
 */
class AuthResolverTest {

    private static final AuthAttempt ATTEMPT = new AuthAttempt("alice", "deadbeef", null, false);

    private final ShortcodeRegistry shortcodes = new ShortcodeRegistry(AuthSuiteLogger.noop());

    private AuthResolver buildResolver(List<AuthProvider> providers, String providerId, List<String> priority) {
        ProviderManager manager = new ProviderManager(shortcodes, AuthSuiteLogger.noop());
        AuthSuiteConfig config = new AuthSuiteConfig();
        config.setPriority(priority);
        for (int i = 0; i < providers.size(); i++) {
            AuthProvider p = providers.get(i);
            shortcodes.register(new ProviderId(p.providerId(), p.shortcode(), p.domain(), p.providerId()));
            manager.register(p, priority.indexOf(p.shortcode()));
        }
        return new AuthResolver(manager, config, AuthSuiteLogger.noop());
    }

    private static AuthProvider accountNotFoundProvider(String providerId, String shortcode, String domain) {
        return new StubProvider(providerId, shortcode, domain,
                () -> CompletableFuture.failedFuture(new ProviderFailureException(
                        ProviderFailure.accountNotFound(new ProviderId(providerId, shortcode, domain, providerId), "no account"))));
    }

    private static AuthProvider successProvider(String providerId, String shortcode, String domain, String account) {
        return new StubProvider(providerId, shortcode, domain, () -> {
            AuthenticatedProfile profile = new AuthenticatedProfile(
                    new ProviderId(providerId, shortcode, domain, providerId),
                    account, "alice", UUID.randomUUID(), java.util.Map.of(), System.currentTimeMillis() + 60_000);
            return CompletableFuture.completedFuture(profile);
        });
    }

    @Test
    void fallthroughToNextProviderOnAccountNotFound() {
        AuthResolver resolver = buildResolver(
                List.of(accountNotFoundProvider("littleskins", "LS", "littleskin.cn"),
                        successProvider("elyby", "EB", "ely.by", "2000001")),
                "?",
                List.of("LS", "EB"));
        AuthResolver.Resolution resolution = resolver.resolve(ATTEMPT, null, null).join();
        assertNotNull(resolution.profile());
        assertEquals("elyby", resolution.profile().provider().providerId());
        assertFalse(resolution.failures().isEmpty());
    }

    @Test
    void firstSuccessStopsChain() {
        AuthResolver resolver = buildResolver(
                List.of(successProvider("littleskins", "LS", "littleskin.cn", "1000001"),
                        successProvider("elyby", "EB", "ely.by", "2000001")),
                "?",
                List.of("LS", "EB"));
        AuthResolver.Resolution resolution = resolver.resolve(ATTEMPT, null, null).join();
        assertNotNull(resolution.profile());
        assertEquals("littleskins", resolution.profile().provider().providerId());
        assertEquals(0, resolution.failures().size());
    }

    @Test
    void allFailYieldsNullProfile() {
        AuthResolver resolver = buildResolver(
                List.of(accountNotFoundProvider("littleskins", "LS", "littleskin.cn"),
                        accountNotFoundProvider("elyby", "EB", "ely.by")),
                "?",
                List.of("LS", "EB"));
        AuthResolver.Resolution resolution = resolver.resolve(ATTEMPT, null, null).join();
        assertNull(resolution.profile());
        assertEquals(2, resolution.failures().size());
    }

    @Test
    void explicitTargetNeverFallsThrough() {
        AuthResolver resolver = buildResolver(
                List.of(accountNotFoundProvider("littleskins", "LS", "littleskin.cn"),
                        successProvider("elyby", "EB", "ely.by", "2000001")),
                "?",
                List.of("LS", "EB"));
        AuthResolver.Resolution resolution = resolver.resolve(ATTEMPT, "LS", null).join();
        assertNull(resolution.profile());
    }

    @Test
    void preferenceHintsPreferredProvider() {
        AuthResolver resolver = buildResolver(
                List.of(accountNotFoundProvider("littleskins", "LS", "littleskin.cn"),
                        successProvider("elyby", "EB", "ely.by", "2000001")),
                "?",
                List.of("LS", "EB"));
        AuthResolver.PreferenceHint preference = new AuthResolver.PreferenceHint("EB", "");
        AuthResolver.Resolution resolution = resolver.resolve(ATTEMPT, null, preference).join();
        assertNotNull(resolution.profile());
        assertEquals("elyby", resolution.profile().provider().providerId());
    }

    /** Minimal stub satisfying the AuthProvider contract without network. */
    private static final class StubProvider implements AuthProvider {
        private final String providerId;
        private final String shortcode;
        private final String domain;
        private final java.util.function.Supplier<CompletableFuture<AuthenticatedProfile>> auth;

        StubProvider(String providerId, String shortcode, String domain,
                     java.util.function.Supplier<CompletableFuture<AuthenticatedProfile>> auth) {
            this.providerId = providerId;
            this.shortcode = shortcode;
            this.domain = domain;
            this.auth = auth;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public String shortcode() {
            return shortcode;
        }

        @Override
        public String domain() {
            return domain;
        }

        @Override
        public CompletableFuture<AuthenticatedProfile> authenticate(AuthAttempt attempt) {
            return auth.get();
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