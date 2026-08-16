package net.authsuite.common.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Top-level mod configuration modeled on {@code config/config.yaml}.
 * <p>
 * Security-relevant invariants enforced here:
 * <ul>
 *   <li>{@code server.online-mode} must be {@code true} (spec §1).</li>
 *   <li>A default priority chain of provider shortcodes selects resolution order.</li>
 *   <li>Shortcodes persist; provider configuration is never created from client input.</li>
 * </ul>
 */
public final class AuthSuiteConfig {

    private boolean onlineMode = true;
    private boolean enforceOnlineMode = true;
    private List<String> priority = new ArrayList<>(Arrays.asList("MA", "LS", "EB"));
    private ClientPreferencePolicy clientPreferencePolicy = ClientPreferencePolicy.ALLOW_FALLTHROUGH;
    private List<ProviderConfig> providers = new ArrayList<>();
    private Path baseConfigDir;
    private long authTimeoutMs = 15_000L;

    public enum ClientPreferencePolicy {
        /** Preferred provider attempted first; fallthrough allowed on classified failures. */
        ALLOW_FALLTHROUGH,
        /** Preferred provider attempted first; hard stop on rejection. */
        STRICT,
        /** Ignore client preference entirely. */
        IGNORE
    }

    public boolean onlineMode() {
        return onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean enforceOnlineMode() {
        return enforceOnlineMode;
    }

    public void setEnforceOnlineMode(boolean enforceOnlineMode) {
        this.enforceOnlineMode = enforceOnlineMode;
    }

    public List<String> priority() {
        return priority;
    }

    public void setPriority(List<String> priority) {
        this.priority = priority == null ? new ArrayList<>() : priority;
    }

    public ClientPreferencePolicy clientPreferencePolicy() {
        return clientPreferencePolicy;
    }

    public void setClientPreferencePolicy(ClientPreferencePolicy clientPreferencePolicy) {
        this.clientPreferencePolicy = clientPreferencePolicy == null
                ? ClientPreferencePolicy.ALLOW_FALLTHROUGH
                : clientPreferencePolicy;
    }

    public List<ProviderConfig> providers() {
        return providers;
    }

    public void setProviders(List<ProviderConfig> providers) {
        this.providers = providers == null ? new ArrayList<>() : providers;
    }

    public void setBaseConfigDir(Path baseConfigDir) {
        this.baseConfigDir = baseConfigDir;
    }

    public Path baseConfigDir() {
        return baseConfigDir;
    }

    public long authTimeoutMs() {
        return authTimeoutMs;
    }

    public void setAuthTimeoutMs(long authTimeoutMs) {
        this.authTimeoutMs = authTimeoutMs;
    }
}