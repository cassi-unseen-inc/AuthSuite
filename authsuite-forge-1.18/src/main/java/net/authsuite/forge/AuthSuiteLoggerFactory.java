package net.authsuite.forge;

import net.authsuite.common.log.AuthSuiteLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adapts the platform Log4j backend to the common {@link AuthSuiteLogger}
 * abstraction. Implements the documented Forge logging contract: no secret
 * material is ever included (see LogSanitizer usage in core).
 */
public final class AuthSuiteLoggerFactory {

    private AuthSuiteLoggerFactory() {
    }

    public static AuthSuiteLogger get() {
        Logger log4j = LogManager.getLogger("authsuite");
        return new AuthSuiteLogger() {
            @Override
            public void debug(String message) {
                log4j.debug(message);
            }

            @Override
            public void debug(String message, Object... args) {
                log4j.debug(message, args);
            }

            @Override
            public void info(String message) {
                log4j.info(message);
            }

            @Override
            public void info(String message, Object... args) {
                log4j.info(message, args);
            }

            @Override
            public void warn(String message) {
                log4j.warn(message);
            }

            @Override
            public void warn(String message, Object... args) {
                log4j.warn(message, args);
            }

            @Override
            public void error(String message) {
                log4j.error(message);
            }

            @Override
            public void error(String message, Throwable cause) {
                log4j.error(message, cause);
            }
        };
    }
}