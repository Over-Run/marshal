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

import org.jetbrains.annotations.Unmodifiable;
import overrun.marshal.gen.Skip;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Map;

/**
 * This interface provides access to function descriptors and method handles
 * for each function that is loaded by {@link Downcall}.
 *
 * @author squid233
 * @see Downcall
 * @since 0.1.0
 */
public interface DirectAccess {
    /**
     * {@return an unmodifiable map of the function descriptors for each method}
     */
    @Skip
    @Unmodifiable
    Map<String, FunctionDescriptor> functionDescriptors();

    /**
     * {@return an unmodifiable map of the method handles for each method}
     */
    @Skip
    @Unmodifiable
    Map<String, MethodHandle> methodHandles();

    /**
     * Gets the method handle with the given entrypoint name.
     *
     * @param entrypoint the entrypoint name
     * @return the method handle
     */
    @Skip
    default MethodHandle methodHandle(String entrypoint) {
        return methodHandles().get(entrypoint);
    }

    /**
     * {@return the symbol lookup of this library}
     */
    @Skip
    SymbolLookup symbolLookup();
}
