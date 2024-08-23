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

import overrun.marshal.Upcall;
import overrun.marshal.gen.Convert;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructAllocatorSpec;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Processor types
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ProcessorTypes {
    private static final Map<Class<?>, ProcessorType> map = new LinkedHashMap<>();

    static {
        register(void.class, ProcessorType.Void.INSTANCE);
        register(boolean.class, ProcessorType.Value.BOOLEAN);
        register(char.class, ProcessorType.Value.CHAR);
        register(byte.class, ProcessorType.Value.BYTE);
        register(short.class, ProcessorType.Value.SHORT);
        register(int.class, ProcessorType.Value.INT);
        register(long.class, ProcessorType.Value.LONG);
        register(float.class, ProcessorType.Value.FLOAT);
        register(double.class, ProcessorType.Value.DOUBLE);
        register(MemorySegment.class, ProcessorType.Value.ADDRESS);
        register(String.class, ProcessorType.Str.INSTANCE);
        register(SegmentAllocator.class, ProcessorType.Allocator.INSTANCE);
        registerStruct(Struct.class, null);
        register(Upcall.class, ProcessorType.Upcall.INSTANCE);
    }

    private ProcessorTypes() {
    }

    /**
     * Get the processor type from the given class.
     *
     * @param aClass the class
     * @return the processor type
     */
    public static ProcessorType fromClass(Class<?> aClass) {
        if (aClass.isArray()) return new ProcessorType.Array(fromClass(aClass.componentType()));
        if (map.containsKey(aClass)) {
            return map.get(aClass);
        }
        ProcessorType candidate = null;
        for (var entry : map.entrySet()) {
            if (entry.getKey().isAssignableFrom(aClass)) {
                candidate = entry.getValue();
            }
        }
        if (candidate != null) {
            return candidate;
        }
        throw new NoSuchElementException("Cannot find processor type of " + aClass);
    }

    /**
     * Gets the processor type from the given method.
     *
     * @param method the method
     * @return the processor type
     */
    public static ProcessorType fromMethod(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            Convert convert = method.getDeclaredAnnotation(Convert.class);
            if (convert != null) {
                return convert.value();
            }
        }
        return fromClass(returnType);
    }

    /**
     * Gets the processor type from the given parameter.
     *
     * @param parameter the parameter
     * @return the processor type
     */
    public static ProcessorType fromParameter(Parameter parameter) {
        Class<?> type = parameter.getType();
        if (type == boolean.class) {
            Convert convert = parameter.getDeclaredAnnotation(Convert.class);
            if (convert != null) {
                return convert.value();
            }
        }
        // TODO: ref processor
        return fromClass(type);
    }

    /**
     * Registers a processor type for the given class.
     *
     * @param aClass the class
     * @param type   the processor type
     */
    public static void register(Class<?> aClass, ProcessorType type) {
        if (type != null) {
            map.put(aClass, type);
        } else {
            map.remove(aClass);
        }
    }

    /**
     * Registers a processor type for the given struct class.
     *
     * @param aClass the class
     * @param type   the processor type
     */
    public static void registerStruct(Class<?> aClass, StructAllocatorSpec<?> type) {
        register(aClass, ProcessorType.struct(aClass, type));
    }

    /**
     * {@return {@code true} if the given class is registered}
     * For an array type, returns {@code true} if its component type is registered.
     *
     * @param aClass the class
     */
    public static boolean isRegistered(Class<?> aClass) {
        if (aClass.isArray()) return isRegistered(aClass.componentType());
        for (Class<?> k : map.keySet()) {
            if (k.isAssignableFrom(aClass)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<Class<?>, T> collect(Class<T> instanceType) {
        return map.entrySet()
            .stream()
            .filter(e -> instanceType.isInstance(e.getValue()))
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> (T) entry.getValue()
            ));
    }
}