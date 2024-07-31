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

package overrun.marshal.internal;

import overrun.marshal.DowncallOption;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Downcall options.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallOptions {
    private DowncallOptions() {
    }

    /**
     * specify target class
     *
     * @param aClass the class
     */
    public record TargetClass(Class<?> aClass) implements DowncallOption {
    }

    /**
     * specify custom function descriptors
     *
     * @param descriptorMap the custom function descriptors
     */

    public record Descriptors(Map<String, FunctionDescriptor> descriptorMap) implements DowncallOption {
    }

    /**
     * specify method handle transform
     *
     * @param operator the transform function
     */
    public record Transform(UnaryOperator<MethodHandle> operator) implements DowncallOption {
    }
}
