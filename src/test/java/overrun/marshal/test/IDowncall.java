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

import overrun.marshal.ByValue;
import overrun.marshal.Downcall;
import overrun.marshal.MemoryStack;
import overrun.marshal.gen.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Downcall interface
 *
 * @author squid233
 * @since 0.1.0
 */
public interface IDowncall {
    static IDowncall getInstance(boolean testDefaultNull) {
        return Downcall.load(DowncallProvider.lookup(testDefaultNull));
    }

    void test();

    @Entrypoint("test")
    void testWithEntrypoint();

    @Skip
    default void testSkip() {
        System.out.print("testSkip");
    }

    default void testDefault() {
        System.out.print("testDefault in interface");
    }

    void testInt(int i);

    void testString(String s);

    void testUTF16String(@StrCharset("UTF-16") String s);

    void testCEnum(MyEnum myEnum);

    int testUpcall(Arena arena, SimpleUpcall upcall);

    void testIntArray(int[] arr);

    void testIntArray(SegmentAllocator allocator, int[] arr);

    void testIntArray(Arena arena, int[] arr);

    void testIntArray(MemoryStack stack, int[] arr);

    void testVarArgsJava(int c, int... arr);

    void testStruct(Vector3 vector3);

    int testReturnInt();

    @SizedSeg(12)
    String testReturnString();

    @SizedSeg(40)
    @StrCharset("UTF-16")
    String testReturnUTF16String();

    MyEnum testReturnCEnum();

    SimpleUpcall testReturnUpcall(Arena arena);

    Vector3 testReturnStruct();

    @ByValue
    Vector3 testReturnStructByValue(SegmentAllocator allocator);

    @SizedSeg(2L)
    Vector3 testReturnStructSizedSeg();

    @Sized(2)
    Vector3 testReturnStructSized();

    @Sized(2)
    int[] testReturnIntArray();

    void testSizedIntArray(@Sized(2) int[] arr);

    @SizedSeg(4L)
    MemorySegment testReturnSizedSeg();

    void testRefIntArray(@Ref int[] arr);

    void testCriticalFalse();

    void testCriticalTrue(@Ref int[] arr);

    @Convert(Type.INT)
    boolean testConvertBoolean(@Convert(Type.INT) boolean b);
}
