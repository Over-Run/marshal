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

import overrun.marshal.MemoryStack;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static overrun.marshal.test.TestUtil.*;

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
            seg("testInt", LOOKUP.findStatic(DowncallProvider.class, "testInt", MethodType.methodType(void.class, int.class)), FunctionDescriptor.ofVoid(JAVA_INT));
            seg("testString", LOOKUP.findStatic(DowncallProvider.class, "testString", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testUTF16String", LOOKUP.findStatic(DowncallProvider.class, "testUTF16String", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testCEnum", LOOKUP.findStatic(DowncallProvider.class, "testCEnum", MethodType.methodType(void.class, int.class)), FunctionDescriptor.ofVoid(JAVA_INT));
            seg("testUpcall", LOOKUP.findStatic(DowncallProvider.class, "testUpcall", MethodType.methodType(int.class, MemorySegment.class)), FunctionDescriptor.of(JAVA_INT, ADDRESS));
            seg("testIntArray", LOOKUP.findStatic(DowncallProvider.class, "testIntArray", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testVarArgsJava", LOOKUP.findStatic(DowncallProvider.class, "testVarArgsJava", MethodType.methodType(void.class, int.class, MemorySegment.class)), FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
            seg("testStruct", LOOKUP.findStatic(DowncallProvider.class, "testStruct", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testReturnInt", LOOKUP.findStatic(DowncallProvider.class, "testReturnInt", MethodType.methodType(int.class)), FunctionDescriptor.of(JAVA_INT));
            seg("testReturnString", LOOKUP.findStatic(DowncallProvider.class, "testReturnString", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnUTF16String", LOOKUP.findStatic(DowncallProvider.class, "testReturnUTF16String", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnCEnum", LOOKUP.findStatic(DowncallProvider.class, "testReturnCEnum", MethodType.methodType(int.class)), FunctionDescriptor.of(JAVA_INT));
            seg("testReturnUpcall", LOOKUP.findStatic(DowncallProvider.class, "testReturnUpcall", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnStruct", LOOKUP.findStatic(DowncallProvider.class, "testReturnStruct", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnStructByValue", LOOKUP.findStatic(DowncallProvider.class, "testReturnStructByValue", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(Vector3.LAYOUT));
            seg("testReturnStructSizedSeg", LOOKUP.findStatic(DowncallProvider.class, "testReturnStructSizedSeg", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnStructSized", LOOKUP.findStatic(DowncallProvider.class, "testReturnStructSized", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testReturnIntArray", LOOKUP.findStatic(DowncallProvider.class, "testReturnIntArray", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testSizedIntArray", LOOKUP.findStatic(DowncallProvider.class, "testSizedIntArray", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testReturnSizedSeg", LOOKUP.findStatic(DowncallProvider.class, "testReturnSizedSeg", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ADDRESS));
            seg("testRefIntArray", LOOKUP.findStatic(DowncallProvider.class, "testRefIntArray", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
            seg("testCriticalFalse", MethodHandles.empty(MethodType.methodType(void.class)), FunctionDescriptor.ofVoid());
            seg("testCriticalTrue", LOOKUP.findStatic(DowncallProvider.class, "testCriticalTrue", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ADDRESS));
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

    private static void testInt(int i) {
        System.out.print(i);
    }

    private static void testString(MemorySegment s) {
        System.out.print(s.reinterpret(12).getString(0));
    }

    private static void testUTF16String(MemorySegment s) {
        System.out.print(s.reinterpret(40).getString(0, StandardCharsets.UTF_16));
    }

    private static void testCEnum(int i) {
        System.out.print(i);
    }

    private static int testUpcall(MemorySegment upcall) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return SimpleUpcall.wrap(stack, upcall).invoke(42);
        }
    }

    private static void testIntArray(MemorySegment arr) {
        testVarArgsJava(2, arr);
    }

    private static void testVarArgsJava(int c, MemorySegment arr) {
        System.out.print(Arrays.toString(arr.reinterpret(JAVA_INT.scale(0, c)).toArray(JAVA_INT)));
    }

    private static void writeVector3(MemorySegment segment, int x, int y, int z) {
        segment.set(JAVA_INT, 0, x);
        segment.set(JAVA_INT, 4, y);
        segment.set(JAVA_INT, 8, z);
    }

    private static void testStruct(MemorySegment vector3) {
        writeVector3(vector3.reinterpret(Vector3.LAYOUT.byteSize()), 1, 2, 3);
    }

    private static int testReturnInt() {
        return 42;
    }

    private static MemorySegment testReturnString() {
        return ARENA.allocateFrom(TEST_STRING);
    }

    private static MemorySegment testReturnUTF16String() {
        return ARENA.allocateFrom(utf16Str(TEST_UTF16_STRING), StandardCharsets.UTF_16);
    }

    private static int testReturnCEnum() {
        return 2;
    }

    private static MemorySegment testReturnUpcall() {
        return ((SimpleUpcall) (i -> i * 2)).stub(ARENA);
    }

    private static MemorySegment testReturnStruct() {
        final MemorySegment segment = ARENA.allocate(Vector3.LAYOUT);
        writeVector3(segment, 4, 5, 6);
        return segment;
    }

    private static MemorySegment testReturnStructByValue() {
        final MemorySegment segment = ARENA.allocate(Vector3.LAYOUT);
        writeVector3(segment, 7, 8, 9);
        return segment;
    }

    private static MemorySegment testReturnStructSizedSeg() {
        final MemorySegment segment = ARENA.allocate(Vector3.LAYOUT, 2L);
        writeVector3(segment, 1, 2, 3);
        writeVector3(segment.asSlice(Vector3.LAYOUT.scale(0L, 1L)), 4, 5, 6);
        return segment;
    }

    private static MemorySegment testReturnStructSized() {
        return testReturnStructSizedSeg();
    }

    private static MemorySegment testReturnIntArray() {
        return ARENA.allocateFrom(JAVA_INT, 4, 2);
    }

    private static void testSizedIntArray(MemorySegment arr) {
        testIntArray(arr);
    }

    private static MemorySegment testReturnSizedSeg() {
        return ARENA.allocateFrom(JAVA_INT, 8);
    }

    private static void testRefIntArray(MemorySegment arr) {
        arr.reinterpret(JAVA_INT.byteSize()).set(JAVA_INT, 0, 8);
    }

    private static void testCriticalTrue(MemorySegment arr) {
        testRefIntArray(arr);
    }

    private static void seg(String name, MethodHandle mh, FunctionDescriptor fd) {
        table.put(name, LINKER.upcallStub(mh, fd, ARENA));
    }
}
