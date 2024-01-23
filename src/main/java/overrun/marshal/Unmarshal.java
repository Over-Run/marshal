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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.function.IntFunction;

import static java.lang.foreign.ValueLayout.*;

/**
 * C-to-Java helper.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Unmarshal {
    /**
     * The max string size.
     */
    public static final long STR_SIZE = Integer.MAX_VALUE - 8;
    private static final AddressLayout STRING_LAYOUT = ADDRESS.withTargetLayout(
        MemoryLayout.sequenceLayout(STR_SIZE, JAVA_BYTE)
    );
    private static final VarHandle vh_stringArray = Marshal.arrayVarHandle(STRING_LAYOUT);

    private Unmarshal() {
    }

    /**
     * Unmarshal the given segment as a string.
     *
     * @param segment the segment
     * @return the string
     */
    public static String unmarshalAsString(MemorySegment segment) {
        return segment.getString(0L);
    }

    /**
     * Unmarshal the given segment as a string.
     *
     * @param segment the segment
     * @param charset the charset
     * @return the string
     */
    public static String unmarshalAsString(MemorySegment segment, Charset charset) {
        return segment.getString(0L, charset);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static boolean[] unmarshal(OfBoolean elementLayout, MemorySegment segment) {
        final boolean[] arr = new boolean[checkArraySize(boolean[].class.getSimpleName(), segment.byteSize(), (int) elementLayout.byteSize())];
        for (int i = 0, l = arr.length; i < l; i++) {
            arr[i] = (boolean) Marshal.vh_booleanArray.get(segment, (long) i);
        }
        return arr;
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static char[] unmarshal(OfChar elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static byte[] unmarshal(OfByte elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static short[] unmarshal(OfShort elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static int[] unmarshal(OfInt elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static long[] unmarshal(OfLong elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static float[] unmarshal(OfFloat elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static double[] unmarshal(OfDouble elementLayout, MemorySegment segment) {
        return segment.toArray(elementLayout);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @param generator     a function which produces a new array of the desired type and the provided length
     * @param function      a function to apply to each element
     * @param <T>           the type of the element
     * @return the array
     */
    public static <T> T[] unmarshal(MemoryLayout elementLayout, MemorySegment segment, IntFunction<T[]> generator, Function<MemorySegment, T> function) {
        return segment.elements(elementLayout).map(function).toArray(generator);
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static MemorySegment[] unmarshal(AddressLayout elementLayout, MemorySegment segment) {
        return unmarshal(elementLayout, segment, MemorySegment[]::new, Function.identity());
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @return the array
     */
    public static String[] unmarshalAsString(AddressLayout elementLayout, MemorySegment segment) {
        return unmarshal(elementLayout, segment, String[]::new, s -> s.get(STRING_LAYOUT, 0L).getString(0L));
    }

    /**
     * Unmarshal the given segment as an array.
     *
     * @param elementLayout the source element layout
     * @param segment       the segment
     * @param charset       the charset
     * @return the array
     */
    public static String[] unmarshalAsString(AddressLayout elementLayout, MemorySegment segment, Charset charset) {
        return unmarshal(elementLayout, segment, String[]::new, s -> s.get(STRING_LAYOUT, 0L).getString(0L, charset));
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

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, boolean[] dst) {
        if (dst == null) return;
        for (int i = 0; i < dst.length; i++) {
            dst[i] = (boolean) Marshal.vh_booleanArray.get(src, (long) i);
        }
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, char[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_CHAR, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, byte[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_BYTE, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, short[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_SHORT, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, int[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_INT, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, long[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_LONG, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, float[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_FLOAT, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, double[] dst) {
        if (dst == null) return;
        MemorySegment.copy(src, JAVA_DOUBLE, 0L, dst, 0, dst.length);
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src the source segment
     * @param dst the destination
     */
    public static void copy(MemorySegment src, String[] dst) {
        if (dst == null) return;
        for (int i = 0; i < dst.length; i++) {
            dst[i] = ((MemorySegment) vh_stringArray.get(src, (long) i)).getString(0L);
        }
    }

    /**
     * Copies from the given segment to the destination.
     *
     * @param src     the source segment
     * @param dst     the destination
     * @param charset the charset
     */
    public static void copy(MemorySegment src, String[] dst, Charset charset) {
        if (dst == null) return;
        for (int i = 0; i < dst.length; i++) {
            dst[i] = ((MemorySegment) vh_stringArray.get(src, (long) i)).getString(0L, charset);
        }
    }
}
