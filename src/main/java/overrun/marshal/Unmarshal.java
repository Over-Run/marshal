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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * C-to-Java helper.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Unmarshal {
    private Unmarshal() {
    }

    public static String unmarshalAsString(MemorySegment segment) {
        return segment.getString(0L);
    }

    public static String unmarshalAsString(MemorySegment segment, Charset charset) {
        return segment.getString(0L, charset);
    }

    public static boolean[] unmarshal(ValueLayout.OfBoolean elementLayout, MemorySegment segment) {
        final boolean[] arr = new boolean[checkArraySize(boolean[].class.getSimpleName(), segment.byteSize(), (int) elementLayout.byteSize())];
        for (int i = 0, l = arr.length; i < l; i++) {
            arr[i] = (boolean) Marshal.vh_booleanArray.get(segment, (long) i);
        }
        return arr;
    }

    public static char[] unmarshal(ValueLayout.OfChar elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static byte[] unmarshal(ValueLayout.OfByte elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static short[] unmarshal(ValueLayout.OfShort elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static int[] unmarshal(ValueLayout.OfInt elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static long[] unmarshal(ValueLayout.OfLong elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static float[] unmarshal(ValueLayout.OfFloat elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static double[] unmarshal(ValueLayout.OfDouble elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    public static <T> T[] unmarshal(MemoryLayout elementLayout, MemorySegment segment, IntFunction<T[]> generator, Function<MemorySegment, T> function) {
        return segment.elements(elementLayout).map(function).toArray(generator);
    }

    public static MemorySegment[] unmarshal(AddressLayout elementLayout, MemorySegment segment) {
        return unmarshal(elementLayout, segment, MemorySegment[]::new, Function.identity());
    }

    public static String[] unmarshalAsString(AddressLayout elementLayout, MemorySegment segment) {
        return unmarshal(elementLayout, segment, String[]::new, s -> s.reinterpret(Long.MAX_VALUE).getString(0L));
    }

    public static String[] unmarshalAsString(AddressLayout elementLayout, MemorySegment segment, Charset charset) {
        return unmarshal(elementLayout, segment, String[]::new, s -> s.reinterpret(Long.MAX_VALUE).getString(0L, charset));
    }

    public static <T extends CEnum> T[] unmarshalAsCEnum(ValueLayout.OfInt elementLayout, MemorySegment segment, IntFunction<T[]> generator, IntFunction<T> function) {
        return segment.elements(elementLayout).mapToInt(s -> s.get(elementLayout, 0L)).mapToObj(function).toArray(generator);
    }

    private static int checkArraySize(String typeName, long byteSize, int elemSize) {
        if (!((byteSize & (elemSize - 1)) == 0)) {
            throw new IllegalStateException(String.format("Segment size is not a multiple of %d. Size: %d", elemSize, byteSize));
        }
        long arraySize = byteSize / elemSize;
        if (arraySize > (Integer.MAX_VALUE - 8)) {
            throw new IllegalStateException(String.format("Segment is too large to wrap as %s. Size: %d", typeName, byteSize));
        }
        return (int) arraySize;
    }
}
