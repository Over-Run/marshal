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

import overrun.marshal.gen.CEnum;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.function.BiFunction;

import static java.lang.foreign.ValueLayout.*;

/**
 * Java-to-C helper.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Marshal {
    private static final VarHandle vh_addressArray = arrayVarHandle(ADDRESS);
    static final VarHandle vh_booleanArray = arrayVarHandle(JAVA_BOOLEAN);
    private static final VarHandle vh_intArray = arrayVarHandle(JAVA_INT);

    private Marshal() {
    }

    static VarHandle arrayVarHandle(ValueLayout valueLayout) {
        return MethodHandles.insertCoordinates(valueLayout.arrayElementVarHandle(), 1, 0L);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, String string) {
        if (string == null) return MemorySegment.NULL;
        return allocator.allocateFrom(string);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, String string, Charset charset) {
        if (string == null) return MemorySegment.NULL;
        return allocator.allocateFrom(string, charset);
    }

    public static int marshal(CEnum cEnum) {
        return cEnum.value();
    }

    public static MemorySegment marshal(Addressable addressable) {
        if (addressable == null) return MemorySegment.NULL;
        return addressable.segment();
    }

    public static MemorySegment marshal(Arena arena, Upcall upcall) {
        if (upcall == null) return MemorySegment.NULL;
        return upcall.stub(arena);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, boolean[] arr) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(JAVA_BOOLEAN, arr.length);
        for (int i = 0, l = arr.length; i < l; i++) {
            vh_booleanArray.set(segment, (long) i, segment, arr[i]);
        }
        return segment;
    }

    public static MemorySegment marshal(SegmentAllocator allocator, char[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_CHAR, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, byte[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_BYTE, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, short[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_SHORT, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, int[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_INT, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, long[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_LONG, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, float[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_FLOAT, arr);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, double[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_DOUBLE, arr);
    }

    public static <T, A extends SegmentAllocator> MemorySegment marshal(A allocator, T[] arr, BiFunction<A, T, MemorySegment> function) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(ADDRESS, arr.length);
        for (int i = 0, l = arr.length; i < l; i++) {
            vh_addressArray.set(segment, (long) i, function.apply(allocator, arr[i]));
        }
        return segment;
    }

    public static MemorySegment marshal(SegmentAllocator allocator, MemorySegment[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return marshal(allocator, arr, (_, segment) -> segment);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, String[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return marshal(allocator, arr, SegmentAllocator::allocateFrom);
    }

    public static MemorySegment marshal(SegmentAllocator allocator, String[] arr, Charset charset) {
        if (arr == null) return MemorySegment.NULL;
        return marshal(allocator, arr, (segmentAllocator, str) -> segmentAllocator.allocateFrom(str, charset));
    }

    public static MemorySegment marshal(SegmentAllocator allocator, CEnum[] arr) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(JAVA_INT, arr.length);
        for (int i = 0; i < arr.length; i++) {
            vh_intArray.set(segment, (long) i, arr[i].value());
        }
        return segment;
    }

    public static MemorySegment marshal(SegmentAllocator allocator, Addressable[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return marshal(allocator, arr, (_, addressable) -> addressable.segment());
    }

    public static MemorySegment marshal(Arena arena, Upcall[] arr) {
        if (arr == null) return MemorySegment.NULL;
        return marshal(arena, arr, (arena1, upcall) -> upcall.stub(arena1));
    }
}
