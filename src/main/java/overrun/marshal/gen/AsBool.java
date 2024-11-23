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

package overrun.marshal.gen;

import java.util.Locale;

/**
 * Primitive types that are convertible with {@code boolean}.
 *
 * @author squid233
 * @see Convert
 * @since 0.1.0
 */
public enum AsBool {
    /**
     * {@code char} type
     */
    CHAR(char.class),
    /**
     * {@code byte} type
     */
    BYTE(byte.class),
    /**
     * {@code short} type
     */
    SHORT(short.class),
    /**
     * {@code int} type
     */
    INT(int.class),
    /**
     * {@code long} type
     */
    LONG(long.class),
    /**
     * {@code float} type
     */
    FLOAT(float.class),
    /**
     * {@code double} type
     */
    DOUBLE(double.class),
    ;

    private final Class<?> javaClass;

    AsBool(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    /// {@return the class of this type}
    public Class<?> javaClass() {
        return javaClass;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
