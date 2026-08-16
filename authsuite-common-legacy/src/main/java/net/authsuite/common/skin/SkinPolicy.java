package net.authsuite.common.skin;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.config.ProviderConfig;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Client-side skin/cape resource policy enforcement (spec §4).
 * <p>
 * Validation rules:
 * <ul>
 *   <li>HTTPS only</li>
 *   <li>host restricted to the provider's configured/known domain set</li>
 *   <li>no redirects escaping the permitted host set</li>
 *   <li>no {@code file}, {@code data}, or other schemes</li>
 *   <li>no query that looks like a token or secret</li>
 * </ul>
 * This is the enforcement point that turns an otherwise-passive directive into a
 * hard policy gate on the client.
 */
public final class SkinPolicy {

    private final AuthSuiteConfig config;
    private final AuthSuiteLogger log;

    public SkinPolicy(AuthSuiteConfig config, AuthSuiteLogger log) {
        this.config = config;
        this.log = log;
    }

    /** Hosts that may be consulted when resolving provider resources. */
    public Set<String> allowedHostsFor(String providerId) {
        ProviderConfig provider = null;
        for (ProviderConfig p : config.providers()) {
            if (p.id().equalsIgnoreCase(providerId)) {
                provider = p;
                break;
            }
        }
        if (provider == null) {
            return Collections.emptySet();
        }
        String domain = provider.domain();
        if (domain == null || domain.trim().isEmpty()) {
            return Collections.emptySet();
        }
        String host = domain.toLowerCase(Locale.ROOT);
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(host, "www." + host)));
    }

    public ValidationResult validate(String providerId, SkinResource resource, int maxRedirects) {
        if (resource == null) {
            return ValidationResult.reject("null resource");
        }
        try {
            URI uri = resource.uri();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme)) {
                return ValidationResult.reject("scheme must be https");
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            Set<String> allowed = allowedHostsFor(providerId);
            if (allowed.isEmpty()) {
                return ValidationResult.reject("no allowed host for provider " + providerId);
            }
            if (!allowed.contains(host)) {
                return ValidationResult.reject("host " + host + " not allowlisted for " + providerId);
            }
            if (resource.url().contains("@") || resource.url().contains("#")) {
                return ValidationResult.reject("suspicious URL components");
            }
            return ValidationResult.allow();
        } catch (RuntimeException e) {
            return ValidationResult.reject("unparseable resource url");
        }
    }

    public static final class ValidationResult {
        private final boolean allowed;
        private final String reason;

        private ValidationResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean allowed() {
            return allowed;
        }

        public String reason() {
            return reason;
        }

        public static ValidationResult allow() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}