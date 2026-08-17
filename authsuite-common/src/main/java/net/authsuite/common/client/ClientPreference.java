package net.authsuite.common.client;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the provider the client's launcher authenticated against, so the server
 * can prefer the matching provider on reconnect (advisory routing hint only).
 * <p>
 * Mirrors authlib-injector's own config resolution so the detection is
 * authoritative regardless of launcher: the {@code authlibinjector.baseUrl} system
 * property, the {@code -javaagent:} argument (base URL or config path), and the
 * {@code authlib-injector.{yaml,json,properties}} config files in the working
 * directory. The detected host is sent to the server, which matches it against the
 * provider {@code domain} / {@code check_url} host.
 */
public final class ClientPreference {

    private static final Pattern BASE_URL_IN_FILE = Pattern.compile(
            "(?i)baseUrl\\s*[:=]\\s*[\"']?\\s*(https?://[^\\s\"',}]+)");

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
        if (override != null && !override.trim().isEmpty()) {
            return override.trim();
        }
        String host = hostFromUrl(System.getProperty("authlibinjector.baseUrl"));
        if (host != null) {
            return host;
        }
        host = hostFromAgentArg();
        if (host != null) {
            return host;
        }
        host = hostFromConfigFile();
        if (host != null) {
            return host;
        }
        return "";
    }

    /** Parse the {@code -javaagent:authlib-injector.jar=<baseUrl|configPath>} argument. */
    private static String hostFromAgentArg() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            if (arg == null || !arg.startsWith("-javaagent:")) {
                continue;
            }
            String spec = arg.substring("-javaagent:".length());
            int eq = spec.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String path = spec.substring(0, eq);
            String options = spec.substring(eq + 1).trim();
            if (!path.toLowerCase().contains("authlib-injector")) {
                continue;
            }
            String host = hostFromUrl(options);
            if (host != null) {
                return host;
            }
            host = hostFromConfigPath(options);
            if (host != null) {
                return host;
            }
        }
        return null;
    }

    /** Read baseUrl from authlib-injector config files in the working directory. */
    private static String hostFromConfigFile() {
        String configured = System.getProperty("authlibinjector.config");
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.trim().isEmpty()) {
            candidates.add(Paths.get(configured.trim()));
        }
        String cwd = System.getProperty("user.dir");
        if (cwd != null) {
            candidates.add(Paths.get(cwd, "authlib-injector.yaml"));
            candidates.add(Paths.get(cwd, "authlib-injector.yml"));
            candidates.add(Paths.get(cwd, "authlib-injector.json"));
            candidates.add(Paths.get(cwd, "authlib-injector.properties"));
        }
        for (Path p : candidates) {
            String host = hostFromConfigPath(p.toString());
            if (host != null) {
                return host;
            }
        }
        return null;
    }

    private static String hostFromConfigPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        Path p = Paths.get(path.trim());
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Matcher m = BASE_URL_IN_FILE.matcher(content);
            if (m.find()) {
                return hostFromUrl(m.group(1));
            }
        } catch (IOException ignored) {
            // try next candidate
        }
        return null;
    }

    private static String hostFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
                return uri.getHost().toLowerCase();
            }
        } catch (URISyntaxException ignored) {
            // not a URL
        }
        return null;
    }
}
