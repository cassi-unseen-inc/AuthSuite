package net.authsuite.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Per-provider configuration (provider.yaml) stored inside an isolated provider
 * configuration directory: {@code /config/<provider_directory>/provider.yaml}.
 * <p>
 * Never constructed from client input. Shortcodes and provider ids are admin
 * authored and persisted (spec §6, §7).
 */
public final class ProviderConfig {

    private String id;
    private String shortcode;
    private String domain;
    private String checkUrl;
    private String profilesUrl;
    private String propertyUrl;
    private boolean enabled = true;
    private int priority = 50;

    public String id() {
        return id;
    }

    public String shortcode() {
        return shortcode;
    }

    public String domain() {
        return domain;
    }

    public String checkUrl() {
        return checkUrl;
    }

    public String profilesUrl() {
        return profilesUrl;
    }

    public String propertyUrl() {
        return propertyUrl;
    }

    public boolean enabled() {
        return enabled;
    }

    public int priority() {
        return priority;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setShortcode(String shortcode) {
        this.shortcode = shortcode;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setCheckUrl(String checkUrl) {
        this.checkUrl = checkUrl;
    }

    public void setProfilesUrl(String profilesUrl) {
        this.profilesUrl = profilesUrl;
    }

    public void setPropertyUrl(String propertyUrl) {
        this.propertyUrl = propertyUrl;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void validate() {
        Objects.requireNonNull(id, "provider id");
        Objects.requireNonNull(shortcode, "shortcode");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(checkUrl, "checkUrl");
    }

    public static ProviderConfig of(String id, String shortcode, String domain, String checkUrl, String profilesUrl, String propertyUrl) {
        ProviderConfig c = new ProviderConfig();
        c.id = id;
        c.shortcode = shortcode;
        c.domain = domain;
        c.checkUrl = checkUrl;
        c.profilesUrl = profilesUrl;
        c.propertyUrl = propertyUrl;
        return c;
    }
}