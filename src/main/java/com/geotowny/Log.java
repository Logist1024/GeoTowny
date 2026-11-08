package com.geotowny;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 日志适配层。
 * <p>保留与旧项目一致的 {@code {}} 占位符语义，降低 P2 搬迁时的改动量。
 */
public final class Log {

    private static volatile Logger logger = Logger.getLogger("GeoTowny");

    private Log() {
    }

    public static void init(Logger pluginLogger) {
        if (pluginLogger != null) {
            logger = pluginLogger;
        }
    }

    public static void info(String message, Object... args) {
        logger.log(Level.INFO, format(message, args));
    }

    public static void warn(String message, Object... args) {
        logger.log(Level.WARNING, format(message, args));
    }

    public static void error(String message, Object... args) {
        logger.log(Level.SEVERE, format(message, args));
    }

    public static void error(String message, Throwable throwable, Object... args) {
        logger.log(Level.SEVERE, format(message, args), throwable);
    }

    public static void debug(String message, Object... args) {
        logger.log(Level.FINE, format(message, args));
    }

    private static String format(String message, Object[] args) {
        if (message == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16);
        int argIndex = 0;
        int cursor = 0;
        while (cursor < message.length()) {
            int placeholder = message.indexOf("{}", cursor);
            if (placeholder < 0 || argIndex >= args.length) {
                out.append(message, cursor, message.length());
                break;
            }
            out.append(message, cursor, placeholder);
            out.append(args[argIndex++] == null ? "null" : args[argIndex - 1]);
            cursor = placeholder + 2;
        }
        return out.toString();
    }
}