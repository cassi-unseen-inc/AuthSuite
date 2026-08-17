package net.authsuite.common.config;

import net.authsuite.common.AuthSuiteConstants;
import net.authsuite.common.log.AuthSuiteLogger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and persists {@link AuthSuiteConfig} from the YAML configuration layout.
 * <p>
 * Directory layout (spec §7):
 * <pre>
 * /config/config.yaml
 * /config/&lt;provider_directory&gt;/provider.yaml
 * /config/&lt;provider_directory&gt;/ops.json
 * /config/&lt;provider_directory&gt;/playerdata/
 * </pre>
 * Parsing uses YAML's safe constructor: no arbitrary object instantiation.
 */
public final class ConfigLoader {

    private final AuthSuiteLogger log;

    public ConfigLoader(AuthSuiteLogger log) {
        this.log = log;
    }

    private Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(loader), new Representer(options), options);
    }

    /** Load config.yaml. Missing file yields defaults. */
    public AuthSuiteConfig load(Path configFile) {
        AuthSuiteConfig config = new AuthSuiteConfig();
        config.setBaseConfigDir(configFile.getParent());
        if (configFile == null || !Files.exists(configFile)) {
            log.warn("config.yaml not found at {}; using defaults", configFile);
            return config;
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = createYaml();
            Object parsed = yaml.load(in);
            if (parsed instanceof Map<?, ?> map) {
                apply(config, map);
            }
        } catch (IOException e) {
            log.error("Failed to read config.yaml: " + e.getMessage(), e);
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private void apply(AuthSuiteConfig config, Map<?, ?> map) {
        Object onlineMode = map.get("online-mode");
        if (onlineMode instanceof Boolean b) {
            config.setOnlineMode(b);
        }
        Object enforce = map.get("enforce-online-mode");
        if (enforce instanceof Boolean b) {
            config.setEnforceOnlineMode(b);
        }
        Object priority = map.get("priority");
        if (priority instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s) {
                    out.add(s);
                }
            }
            config.setPriority(out);
        }
        Object policy = map.get("client-preference-policy");
        if (policy instanceof String s) {
            try {
                config.setClientPreferencePolicy(AuthSuiteConfig.ClientPreferencePolicy.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown client-preference-policy '{}'; using default", s);
            }
        }
        Object providers = map.get("providers");
        if (providers instanceof Map<?, ?> providerMap) {
            List<ProviderConfig> out = new ArrayList<>();
            for (Map.Entry<?, ?> entry : providerMap.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key instanceof String id && value instanceof Map<?, ?> fields) {
                    out.add(providerFromMap(id, fields));
                }
            }
            config.setProviders(out);
        }
    }

    @SuppressWarnings("unchecked")
    private ProviderConfig providerFromMap(String id, Map<?, ?> fields) {
        ProviderConfig p = ProviderConfig.of(
                id,
                asString(fields.get("shortcode"), inferShortcode(id)),
                asString(fields.get("domain"), id + ".example.invalid"),
                asString(fields.get("check_url"), ""),
                asString(fields.get("profiles_url"), ""),
                asString(fields.get("property_url"), ""));
        Object enabled = fields.get("enabled");
        if (enabled instanceof Boolean b) {
            p.setEnabled(b);
        }
        Object priority = fields.get("priority");
        if (priority instanceof Number n) {
            p.setPriority(n.intValue());
        }
        Object sendIp = fields.get("send_ip");
        if (sendIp instanceof Boolean b) {
            p.setSendIp(b);
        }
        Object verbose = fields.get("verbose");
        if (verbose instanceof Boolean b) {
            p.setVerboseLogging(b);
        }
        return p;
    }

    private static String asString(Object o, String fallback) {
        return o instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static String inferShortcode(String id) {
        return switch (id) {
            case AuthSuiteConstants.PROVIDER_MICROSOFT -> "MA";
            case AuthSuiteConstants.PROVIDER_LITTLESKINS -> "LS";
            case AuthSuiteConstants.PROVIDER_ELYBY -> "EB";
            default -> id.substring(0, Math.min(2, id.length())).toUpperCase();
        };
    }

    /** Writes a default config.yaml if absent, including the built-in providers. */
    public void writeDefaultsIfAbsent(Path configFile) throws IOException {
        if (Files.exists(configFile)) {
            return;
        }
        Files.createDirectories(configFile.getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("online-mode", true);
        root.put("enforce-online-mode", true);
        root.put("priority", List.of("MA", "LS", "EB"));
        root.put("client-preference-policy", "ALLOW_FALLTHROUGH");

        Map<String, Object> providers = new LinkedHashMap<>();
        providers.put(AuthSuiteConstants.PROVIDER_MICROSOFT, providerDefaults("MA",
                "sessionserver.mojang.com",
                "https://sessionserver.mojang.com/session/minecraft/hasJoined",
                "https://api.mojang.com/profiles/minecraft",
                ""));
        providers.put(AuthSuiteConstants.PROVIDER_LITTLESKINS, providerDefaults("LS",
                "littleskin.cn",
                "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/hasJoined",
                "https://littleskin.cn/api/yggdrasil/api/profiles/minecraft",
                "https://littleskin.cn/api/yggdrasil/textures/{0}"));
        providers.put(AuthSuiteConstants.PROVIDER_ELYBY, providerDefaults("EB",
                "ely.by",
                "https://authserver.ely.by/session/hasJoined",
                "https://authserver.ely.by/api/profiles/minecraft",
                "https://authserver.ely.by/api/textures/{0}"));
        root.put("providers", providers);

        try (var writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            createYaml().dump(root, writer);
        }
        log.info("Wrote default config to {}", configFile);
    }

    private Map<String, Object> providerDefaults(String shortcode, String domain, String checkUrl, String profilesUrl, String propertyUrl) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("shortcode", shortcode);
        p.put("domain", domain);
        p.put("enabled", false);
        p.put("priority", 50);
        p.put("send_ip", false);
        p.put("verbose", true);
        p.put("check_url", checkUrl);
        p.put("profiles_url", profilesUrl);
        p.put("property_url", propertyUrl);
        return p;
    }
}