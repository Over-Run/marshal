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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The data for {@link DirectAccess}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DirectAccessData {
    private final Map<String, FunctionDescriptor> functionDescriptors;
    private final Function<String, MethodHandle> methodHandleGetter;
    private final Map<String, MethodHandle> methodHandles = new HashMap<>();
    private final SymbolLookup symbolLookup;

    DirectAccessData(
        Map<String, FunctionDescriptor> functionDescriptors,
        Function<String, MethodHandle> methodHandleGetter,
        SymbolLookup symbolLookup
    ) {
        this.functionDescriptors = functionDescriptors;
        this.methodHandleGetter = methodHandleGetter;
        this.symbolLookup = symbolLookup;
    }

    /// {@return an unmodifiable map of the function descriptors for each method}
    public Map<String, FunctionDescriptor> functionDescriptors() {
        return functionDescriptors;
    }

    /// Gets or loads a method handle with the given entrypoint.
    ///
    /// @param entrypoint the entrypoint
    /// @return the loaded method handle
    public MethodHandle methodHandle(String entrypoint) {
        return methodHandles.computeIfAbsent(entrypoint, methodHandleGetter);
    }

    /// {@return the symbol lookup of this library}
    public SymbolLookup symbolLookup() {
        return symbolLookup;
    }
}
