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

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.util.Map;

/**
 * Common C memory layouts obtained with {@link Linker#canonicalLayouts()}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class CanonicalLayouts {
    private static final Map<String, MemoryLayout> canonicalLayouts = Linker.nativeLinker().canonicalLayouts();
    /// Specified canonical layouts
    public static final MemoryLayout BOOL = get("bool"),
        CHAR = get("char"),
        SHORT = get("short"),
        INT = get("int"),
        FLOAT = get("float"),
        LONG = get("long"),
        LONG_LONG = get("long long"),
        DOUBLE = get("double"),
        VOID_POINTER = get("void*"),
        SIZE_T = get("size_t"),
        WCHAR_T = get("wchar_t");

    private CanonicalLayouts() {
    }

    /// Gets the layout from {@link Linker#canonicalLayouts()} with the given name.
    ///
    /// @param name the name of the layout
    /// @return the layout
    public static MemoryLayout get(String name) {
        return canonicalLayouts.get(name);
    }
}
