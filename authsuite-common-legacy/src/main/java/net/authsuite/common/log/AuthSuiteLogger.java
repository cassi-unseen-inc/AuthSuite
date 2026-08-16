package net.authsuite.common.log;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Minimal abstraction over the platform logging backend.
 * <p>
 * Providers and core logic never touch a Minecraft logger directly; every loader
 * module adapts this interface to its own logging facility (Log4j on NeoForge/Forge,
 * SLF4J on Fabric). Records {@link #debug}, {@link #info}, {@link #warn} and
 * {@link #error} levels and always forbids secret material (see sanitization rules
 * in {@link net.authsuite.common.security.LogSanitizer}).
 */
public interface AuthSuiteLogger {

    void debug(String message);

    void debug(String message, Object... args);

    void info(String message);

    void info(String message, Object... args);

    void warn(String message);

    void warn(String message, Object... args);

    void error(String message);

    void error(String message, Throwable cause);

    default void debug(Supplier<String> message) {
        debug(message.get());
    }

    static AuthSuiteLogger noop() {
        return new AuthSuiteLogger() {
            @Override
            public void debug(String message) {
            }

            @Override
            public void debug(String message, Object... args) {
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void info(String message, Object... args) {
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void warn(String message, Object... args) {
            }

            @Override
            public void error(String message) {
            }

            @Override
            public void error(String message, Throwable cause) {
            }
        };
    }
}