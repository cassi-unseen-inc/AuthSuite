package net.authsuite.common.provider;

import java.util.concurrent.CompletableFuture;

/**
 * The authentication-provider abstraction. This is the seam where third-party
 * identity providers (Microsoft, LittleSkins, Ely.by) are decoupled from the rest
 * of the mod.
 * <p>
 * Contract:
 * <ul>
 *   <li>All network operations return non-blocking {@link CompletableFuture}s.</li>
 *   <li>{@link #authenticate} MUST return a provider-validated
 *       {@link AuthenticatedProfile} or a classified {@link ProviderFailure}.</li>
 *   <li>A provider may only create identities after independent validation; a
 *       client-provided account id is never accepted as proof (spec §2, §3).</li>
 * </ul>
 *
 * @see AuthenticatedProfile
 * @see ProviderFailure
 */
public interface AuthProvider {

    /** Stable canonical provider id, e.g. {@code "microsoft"}. */
    String providerId();

    /** Display shortcode, e.g. {@code "MA"}, {@code "LS"}, {@code "EB"}. */
    String shortcode();

    /** Allowed domain for skin/cape resource URLs produced by this provider. */
    String domain();

    /**
     * Independently authenticate the attempt and, on success, return a
     * provider-visible {@link AuthenticatedProfile}. Must never block the caller
     * synchronously for network I/O.
     */
    CompletableFuture<AuthenticatedProfile> authenticate(AuthAttempt attempt);

    /**
     * Resolve a player's provider account by username (used for command targeting
     * and identity resolution). Never treats the argument as an authenticated
     * assertion.
     */
    CompletableFuture<ResolverResult> resolvePlayer(String username);

    /**
     * Validate an existing provider session. Used on reconnect and before
     * authorization boundaries.
     */
    CompletableFuture<SessionValidity> validateSession(String providerAccountId);

    /** Release any provider-side session resources (e.g. cached tokens) on logout. */
    CompletableFuture<Void> disconnect(String providerAccountId);

    /** Whether this provider is enabled in the current server configuration. */
    boolean isEnabled();

    /** Result of {@link #resolvePlayer}: provider account resume point. */
    record ResolverResult(String providerAccountId, String displayName) {
    }

    /** Result of {@link #validateSession}. */
    enum SessionValidity {
        VALID,
        EXPIRED,
        INVALID
    }
}