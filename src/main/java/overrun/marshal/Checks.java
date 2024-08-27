/*
 * MIT License
 *
 * Copyright (c) 2024 Overrun Organization
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

/**
 * Checks
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Checks {
    private Checks() {
    }

    /**
     * Checks the array size.
     *
     * @param expected the expected size
     * @param actual   the actual size
     */
    public static void checkArraySize(long expected, int actual) {
        if (MarshalConfigs.CHECK_ARRAY_SIZE.get() && expected != actual) {
            throw new IllegalArgumentException("Expected array of size " + expected + ", got " + actual);
        }
    }
}
