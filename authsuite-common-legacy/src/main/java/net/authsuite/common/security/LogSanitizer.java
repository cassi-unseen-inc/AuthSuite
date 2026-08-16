package net.authsuite.common.security;

import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Central place where log output is made safe. Secrets, tokens and passwords must
 * never appear in logs. Logs record only provider ids, sanitized error codes and
 * session UUIDs (see the mod security contract).
 */
public final class LogSanitizer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(access[_-]?token|client[_-]?secret|password|refresh[_-]?token|session[_-]?key|secret|apikey|api[_-]?key)\\s*[=:]\\s*[A-Za-z0-9_\\-./+=]{6,}");

    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "(?i)(authorization|bearer)\\s*:\\s*[A-Za-z0-9_\\-./+=]{6,}");

    private LogSanitizer() {
    }

    /** Sanitize a log line: replace all secret payloads with a fixed redaction marker. */
    public static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String out = message;
        out = TOKEN_PATTERN.matcher(out).replaceAll("$1=<redacted>");
        out = AUTHORIZATION_HEADER.matcher(out).replaceAll("$1:<redacted>");
        return out;
    }

    /** Sanitize a value for a log line only if it looks like a secret; otherwise pass through. */
    public static String sanitizeValue(String label, String value) {
        if (value == null) {
            return label + "=<null>";
        }
        boolean secret = TOKEN_PATTERN.matcher(value).matches()
                || TOKEN_PATTERN.matcher(label + "=" + value).matches();
        return secret ? label + "=<redacted>" : label + "=" + value;
    }

    /** UUID formatting for logs is safe and stays visible; it identifies sessions only. */
    public static UUID sanitizeUuid(UUID uuid) {
        return uuid;
    }

    /** Redacts a throwable's message so accidental token embedding can't leak. */
    public static String sanitizeMessage(Throwable throwable) {
        return sanitize(throwable == null ? "null" : throwable.getMessage());
    }
}