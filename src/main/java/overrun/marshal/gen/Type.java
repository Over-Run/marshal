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

import java.lang.constant.ClassDesc;
import java.lang.foreign.ValueLayout;

import static java.lang.constant.ConstantDescs.*;

/**
 * Primitive types that are convertible with {@code boolean}.
 *
 * @author squid233
 * @since 0.1.0
 */
public enum Type {
    /**
     * {@code char} type
     */
    CHAR(CD_char, char.class, ValueLayout.JAVA_CHAR),
    /**
     * {@code byte} type
     */
    BYTE(CD_byte, byte.class, ValueLayout.JAVA_BYTE),
    /**
     * {@code short} type
     */
    SHORT(CD_short, short.class, ValueLayout.JAVA_SHORT),
    /**
     * {@code int} type
     */
    INT(CD_int, int.class, ValueLayout.JAVA_INT),
    /**
     * {@code long} type
     */
    LONG(CD_long, long.class, ValueLayout.JAVA_LONG),
    /**
     * {@code float} type
     */
    FLOAT(CD_float, float.class, ValueLayout.JAVA_FLOAT),
    /**
     * {@code double} type
     */
    DOUBLE(CD_double, double.class, ValueLayout.JAVA_DOUBLE);

    private final ClassDesc classDesc;
    private final Class<?> presentation;
    private final ValueLayout layout;

    Type(ClassDesc classDesc, Class<?> presentation, ValueLayout layout) {
        this.classDesc = classDesc;
        this.presentation = presentation;
        this.layout = layout;
    }

    /**
     * {@return the class desc of this type}
     */
    public ClassDesc classDesc() {
        return classDesc;
    }

    /**
     * {@return the representation class of this type}
     */
    public Class<?> representation() {
        return presentation;
    }

    /**
     * {@return the layout of this type}
     */
    public ValueLayout layout() {
        return layout;
    }
}
