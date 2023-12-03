/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

import java.util.function.Supplier;

/**
 * Checks
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Checks {
    /**
     * Check size of a fixed size array argument. Default value: {@code true}
     *
     * @see FixedSize
     */
    public static final Entry<Boolean> CHECK_ARRAY_SIZE = new Entry<>(() -> true);

    private Checks() {
        //no instance
    }

    /**
     * Checks the array size.
     *
     * @param expected the expected size
     * @param got      the got size
     */
    public static void checkArraySize(int expected, int got) {
        if (CHECK_ARRAY_SIZE.get() && expected != got) {
            throw new IllegalArgumentException("Expected array of size " + expected + ", got " + got);
        }
    }

    /**
     * A check entry
     *
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
