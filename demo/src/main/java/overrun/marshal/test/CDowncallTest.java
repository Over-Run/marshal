/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
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
import overrun.marshal.gen.*;
import overrun.marshal.gen.struct.ByValue;
import overrun.marshal.gen.struct.StructRef;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Test basic features
 *
 * @author squid233
 * @since 0.1.0
 */
@Downcall(libname = "NativeLib.dll", nonFinal = true)
interface CDowncallTest {
    int CONST_VALUE = 42;
    boolean BOOL_VALUE = true;
    /**
     * A string value
     */
    String STR_VALUE = "Hello world";
    @Skip
    int SKIPPED = -1;

    /**
     * This is a test method with javadoc.
     *
     * @return an integer
     */
    int testWithDocAndReturnValue();

    int testWithReturnValue();

    void testWithArgument(int i, MemorySegment holder);

    MemorySegment testWithArgAndReturnValue(MemorySegment segment);

    @Custom("""
        return testWithArgAndReturnValue(MemorySegment.NULL);""")
    MemorySegment testWithCustomBody();

    @Custom("""
        System.out.println("Hello world");
        return testWithArgAndReturnValue(MemorySegment.NULL);""")
    MemorySegment testWithCustomBodyMultiline();

    @Access(AccessModifier.PRIVATE)
    int testWithPrivate(int i);

    void testWithArray(int i, MemorySegment arr, MemorySegment nullableArr);

    void testWithArray(int i, int[] arr, @NullableRef int[] nullableArr);

    void testWithArray(SegmentAllocator allocator, int i, int[] arr, @NullableRef int[] nullableArr);

    void testWithArray(Arena arena, int i, int[] arr, @NullableRef int[] nullableArr);

    void testWithArray(MemoryStack stack, int i, int[] arr, @NullableRef int[] nullableArr);

    void testWithOneRef(MemorySegment arr);

    /**
     * Test with ref
     *
     * @param arr arr
     */
    void testWithOneRef(@Ref int[] arr);

    void testWithRefArray(MemorySegment arr0, MemorySegment arr1, MemorySegment arr2, MemorySegment arr3, MemorySegment arr4, MemorySegment arr5);

    void testWithRefArray(int[] arr0, @Ref int[] arr1, @NullableRef @Ref int[] arr2, boolean[] arr3, @Ref boolean[] arr4, @Sized(3) int[] arr5);

    void testWithString(MemorySegment str1, MemorySegment str2, MemorySegment nullableStr);

    void testWithString(String str1, @StrCharset("UTF-16") String str2, @NullableRef String nullableStr);

    @Entrypoint("testReturnString")
    MemorySegment ntestReturnString();

    String testReturnString();

    @Entrypoint("testReturnStringUTF16")
    MemorySegment ntestReturnStringUTF16();

    @StrCharset("UTF-16")
    String testReturnStringUTF16();

    MemorySegment testStringArray(MemorySegment arr, MemorySegment refArr);

    String[] testStringArray(String[] arr, @Ref String[] refArr);

    @StrCharset("UTF-16")
    String[] testStringArrayUTF16(@StrCharset("UTF-16") String[] arr, @Ref @StrCharset("UTF-16") String[] refArr);

    @Entrypoint("testWithReturnArray")
    MemorySegment ntestWithReturnArray();

    boolean[] testWithReturnArray();

    void testMixArrSeg(MemorySegment segment, MemorySegment arr);

    void testMixArrSeg(MemorySegment segment, @Ref int[] arr);

    @Critical(allowHeapAccess = true)
    void testCriticalTrue();

    @Critical(allowHeapAccess = false)
    void testCriticalFalse();

    @Entrypoint("testReturnSizedArr")
    @SizedSeg(4 * Integer.BYTES)
    MemorySegment ntestReturnSizedArr();

    @Sized(4)
    int[] testReturnSizedArr();

    @SizedSeg(4L)
    MemorySegment testReturnSizedSeg();

    void testUpcall(MemorySegment cb, MemorySegment nullableCb);

    void testUpcall(Arena arena, GLFWErrorCallback cb, @NullableRef GLFWErrorCallback nullableCb);

    @Entrypoint("testReturnUpcall")
    MemorySegment ntestReturnUpcall();

    GLFWErrorCallback testReturnUpcall(Arena arena);

    void testStruct(MemorySegment struct, MemorySegment nullableStruct);

    void testStruct(@StructRef("overrun.marshal.test.Vector3") Object struct, @NullableRef @StructRef("overrun.marshal.test.Vector3") Object nullableStruct);

    @StructRef("overrun.marshal.test.StructTest")
    Object testReturnStruct();

    @ByValue
    @StructRef("overrun.marshal.test.StructTest")
    Object returnByValueStruct(SegmentAllocator allocator);

    @ByValue
    @StructRef("overrun.marshal.test.StructTest")
    Object returnByValueStructWithArena(Arena arena);

    int testEnumValue(int value);

    MyEnum testEnumValue(MyEnum value);

    int testEnumValueWithRef(int value, MemorySegment ref);

    MyEnum testEnumValueWithRef(MyEnum value, @Ref int[] ref);

    void testNameMemoryStack(MemorySegment stack, MemorySegment arr);

    void testNameMemoryStack(MemorySegment stack, int[] arr);

    @Entrypoint("_testAnotherEntrypoint")
    void testAnotherEntrypoint(MemorySegment segment);

    void testAnotherEntrypoint(int[] segment);

    void testDuplicateName(int[] testDuplicateName);

    /**
     * This is a test that tests all features.
     *
     * @param segment A memory segment
     * @return Another memory segment
     */
    @Access(AccessModifier.PROTECTED)
    @Default("MemorySegment.NULL")
    @Entrypoint("testAllFeatures")
    MemorySegment testAll(MemorySegment segment);
}
