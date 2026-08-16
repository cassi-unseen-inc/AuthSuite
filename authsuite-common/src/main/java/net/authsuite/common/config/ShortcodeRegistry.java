package net.authsuite.common.config;

import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.ProviderId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps display shortcode aliases (MA, LS, EB) to canonical provider ids.
 * <p>
 * Lifecycle (spec §6): discovered shortcodes are checked against configuration; if
 * absent a shortcode is generated and persisted; it is never auto-regenerated once
 * present. Client-provided shortcodes are preferences only and never create or
 * modify mappings.
 */
public final class ShortcodeRegistry {

    private final Map<String, ProviderId> byShortcode = new LinkedHashMap<>();
    private final Map<String, ProviderId> byId = new LinkedHashMap<>();
    private final AuthSuiteLogger log;

    public ShortcodeRegistry(AuthSuiteLogger log) {
        this.log = log;
    }

    public void register(ProviderId provider) {
        if (provider == null) {
            return;
        }
        ProviderId existingShort = byShortcode.get(provider.shortcode());
        ProviderId existingId = byId.get(provider.providerId());
        if (existingShort != null && !existingShort.equals(provider)) {
            log.warn("Shortcode collision: '{}' already maps to {}, ignoring {}", provider.shortcode(), existingShort, provider);
            return;
        }
        if (existingId != null && !existingId.equals(provider)) {
            log.warn("Provider id collision: '{}' already registered as {}, ignoring re-registration", provider.providerId(), existingId);
            return;
        }
        byShortcode.put(provider.shortcode(), provider);
        byId.put(provider.providerId(), provider);
    }

    public Optional<ProviderId> byShortcode(String shortcode) {
        return shortcode == null ? Optional.empty() : Optional.ofNullable(byShortcode.get(normalize(shortcode)));
    }

    public Optional<ProviderId> byId(String providerId) {
        return providerId == null ? Optional.empty() : Optional.ofNullable(byId.get(providerId));
    }

    public boolean isKnownShortcode(String shortcode) {
        return byShortcode(shortcode).isPresent();
    }

    public Map<String, ProviderId> all() {
        return Map.copyOf(byId);
    }

    private String normalize(String shortcode) {
        return shortcode.trim().toUpperCase();
    }
}