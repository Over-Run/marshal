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

package overrun.marshal.test.downcall;

import io.github.overrun.memstack.MemoryStack;
import overrun.marshal.Downcall;
import overrun.marshal.DowncallOption;
import overrun.marshal.gen.*;
import overrun.marshal.gen.processor.ProcessorType;
import overrun.marshal.gen.processor.ProcessorTypes;
import overrun.marshal.struct.ByValue;
import overrun.marshal.test.struct.Vector3;
import overrun.marshal.test.upcall.SimpleUpcall;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * Downcall interface
 *
 * @author squid233
 * @since 0.1.0
 */
public interface IDowncall {
    Map<String, FunctionDescriptor> MAP = Map.of("testDefault", FunctionDescriptor.of(ValueLayout.JAVA_INT));

    static IDowncall getInstance(boolean testDefaultNull) {
        ProcessorTypes.registerStruct(Vector3.class, Vector3.OF);
        ProcessorTypes.registerUpcall(SimpleUpcall.class, stub -> i -> SimpleUpcall.invoke(stub, i));
        return Downcall.load(MethodHandles.lookup(), DowncallProvider.lookup(testDefaultNull), DowncallOption.descriptors(MAP));
    }

    @Entrypoint("testReturnInt")
    MethodHandle mh_testReturnInt();

    void test();

    @Entrypoint("test")
    void testWithEntrypoint();

    @Skip
    default void testSkip() {
        System.out.print("testSkip");
    }

    default int testDefault() {
        return 84;
    }

    void testInt(int i);

    void testString(String s);

    void testUTF16String(@StrCharset("UTF-16") String s);

    int testUpcall(Arena arena, SimpleUpcall upcall);

    void testIntArray(int[] arr);

    void testIntArray(SegmentAllocator allocator, int[] arr);

    void testIntArray(Arena arena, int[] arr);

    void testIntArray(MemoryStack stack, int[] arr);

    void testVarArgsJava(int c, int... arr);

    void testStruct(Vector3 vector3);

    void testStructByValue(@ByValue Vector3 vector3);

    int testReturnInt();

    String testReturnString();

    @StrCharset("UTF-16")
    String testReturnUTF16String();

    MemorySegment testReturnUpcall();

    @Entrypoint("testReturnUpcall")
    SimpleUpcall testReturnUpcallObject();

    Vector3 testReturnStruct();

    @ByValue
    Vector3 testReturnStructByValue(SegmentAllocator allocator, int i);

    @Sized(2L)
    Vector3 testReturnStructSizedSeg();

    @Sized(2)
    int[] testReturnIntArray();

    void testSizedIntArray(@Sized(2) int[] arr);

    @Sized(4L)
    MemorySegment testReturnSizedSeg();

    void testRefIntArray(@Ref int[] arr);

    void testCriticalFalse();

    void testCriticalTrue(@Ref int[] arr);

    @Convert(ProcessorType.BoolConvert.INT)
    boolean testConvertBoolean(@Convert(ProcessorType.BoolConvert.INT) boolean b);

    @Entrypoint("testDefault")
    default MethodHandle mh_testDefaultMethodHandle() {
        throw new IllegalStateException("Thrown from interface");
    }

    @Skip
    default int testDefaultMethodHandle() {
        try {
            return (int) mh_testDefaultMethodHandle().invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
