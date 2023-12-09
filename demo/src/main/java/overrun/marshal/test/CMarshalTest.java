/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

import overrun.marshal.*;

import java.lang.foreign.MemorySegment;

/**
 * Test basic features
 *
 * @author squid233
 * @since 0.1.0
 */
@NativeApi(libname = "NativeLib.dll", name = "MarshalTest", makeFinal = false)
public interface CMarshalTest {
    int CONST_VALUE = 42;
    boolean BOOL_VALUE = true;
    /**
     * A string value
     */
    String STR_VALUE = "Hello world";

    void test();

    @Entrypoint("test")
    void testWithEntrypoint();

    /**
     * This is a test method with javadoc.
     *
     * @return an integer
     */
    int testWithDocAndReturnValue();

    int testWithReturnValue();

    @Default
    void testWithOptional();

    void testWithArgument(int i, MemorySegment holder);

    MemorySegment testWithArgAndReturnValue(MemorySegment segment);

    @Custom("""
        return testWithArgAndReturnValue(MemorySegment.NULL);""")
    MemorySegment testWithCustomBody();

    @Access(AccessModifier.PRIVATE)
    int testWithPrivate(int i);

    void testWithArray(MemorySegment arr);

    @Overload
    void testWithArray(int[] arr);

    void testWithOneRef(MemorySegment arr);

    @Overload
    void testWithOneRef(@Ref int[] arr);

    void testWithRefArray(MemorySegment arr0, MemorySegment arr1, MemorySegment arr2, MemorySegment arr3, MemorySegment arr4, MemorySegment arr5);

    @Overload
    void testWithRefArray(int[] arr0, @Ref int[] arr1, @Ref(nullable = true) int[] arr2, boolean[] arr3, @Ref boolean[] arr4, @FixedSize(3) int[] arr5);

    void testWithString(MemorySegment str);

    @Overload
    void testWithString(String str);

    @Entrypoint("testReturnString")
    MemorySegment ntestReturnString();

    @Overload("ntestReturnString")
    String testReturnString();

    MemorySegment testStringArray(MemorySegment arr, MemorySegment refArr);

    @Overload
    String[] testStringArray(String[] arr, @Ref String[] refArr);

    @Entrypoint("testWithReturnArray")
    MemorySegment ntestWithReturnArray();

    @Overload("ntestWithReturnArray")
    boolean[] testWithReturnArray();

    void testMixArrSeg(MemorySegment segment, MemorySegment arr);

    @Overload
    void testMixArrSeg(MemorySegment segment, @Ref int[] arr);

    /**
     * This is a test that tests all features.
     *
     * @param segment A memory segment
     * @return Another memory segment
     */
    @Access(AccessModifier.PROTECTED)
    @Default("MemorySegment.NULL")
//    @Default("""
//        MemorySegment.
//        NULL""")
    @Entrypoint("testAllFeatures")
    MemorySegment testAll(MemorySegment segment);
}
