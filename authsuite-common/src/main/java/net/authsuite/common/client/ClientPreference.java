package net.authsuite.common.client;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Detects the provider the client's launcher authenticated against, so the server
 * can prefer the matching provider on reconnect (advisory routing hint only).
 * <p>
 * The launcher (authlib-injector) sets {@code authlibinjector.baseUrl} as a JVM
 * system property; we extract its host and send it to the server, which matches
 * it against the provider {@code domain} / {@code check_url} host.
 */
public final class ClientPreference {

    private ClientPreference() {
    }

    /**
     * Preferred provider hint for the current client session.
     *
     * @return the provider shortcode/id if explicitly overridden, otherwise the
     *         authlib-injector host, or an empty string when unknown.
     */
    public static String detect() {
        String override = System.getProperty("authsuite.preferredProvider");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String baseUrl = System.getProperty("authlibinjector.baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(baseUrl.trim());
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost().toLowerCase();
            }
        } catch (URISyntaxException ignored) {
            // fall through to empty hint
        }
        return "";
    }
}
