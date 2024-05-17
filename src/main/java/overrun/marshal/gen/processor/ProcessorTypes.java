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

package overrun.marshal.gen.processor;

import overrun.marshal.Addressable;
import overrun.marshal.CEnum;
import overrun.marshal.Upcall;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Processor types
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ProcessorTypes {
    private static final Map<Class<?>, ProcessorType> map = new HashMap<>(0);

    private ProcessorTypes() {
    }

    /**
     * Get the processor type from the given class.
     *
     * @param aClass the class
     * @return the processor type
     */
    public static ProcessorType fromClass(Class<?> aClass) {
        if (aClass == boolean.class) return ProcessorType.Value.BOOLEAN;
        if (aClass == char.class) return ProcessorType.Value.CHAR;
        if (aClass == byte.class) return ProcessorType.Value.BYTE;
        if (aClass == short.class) return ProcessorType.Value.SHORT;
        if (aClass == int.class) return ProcessorType.Value.INT;
        if (aClass == long.class) return ProcessorType.Value.LONG;
        if (aClass == float.class) return ProcessorType.Value.FLOAT;
        if (aClass == double.class) return ProcessorType.Value.DOUBLE;
        if (aClass == MemorySegment.class) return ProcessorType.Value.ADDRESS;
        if (aClass == String.class) return ProcessorType.Str.INSTANCE;
        if (SegmentAllocator.class.isAssignableFrom(aClass)) return ProcessorType.Allocator.INSTANCE;
        if (Addressable.class.isAssignableFrom(aClass)) return ProcessorType.Addr.INSTANCE;
        if (CEnum.class.isAssignableFrom(aClass)) return ProcessorType.CEnum.INSTANCE;
        if (Upcall.class.isAssignableFrom(aClass)) return ProcessorType.Upcall.INSTANCE;
        if (aClass.isArray()) return new ProcessorType.Array(fromClass(aClass.componentType()));
        return Objects.requireNonNull(map.get(aClass), STR."Cannot find processor type of \{aClass}");
    }

    /**
     * Registers a processor type for the given class.
     *
     * @param aClass the class
     * @param type   the processor type
     */
    public static void registerClass(Class<?> aClass, ProcessorType type) {
        if (type != null) {
            map.put(aClass, type);
        } else {
            map.remove(aClass);
        }
    }
}
