package net.authsuite.common.provider;

import net.authsuite.common.config.ShortcodeRegistry;
import net.authsuite.common.log.AuthSuiteLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of active {@link AuthProvider}s plus shortcode/index lookups.
 * Provider identity can never be created dynamically from client input: a provider
 * must already be registered and configured here before any attempt can use it.
 */
public final class ProviderManager {

    private final Map<String, AuthProvider> byId = new LinkedHashMap<>();
    private final Map<String, AuthProvider> byShortcode = new LinkedHashMap<>();
    private final List<String> priorityChain = new ArrayList<>();
    private final ShortcodeRegistry shortcodes;
    private final AuthSuiteLogger log;

    public ProviderManager(ShortcodeRegistry shortcodes, AuthSuiteLogger log) {
        this.shortcodes = shortcodes;
        this.log = log;
    }

    public void register(AuthProvider provider, int priority) {
        if (provider == null || byId.containsKey(provider.providerId())) {
            return;
        }
        byId.put(provider.providerId(), provider);
        byShortcode.put(normalize(provider.shortcode()), provider);
    }

    public Optional<AuthProvider> byId(String providerId) {
        return Optional.ofNullable(byId.get(providerId));
    }

    public Optional<AuthProvider> byShortcode(String shortcode) {
        return Optional.ofNullable(byShortcode.get(normalize(shortcode)));
    }

    /** Matches a provider by host, using its configured domain or check_url host. */
    public Optional<AuthProvider> byHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return Optional.empty();
        }
        String wanted = normalizeHost(host);
        for (AuthProvider p : byId.values()) {
            if (matchesHost(p, wanted)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Ordered list of currently enabled providers. */
    public List<AuthProvider> enabled() {
        List<AuthProvider> out = new ArrayList<>();
        for (AuthProvider p : byId.values()) {
            if (p.isEnabled()) {
                out.add(p);
            }
        }
        return out;
    }

    public List<String> priorityChain() {
        return Collections.unmodifiableList(priorityChain);
    }

    /** Sets resolution order using provider shortcodes; unknown entries are skipped. */
    public void setPriorityChain(List<String> shortcodes) {
        priorityChain.clear();
        if (shortcodes == null) {
            return;
        }
        for (String sc : shortcodes) {
            if (byShortcode.containsKey(normalize(sc))) {
                priorityChain.add(normalize(sc));
            } else {
                log.warn("Priority chain references unknown provider shortcode '{}'; ignoring", sc);
            }
        }
    }

    public Integer priorityOf(AuthProvider provider) {
        for (int i = 0; i < priorityChain.size(); i++) {
            if (priorityChain.get(i).equals(normalize(provider.shortcode()))) {
                return i;
            }
        }
        return null;
    }

    private String normalize(String shortcode) {
        return shortcode.trim().toUpperCase();
    }

    private static String normalizeHost(String host) {
        return host.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*$", "");
    }

    private static boolean matchesHost(AuthProvider p, String wanted) {
        if (p.domain() != null && normalizeHost(p.domain()).equals(wanted)) {
            return true;
        }
        if (p instanceof AuthlibProvider) {
            AuthlibProvider ap = (AuthlibProvider) p;
            if (ap.config() != null && ap.config().checkUrl() != null
                    && !ap.config().checkUrl().trim().isEmpty() && normalizeHost(ap.config().checkUrl()).equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}