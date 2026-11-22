package com.gtceu.compat.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logger utility for the GTCEu Compatibility Layer.
 */
public class GTCEuCompatLogger {

    private static final Logger LOGGER = LogManager.getLogger("GTCEuCompat");

    /**
     * Logs an info message.
     * @param message The message format string.
     * @param args The arguments for the format string.
     */
    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    /**
     * Logs a warning message.
     * @param message The message format string.
     * @param args The arguments for the format string.
     */
    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    /**
     * Logs an error message.
     * @param message The message format string.
     * @param args The arguments for the format string.
     */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    /**
     * Logs a debug message.
     * @param message The message format string.
     * @param args The arguments for the format string.
     */
    public static void debug(String message, Object... args) {
        LOGGER.debug(message, args);
    }

    /**
     * Logs a trace message.
     * @param message The message format string.
     * @param args The arguments for the format string.
     */
    public static void trace(String message, Object... args) {
        LOGGER.trace(message, args);
    }

    /**
     * Logs an error message with an exception.
     * @param message The message format string.
     * @param throwable The exception to log.
     * @param args The arguments for the format string.
     */
    public static void error(String message, Throwable throwable, Object... args) {
        LOGGER.error(message, args, throwable);
    }
}
