/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package overrun.marshal;

import overrun.marshal.gen.Sized;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Configurations
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Configurations {
    /**
     * Enable checks.
     * <p>
     * The default value is {@code true}.
     */
    public static final Entry<Boolean> CHECKS = new Entry<>(() -> true);
    /**
     * Check the size of a fixed size array argument.
     * <p>
     * The default value is {@code true}.
     *
     * @see Sized
     */
    public static final Entry<Boolean> CHECK_ARRAY_SIZE = new Entry<>(() -> true);
    /**
     * Enable debug messages and prints to {@link #apiLogger()}.
     * <p>
     * The default value is {@code false}.
     */
    public static final Entry<Boolean> DEBUG = new Entry<>(() -> false);
    /**
     * Enable using debug memory stack.
     * <p>
     * The default value is {@code false}.
     */
    public static final Entry<Boolean> DEBUG_STACK = new Entry<>(() -> false);
    /**
     * The default stack size in KiB of {@link MemoryStack}.
     * <p>
     * The default value is {@code 64}.
     */
    public static final Entry<Long> STACK_SIZE = new Entry<>(() -> 64L);
    /**
     * The default stack frames of {@link MemoryStack}.
     * <p>
     * The default value is {@code 8}.
     */
    public static final Entry<Integer> STACK_FRAMES = new Entry<>(() -> 8);
    private static Consumer<String> apiLogger = System.err::println;

    private Configurations() {
        //no instance
    }

    /**
     * Sets the API logger.
     *
     * @param logger the logger
     */
    public static void setApiLogger(Consumer<String> logger) {
        apiLogger = Objects.requireNonNullElseGet(logger, () -> System.err::println);
    }

    /**
     * {@return the API logger}
     */
    public static Consumer<String> apiLogger() {
        return apiLogger;
    }

    /**
     * Logs the given message.
     *
     * @param log the message
     */
    public static void apiLog(String log) {
        apiLogger().accept(log);
    }

    /**
     * A check entry
     *
     * @param <T> the type of the value
     * @author squid233
     * @since 0.1.0
     */
    public static final class Entry<T> {
        private final Supplier<T> supplier;
        private T value;

        private Entry(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        /**
         * {@return the value of this entry}
         */
        public T get() {
            if (value == null) {
                value = supplier.get();
            }
            return value;
        }
    }
}
