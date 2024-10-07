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
import java.util.Map;

/**
 * The data for {@link DirectAccess}.
 *
 * @param functionDescriptors an unmodifiable map of the function descriptors for each method
 * @param methodHandles       an unmodifiable map of the method handles for each method
 * @param symbolLookup        the symbol lookup of this library
 * @author squid233
 * @since 0.1.0
 */
public record DirectAccessData(
    Map<String, FunctionDescriptor> functionDescriptors,
    Map<String, MethodHandle> methodHandles,
    SymbolLookup symbolLookup
) {
}
