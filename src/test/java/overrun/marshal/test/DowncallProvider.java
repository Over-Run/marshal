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

package overrun.marshal.test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Provides downcall handle
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallProvider {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena ARENA = Arena.ofAuto();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<String, MemorySegment> table = HashMap.newHashMap(1);

    static {
        try {
            seg("test", LOOKUP.findStatic(DowncallProvider.class, "test", MethodType.methodType(void.class)), FunctionDescriptor.ofVoid());
            seg("testDefault", LOOKUP.findStatic(DowncallProvider.class, "testDefault", MethodType.methodType(void.class)), FunctionDescriptor.ofVoid());
            seg("test_int", LOOKUP.findStatic(DowncallProvider.class, "test_int", MethodType.methodType(void.class, int.class)), FunctionDescriptor.ofVoid(JAVA_INT));
            seg("test_String", LOOKUP.findStatic(DowncallProvider.class, "test_String", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("test_UTF16String", LOOKUP.findStatic(DowncallProvider.class, "test_UTF16String", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("test_CEnum", LOOKUP.findStatic(DowncallProvider.class, "test_CEnum", MethodType.methodType(void.class, int.class)), FunctionDescriptor.ofVoid(JAVA_INT));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static SymbolLookup lookup(boolean testDefaultNull) {
        return name -> {
            if (testDefaultNull && "testDefault".equals(name)) {
                return Optional.empty();
            }
            return Optional.ofNullable(table.get(name));
        };
    }

    private static void test() {
        System.out.print("test");
    }

    private static void testDefault() {
        System.out.print("testDefault");
    }

    private static void test_int(int i) {
        System.out.print(i);
    }

    private static void test_String(MemorySegment s) {
        System.out.print(s.reinterpret(12).getString(0));
    }

    private static void test_UTF16String(MemorySegment s) {
        System.out.print(s.reinterpret(40).getString(0, StandardCharsets.UTF_16));
    }

    private static void test_CEnum(int i) {
        System.out.print(i);
    }

    private static void seg(String name, MethodHandle mh, FunctionDescriptor fd) {
        table.put(name, LINKER.upcallStub(mh, fd, ARENA));
    }
}
