package net.authsuite.fabric;

import net.authsuite.common.log.AuthSuiteLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SLF4J-backed {@link AuthSuiteLogger}. */
public final class AuthSuiteLoggerFactory {

    private AuthSuiteLoggerFactory() {
    }

    public static AuthSuiteLogger get() {
        Logger delegate = LoggerFactory.getLogger("authsuite");
        return new AuthSuiteLogger() {
            @Override
            public void debug(String message) {
                delegate.debug(message);
            }

            @Override
            public void debug(String message, Object... args) {
                delegate.debug(message, args);
            }

            @Override
            public void info(String message) {
                delegate.info(message);
            }

            @Override
            public void info(String message, Object... args) {
                delegate.info(message, args);
            }

            @Override
            public void warn(String message) {
                delegate.warn(message);
            }

            @Override
            public void warn(String message, Object... args) {
                delegate.warn(message, args);
            }

            @Override
            public void error(String message) {
                delegate.error(message);
            }

            @Override
            public void error(String message, Throwable cause) {
                delegate.error(message, cause);
            }
        };
    }
}