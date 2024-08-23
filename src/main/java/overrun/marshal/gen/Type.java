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

import overrun.marshal.gen.processor.ProcessorType;

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
    CHAR(CD_char, ValueLayout.JAVA_CHAR, ProcessorType.Value.CHAR),
    /**
     * {@code byte} type
     */
    BYTE(CD_byte, ValueLayout.JAVA_BYTE, ProcessorType.Value.BYTE),
    /**
     * {@code short} type
     */
    SHORT(CD_short, ValueLayout.JAVA_SHORT, ProcessorType.Value.SHORT),
    /**
     * {@code int} type
     */
    INT(CD_int, ValueLayout.JAVA_INT, ProcessorType.Value.INT),
    /**
     * {@code long} type
     */
    LONG(CD_long, ValueLayout.JAVA_LONG, ProcessorType.Value.LONG),
    /**
     * {@code float} type
     */
    FLOAT(CD_float, ValueLayout.JAVA_FLOAT, ProcessorType.Value.FLOAT),
    /**
     * {@code double} type
     */
    DOUBLE(CD_double, ValueLayout.JAVA_DOUBLE, ProcessorType.Value.DOUBLE);

    private final ClassDesc classDesc;
    private final ValueLayout layout;
    private final ProcessorType.Value processorType;

    Type(ClassDesc classDesc, ValueLayout layout, ProcessorType.Value processorType) {
        this.classDesc = classDesc;
        this.layout = layout;
        this.processorType = processorType;
    }

    /**
     * {@return the class desc of this type}
     */
    public ClassDesc classDesc() {
        return classDesc;
    }

    /**
     * {@return the layout of this type}
     */
    public ValueLayout layout() {
        return layout;
    }

    /**
     * {@return the processor type of this type}
     */
    public ProcessorType.Value processorType() {
        return processorType;
    }
}
