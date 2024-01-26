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

import org.jetbrains.annotations.Nullable;

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

    /**
     * Converts the given string to a segment.
     *
     * @param allocator the allocator
     * @param string    the string
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, @Nullable String string) {
        if (string == null) return MemorySegment.NULL;
        return allocator.allocateFrom(string);
    }

    /**
     * Converts the given string to a segment.
     *
     * @param allocator the allocator
     * @param string    the string
     * @param charset   the charset
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, @Nullable String string, Charset charset) {
        if (string == null) return MemorySegment.NULL;
        return allocator.allocateFrom(string, charset);
    }

    /**
     * Converts the given CEnum to an integer.
     *
     * @param cEnum the CEnum
     * @return the integer
     */
    public static int marshal(@Nullable CEnum cEnum) {
        return cEnum != null ? cEnum.value() : 0;
    }

    /**
     * Converts the given addressable to a segment.
     *
     * @param addressable the addressable
     * @return the segment
     */
    public static MemorySegment marshal(@Nullable Addressable addressable) {
        if (addressable == null) return MemorySegment.NULL;
        return addressable.segment();
    }

    /**
     * Converts the given upcall to a segment.
     *
     * @param arena  the arena
     * @param upcall the upcall
     * @return the segment
     */
    public static MemorySegment marshal(Arena arena, @Nullable Upcall upcall) {
        if (upcall == null) return MemorySegment.NULL;
        return upcall.stub(arena);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, boolean @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(JAVA_BOOLEAN, arr.length);
        for (int i = 0, l = arr.length; i < l; i++) {
            vh_booleanArray.set(segment, (long) i, arr[i]);
        }
        return segment;
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, char @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_CHAR, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, byte @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_BYTE, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, short @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_SHORT, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, int @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_INT, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, long @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_LONG, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, float @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_FLOAT, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, double @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        return allocator.allocateFrom(JAVA_DOUBLE, arr);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @param function  a function to apply to each element
     * @param <A>       the type of the allocator
     * @param <T>       the type of the element
     * @return the segment
     */
    public static <A extends SegmentAllocator, T> MemorySegment marshal(A allocator, T @Nullable [] arr, BiFunction<A, T, MemorySegment> function) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(ADDRESS, arr.length);
        for (int i = 0, l = arr.length; i < l; i++) {
            vh_addressArray.set(segment, (long) i, function.apply(allocator, arr[i]));
        }
        return segment;
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, MemorySegment @Nullable [] arr) {
        return marshal(allocator, arr, (_, segment) -> segment);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, String @Nullable [] arr) {
        return marshal(allocator, arr, SegmentAllocator::allocateFrom);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @param charset   the charset
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, String @Nullable [] arr, Charset charset) {
        return marshal(allocator, arr, (segmentAllocator, str) -> segmentAllocator.allocateFrom(str, charset));
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, CEnum @Nullable [] arr) {
        if (arr == null) return MemorySegment.NULL;
        final MemorySegment segment = allocator.allocate(JAVA_INT, arr.length);
        for (int i = 0; i < arr.length; i++) {
            vh_intArray.set(segment, (long) i, arr[i].value());
        }
        return segment;
    }

    /**
     * Converts the given array to a segment.
     *
     * @param allocator the allocator
     * @param arr       the array
     * @return the segment
     */
    public static MemorySegment marshal(SegmentAllocator allocator, @Nullable Addressable @Nullable [] arr) {
        return marshal(allocator, arr, (_, addressable) -> addressable != null ? addressable.segment() : MemorySegment.NULL);
    }

    /**
     * Converts the given array to a segment.
     *
     * @param arena the arena
     * @param arr   the array
     * @return the segment
     */
    public static MemorySegment marshal(Arena arena, Upcall @Nullable [] arr) {
        return marshal(arena, arr, (arena1, upcall) -> upcall.stub(arena1));
    }
}
