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

import io.github.overrun.memstack.MemoryStack;
import overrun.marshal.gen.DowncallMethodParameter;
import overrun.marshal.gen.DowncallMethodType;
import overrun.marshal.gen.ConvertedClassType;
import overrun.marshal.gen.processor.ReturnValueTransformer;

import java.lang.invoke.*;
import java.util.Arrays;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallFactory {
    private static final MethodHandles.Lookup privateLookup = MethodHandles.lookup();
    private static final MethodHandle
        MH_throwISE,
        MH_popStack,
        MH_popStackVoid;

    static {
        try {
            MH_throwISE = privateLookup.findStatic(DowncallFactory.class, "throwISE", MethodType.methodType(Object.class, Throwable.class, Class.class, String.class, MethodType.class));
            MH_popStack = privateLookup.findStatic(DowncallFactory.class, "popStack", MethodType.methodType(Object.class, Throwable.class, Object.class));
            MH_popStackVoid = privateLookup.findStatic(DowncallFactory.class, "popStack", MethodType.methodType(void.class, Throwable.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private DowncallFactory() {
    }

    public static MethodHandle downcallHandle(
        MethodHandles.Lookup caller,
        String entrypoint,
        Class<?> type,
        DirectAccessData data
    ) {
        return data.methodHandle(entrypoint);
    }

    public static CallSite downcallCallSite(
        MethodHandles.Lookup caller,
        String entrypoint,
        MethodType type,
        DirectAccessData data,
        DowncallMethodType downcallMethodType,
        boolean popStack
    ) {
        MethodHandle methodHandle = data.methodHandle(entrypoint);
        Class<?> returnType = type.returnType();
        methodHandle = ReturnValueTransformer.getInstance().process(methodHandle, new ReturnValueTransformer.Context(returnType, downcallMethodType));
        methodHandle = MethodHandles.catchException(methodHandle, Throwable.class, MethodHandles.insertArguments(MH_throwISE.asType(MH_throwISE.type().changeReturnType(returnType)), 1, caller.lookupClass(), entrypoint, type));
        if (popStack) {
            if (returnType == void.class) {
                methodHandle = MethodHandles.tryFinally(methodHandle, MH_popStackVoid);
            } else {
                methodHandle = MethodHandles.tryFinally(methodHandle, MH_popStack.asType(MethodType.methodType(returnType, Throwable.class, returnType)));
            }
        }
        return new ConstantCallSite(methodHandle);
    }

    private static Object throwISE(Throwable t, Class<?> caller, String entrypoint, MethodType type) {
        throw new IllegalStateException(caller.getName() + "." + entrypoint + type, t);
    }

    private static Object popStack(Throwable t, Object result) {
        MemoryStack.popLocal();
        return result;
    }

    private static void popStack(Throwable t) {
        MemoryStack.popLocal();
    }

    public static DowncallMethodParameter createDowncallMethodParameter(
        MethodHandles.Lookup lookup,
        String name,
        Class<?> type,
        ConvertedClassType parameterType,
        boolean byValue,
        boolean ref,
        long sized,
        String charset,
        String canonicalType
    ) {
        return new DowncallMethodParameter(parameterType, byValue, ref, sized, charset, canonicalType);
    }

    public static DowncallMethodType createDowncallMethodType(
        MethodHandles.Lookup lookup,
        String entrypoint,
        Class<?> type,
        ConvertedClassType returnType,
        boolean byValue,
        boolean critical,
        boolean criticalAllowHeapAccess,
        long sized,
        String charset,
        String canonicalType,
        Object... args
    ) {
        return new DowncallMethodType(entrypoint,
            returnType,
            Arrays.stream(args).map(o -> (DowncallMethodParameter) o).toArray(DowncallMethodParameter[]::new),
            byValue,
            critical,
            criticalAllowHeapAccess,
            sized,
            charset,
            canonicalType);
    }

    public static ConvertedClassType createExtendedClassType(
        MethodHandles.Lookup lookup,
        String name,
        Class<?> type,
        Class<?> javaClass,
        Class<?> downcallClass
    ) {
        return new ConvertedClassType(javaClass, downcallClass);
    }
}
