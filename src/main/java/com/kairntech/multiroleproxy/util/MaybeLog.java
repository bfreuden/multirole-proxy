package com.kairntech.multiroleproxy.util;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MaybeLog {

    public static void maybeLogFinest(Logger logger, Supplier<String> messageSupplier) {
        maybeLog(logger, Level.FINEST, messageSupplier);
    }

    public static void maybeLog(Logger logger, Level level, Supplier<String> messageSupplier) {
        if (logger.isLoggable(level)) logger.log(level, messageSupplier.get());
    }
}
