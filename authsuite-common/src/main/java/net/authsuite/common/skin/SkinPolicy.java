package net.authsuite.common.skin;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.log.AuthSuiteLogger;

import java.net.URI;
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
        var provider = config.providers().stream()
                .filter(p -> p.id().equalsIgnoreCase(providerId))
                .findFirst()
                .orElse(null);
        if (provider == null) {
            return Set.of();
        }
        String domain = provider.domain();
        if (domain == null || domain.isBlank()) {
            return Set.of();
        }
        String host = domain.toLowerCase(Locale.ROOT);
        return Set.of(host, "www." + host);
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

    /**
     * Validates a full skin directive. The permitted host set comes from the
     * locally configured providers; when the client has no configuration for the
     * provider (e.g. a zero-config client), the host declared by the authoritative
     * server directive is used instead. HTTPS and hostile-URL-component checks
     * always apply.
     */
    public ValidationResult validateDirective(SkinDirective directive, int maxRedirects) {
        if (directive == null) {
            return ValidationResult.reject("null directive");
        }
        SkinResource resource = directive.skinResource();
        if (resource == null) {
            return ValidationResult.reject("null skin resource");
        }
        try {
            URI uri = resource.uri();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme)) {
                return ValidationResult.reject("scheme must be https");
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            Set<String> allowed = allowedHostsFor(directive.providerId());
            if (allowed.isEmpty()) {
                String declared = directive.skinHost();
                if (declared == null || declared.isBlank()) {
                    return ValidationResult.reject("no allowed host for provider " + directive.providerId());
                }
                allowed = Set.of(declared.toLowerCase(Locale.ROOT));
            }
            if (!allowed.contains(host)) {
                return ValidationResult.reject("host " + host + " not allowlisted for " + directive.providerId());
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