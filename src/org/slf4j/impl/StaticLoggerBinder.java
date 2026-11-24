package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * Silent SLF4J binding.
 * jSSC bundles slf4j-api but no logging backend, so SLF4J prints a
 * "Failed to load class org.slf4j.impl.StaticLoggerBinder" warning at start-up.
 * This class is the binding SLF4J looks for; it discards all log output.
 */
public final class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    /** Checked by slf4j-api for compatibility. */
    public static String REQUESTED_API_VERSION = "1.6.99";

    private final ILoggerFactory factory = new NOPLoggerFactory();

    private StaticLoggerBinder() {
    }

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return NOPLoggerFactory.class.getName();
    }
}
