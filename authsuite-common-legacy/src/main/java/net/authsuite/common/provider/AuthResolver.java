package net.authsuite.common.provider;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.security.LogSanitizer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Provider resolution engine implementing spec §5.
 * <p>
 * Behavior:
 * <ul>
 *   <li>Unqualified logins cycle through the configured priority chain with
 *       fallthrough only on fallthrough-eligible failures.</li>
 *   <li>Shortcode-qualified requests (e.g. {@code /op Steve LS}) bypass the chain;
 *       failure means immediate rejection (no fallthrough).</li>
 *   <li>An optional client provider preference may reorder the first attempt but
 *       NEVER proves identity; the server independently authenticates the
 *       resulting provider identity.</li>
 * </ul>
 */
public final class AuthResolver {

    private final ProviderManager manager;
    private final AuthSuiteConfig config;
    private final AuthSuiteLogger log;

    public AuthResolver(ProviderManager manager, AuthSuiteConfig config, AuthSuiteLogger log) {
        this.manager = manager;
        this.config = config;
        this.log = log;
    }

    /** Attempt selection hint published by the client (untrusted routing hint). */
    public static final class PreferenceHint {
        private final String providerIdOrShortcode;
        private final String sessionHint;

        public PreferenceHint(String providerIdOrShortcode, String sessionHint) {
            this.providerIdOrShortcode = providerIdOrShortcode;
            this.sessionHint = sessionHint;
        }

        public String providerIdOrShortcode() {
            return providerIdOrShortcode;
        }

        public String sessionHint() {
            return sessionHint;
        }
    }

    public static final class Resolution {
        private final AuthenticatedProfile profile;
        private final List<ProviderFailure> failures;

        public Resolution(AuthenticatedProfile profile, List<ProviderFailure> failures) {
            this.profile = profile;
            this.failures = failures;
        }

        public AuthenticatedProfile profile() {
            return profile;
        }

        public List<ProviderFailure> failures() {
            return failures;
        }

        public Optional<ProviderFailure> lastFailure() {
            if (failures == null || failures.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(failures.get(failures.size() - 1));
        }
    }

    /**
     * Resolve an unqualified automatic login (or a client-preferred login).
     *
     * @param explicitTarget provider shortcode/id if the request was shortcode-qualified
     * @param preference     client provider preference (may be null/empty)
     */
    public CompletableFuture<Resolution> resolve(
            AuthAttempt attempt, String explicitTarget, PreferenceHint preference) {

        List<AuthProvider> chain = buildChain(explicitTarget, preference);

        AuthProvider preferred = firstOf(chain, preference);
        boolean explicit = explicitTarget != null && !explicitTarget.trim().isEmpty();

        List<ProviderFailure> failures = new ArrayList<>();

        return tryChain(attempt, chain, preferred, explicit, failures, 0);
    }

    private CompletableFuture<Resolution> tryChain(
            AuthAttempt attempt,
            List<AuthProvider> chain,
            AuthProvider preferred,
            boolean explicit,
            List<ProviderFailure> failures,
            int index) {

        AuthProvider candidate;
        if (preferred != null && failures.isEmpty()) {
            candidate = preferred;
        } else if (index < chain.size()) {
            candidate = chain.get(index);
        } else {
            return CompletableFuture.completedFuture(new Resolution(null, failures));
        }

        if (candidate == null) {
            return tryChain(attempt, chain, null, explicit, failures, index + 1);
        }

        return candidate.authenticate(attempt)
                .handle((profile, ex) -> {
                    AuthenticatedProfile result = ex == null ? profile : null;
                    if (ex != null) {
                        failures.add(failureFrom(candidate, ex));
                    } else if (profile == null) {
                        failures.add(ProviderFailure.accountNotFound(
                                candidateId(candidate), "null profile from provider " + candidate.shortcode()));
                    }
                    boolean ok = result != null;
                    boolean fallthrough = !explicit
                            && !ok
                            && lastFailureIsEligible(failures);
                    boolean advance = !ok && fallthrough;
                    if (ok) {
                        return CompletableFuture.completedFuture(new Resolution(result, failures));
                    }
                    if (explicit || !advance) {
                        return CompletableFuture.completedFuture(new Resolution(null, failures));
                    }
                    return tryChain(attempt, chain, null, false, failures, index + 1);
                })
                .thenCompose(f -> f);
    }

    private boolean lastFailureIsEligible(List<ProviderFailure> failures) {
        return !failures.isEmpty() && failures.get(failures.size() - 1).isFallthroughEligible();
    }

    private ProviderFailure failureFrom(AuthProvider candidate, Throwable ex) {
        Throwable c = ex;
        while (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        if (c instanceof ProviderFailureException) {
            ProviderFailureException pfe = (ProviderFailureException) c;
            if (pfe.failure() != null) {
                return pfe.failure();
            }
        }
        return ProviderFailure.unavailable(candidateId(candidate), LogSanitizer.sanitizeMessage(c));
    }

    private ProviderId candidateId(AuthProvider candidate) {
        return candidate == null
                ? new ProviderId("?", "?", "?", "?")
                : new ProviderId(candidate.providerId(), candidate.shortcode(), candidate.domain(), candidate.providerId());
    }

    private List<AuthProvider> buildChain(String explicitTarget, PreferenceHint preference) {
        if (explicitTarget != null && !explicitTarget.trim().isEmpty()) {
            AuthProvider explicit = lookup(explicitTarget).orElse(null);
            return explicit == null ? Collections.<AuthProvider>emptyList() : Collections.singletonList(explicit);
        }
        List<String> chain = new ArrayList<>(manager.priorityChain());
        List<AuthProvider> out = new ArrayList<>();
        String prefShortcode = preferenceShortcode(preference);
        if (prefShortcode != null
                && config.clientPreferencePolicy() != AuthSuiteConfig.ClientPreferencePolicy.IGNORE
                && manager.byShortcode(prefShortcode).isPresent()) {
            chain.remove(prefShortcode);
            chain.add(0, prefShortcode);
        }
        for (String sc : chain) {
            manager.byShortcode(sc).ifPresent(out::add);
        }
        // Append any enabled providers not in the priority chain.
        for (AuthProvider p : manager.enabled()) {
            if (out.stream().noneMatch(x -> x.providerId().equals(p.providerId()))) {
                out.add(p);
            }
        }
        return out;
    }

    private String preferenceShortcode(PreferenceHint preference) {
        if (preference == null || preference.providerIdOrShortcode() == null) {
            return null;
        }
        String value = preference.providerIdOrShortcode().trim();
        Optional<AuthProvider> byId = manager.byId(value);
        if (byId.isPresent()) {
            return byId.get().shortcode();
        }
        return value.toUpperCase();
    }

    private AuthProvider firstOf(List<AuthProvider> chain, PreferenceHint preference) {
        if (preference == null) {
            return chain.isEmpty() ? null : chain.get(0);
        }
        String value = preferenceShortcode(preference);
        if (value != null) {
            Optional<AuthProvider> pref = manager.byShortcode(value);
            if (pref.isPresent()) {
                return pref.get();
            }
            Optional<AuthProvider> byId = manager.byId(value);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        return chain.isEmpty() ? null : chain.get(0);
    }

    private Optional<AuthProvider> lookup(String target) {
        Optional<AuthProvider> byShort = manager.byShortcode(target);
        if (byShort.isPresent()) {
            return byShort;
        }
        return manager.byId(target);
    }
}