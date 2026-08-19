package net.authsuite.common.identity;

import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.common.provider.ProviderManager;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central identity-resolution abstraction (post-audit §7).
 * <p>
 * All AuthSuite-aware systems resolve identities through this class instead of
 * maintaining their own {@code username -> player} logic:
 * <ul>
 *   <li>OP / deOP</li>
 *   <li>permissions</li>
 *   <li>chat shortcode pinning</li>
 *   <li>command target resolution</li>
 *   <li>skins</li>
 *   <li>player identity APIs</li>
 *   <li>player-data routing</li>
 * </ul>
 * A bare username is deliberately NOT treated as globally unique: it may resolve
 * to several identities across providers, in which case resolution is
 * {@link UsernameResolution#AMBIGUOUS} rather than an arbitrary selection.
 */
public final class IdentityResolver {

    /** Outcome of an unqualified username resolution. */
    public enum UsernameResolution { FOUND, AMBIGUOUS, NOT_FOUND }

    /** Result of {@link #resolveByUsername}. */
    public record UsernameResult(
            UsernameResolution status,
            Optional<IdentityRegistry.RegisteredIdentity> identity,
            Set<UUID> candidates) {
    }

    /** A parsed provider-qualified name ({@code provider:username}). */
    public record QualifiedName(String providerQualifier, String username) {
    }

    private final IdentityRegistry registry;
    private final ProviderManager providerManager;
    private final AuthSuiteLogger log;

    public IdentityResolver(IdentityRegistry registry, ProviderManager providerManager, AuthSuiteLogger log) {
        this.registry = registry;
        this.providerManager = providerManager;
        this.log = log;
    }

    /** Resolve by canonical AuthSuite UUID. */
    public Optional<IdentityRegistry.RegisteredIdentity> resolveByUuid(UUID canonicalUuid) {
        return registry.byUuid(canonicalUuid);
    }

    /** Resolve by the immutable {@code provider + account id} pair. */
    public Optional<IdentityRegistry.RegisteredIdentity> resolveByProviderAccount(String providerId, String providerAccountId) {
        return registry.byProviderKey(providerId, providerAccountId);
    }

    /**
     * Resolve an unqualified username:
     * exactly one identity -> {@code FOUND}; several -> {@code AMBIGUOUS};
     * none -> {@code NOT_FOUND}. Never selects arbitrarily.
     */
    public UsernameResult resolveByUsername(String username) {
        if (username == null || username.isBlank()) {
            return new UsernameResult(UsernameResolution.NOT_FOUND, Optional.empty(), Set.of());
        }
        Set<UUID> candidates = registry.usernames(username);
        if (candidates.isEmpty()) {
            return new UsernameResult(UsernameResolution.NOT_FOUND, Optional.empty(), Set.of());
        }
        if (candidates.size() > 1) {
            return new UsernameResult(UsernameResolution.AMBIGUOUS, Optional.empty(), candidates);
        }
        UUID canonical = candidates.iterator().next();
        return new UsernameResult(UsernameResolution.FOUND, registry.byUuid(canonical), candidates);
    }

    /**
     * Resolve a provider-qualified username ({@code MA:cassi__confused}).
     * The qualifier is matched against provider id or shortcode; the result is the
     * identity from that provider whose username matches (or empty).
     */
    public Optional<IdentityRegistry.RegisteredIdentity> resolveByQualifiedUsername(String providerQualifier, String username) {
        if (providerQualifier == null || providerQualifier.isBlank() || username == null || username.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthProvider> provider = providerManager.byId(providerQualifier.trim())
                .or(() -> providerManager.byShortcode(providerQualifier.trim()));
        if (provider.isEmpty()) {
            log.info("Qualified username {}:{} references unknown provider qualifier '{}'",
                    providerQualifier, username, providerQualifier);
            return Optional.empty();
        }
        String providerId = provider.get().providerId();
        IdentityRegistry.RegisteredIdentity match = null;
        int matches = 0;
        for (UUID candidate : registry.usernames(username)) {
            Optional<IdentityRegistry.RegisteredIdentity> registered = registry.byUuid(candidate);
            if (registered.isPresent() && registered.get().identity().providerId().equalsIgnoreCase(providerId)) {
                match = registered.get();
                matches++;
            }
        }
        if (matches == 1) {
            return Optional.of(match);
        }
        if (matches > 1) {
            log.warn("Multiple active identities for {}:{}; treating as unresolved", providerId, username);
        }
        return Optional.empty();
    }

    /**
     * Parse a command/selector identity token:
     * {@code provider:username} (e.g. {@code MA:cassi__confused}) or a bare
     * {@code username}. Returns empty for malformed input.
     */
    public Optional<QualifiedName> parseQualifiedName(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return Optional.of(new QualifiedName(null, trimmed));
        }
        String qualifier = trimmed.substring(0, colon).trim();
        String username = trimmed.substring(colon + 1).trim();
        if (qualifier.isEmpty() || username.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QualifiedName(qualifier, username));
    }

    /**
     * Resolve a command/selector identity token to exactly one identity, or to an
     * explicit failure verdict. Unqualified usernames with several active
     * identities are ambiguous rather than arbitrarily resolved.
     */
    public Optional<IdentityRegistry.RegisteredIdentity> resolveIdentityToken(String token) {
        Optional<QualifiedName> parsed = parseQualifiedName(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        QualifiedName qualified = parsed.get();
        if (qualified.providerQualifier() != null) {
            return resolveByQualifiedUsername(qualified.providerQualifier(), qualified.username());
        }
        return resolveByUsername(qualified.username()).identity();
    }

    /** Human-readable explanation of why an unqualified username did not resolve. */
    public static String describe(UsernameResult result, String username) {
        return switch (result.status()) {
            case FOUND -> username + " resolves to one identity";
            case AMBIGUOUS -> username + " is ambiguous: " + result.candidates().size()
                    + " active identities share this username (qualify it with a provider, e.g. MA:" + username + ")";
            case NOT_FOUND -> "no active identity for '" + username + "'";
        };
    }

    }