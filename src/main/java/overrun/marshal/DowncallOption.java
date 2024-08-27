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

import org.jetbrains.annotations.Nullable;
import overrun.marshal.internal.DowncallOptions;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Downcall option.
 *
 * @author squid233
 * @since 0.1.0
 */
public sealed interface DowncallOption
    permits DowncallOptions.Descriptors, DowncallOptions.SkipClass, DowncallOptions.TargetClass, DowncallOptions.Transform {
    /**
     * Specifies the target class.
     *
     * @param aClass the target class. use {@code null} for caller class
     * @return the option instance
     */
    static DowncallOption targetClass(@Nullable Class<?> aClass) {
        return new DowncallOptions.TargetClass(aClass);
    }

    /**
     * Specifies the custom function descriptors.
     *
     * @param descriptorMap the custom function descriptors for each method handle
     * @return the option instance
     */
    static DowncallOption descriptors(Map<String, FunctionDescriptor> descriptorMap) {
        Objects.requireNonNull(descriptorMap);
        return new DowncallOptions.Descriptors(descriptorMap);
    }

    /**
     * Specifies the method handle transformer.
     * <p>
     * The transformer will be used when each downcall handle is generated in {@link Downcall}.
     *
     * @param transform the transforming function. the argument of the function might be null
     * @return the option instance
     */
    static DowncallOption transform(UnaryOperator<@Nullable MethodHandle> transform) {
        Objects.requireNonNull(transform);
        return new DowncallOptions.Transform(transform);
    }

    /**
     * Adds a class to skip.
     * Methods {@linkplain Class#getDeclaredMethods() declared} in the added class will be skipped.
     * <p>
     * There might be more than one this option added.
     *
     * @param clazz the class
     * @return the option instance
     */
    static DowncallOption skipClass(Class<?> clazz) {
        Objects.requireNonNull(clazz);
        return new DowncallOptions.SkipClass(clazz);
    }
}
