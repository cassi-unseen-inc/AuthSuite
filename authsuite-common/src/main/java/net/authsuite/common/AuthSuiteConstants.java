package net.authsuite.common;

/**
 * Global identity and metadata constants for the AuthSuite mod.
 * <p>
 * Everything here is deliberately platform-agnostic so the value of every constant
 * is identical regardless of whether the mod runs on NeoForge, Fabric or Forge.
 */
public final class AuthSuiteConstants {

    /** Mod id / channel namespace. */
    public static final String MOD_ID = "authsuite";

    /** Namespace prefix used by packet channels and tags. */
    public static final String NS = "authsuite";

    /** Shortcode prefix: AS = AuthSuite. */
    public static final String SHORTCODE_PREFIX = "AS";

    /** In-identity provider namespace prefix (mirrors the "HybridAuth:" scheme from the original spec). */
    public static final String IDENTITY_NAMESPACE = "AuthSuite:";

    /** Canonical identity delimiter between namespace, provider and account id. */
    public static final String IDENTITY_DELIMITER = ":";

    /** Maximum number of concurrent provider auth operations server-wide. */
    public static final int MAX_CONCURRENT_AUTHS = 32;

    /** Authentication operation timeout. */
    public static final long AUTH_TIMEOUT_MS = 15_000L;

    /** Default provider priority chain used when config does not specify one. */
    public static final String[] DEFAULT_PRIORITY = {"MA", "LS", "EB"};

    /** Built-in provider ids. */
    public static final String PROVIDER_MICROSOFT = "microsoft";
    public static final String PROVIDER_LITTLESKINS = "littleskins";
    public static final String PROVIDER_ELYBY = "elyby";

    private AuthSuiteConstants() {
    }
}