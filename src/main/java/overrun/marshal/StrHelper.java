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

package overrun.marshal;

import java.lang.foreign.*;
import java.nio.charset.Charset;

/**
 * Helper of string arrays.
 *
 * @author squid233
 * @since 0.1.0
 */
@Deprecated(since = "0.1.0", forRemoval = true)
public final class StrHelper {
    private static final SequenceLayout STR_LAYOUT = MemoryLayout.sequenceLayout(Integer.MAX_VALUE - 8, ValueLayout.JAVA_BYTE);

    private StrHelper() {
        //no instance
    }

    /**
     * Allocates a memory segment with the given string array.
     *
     * @param allocator the segment allocator
     * @param strings   the string array
     * @param charset   the string charset
     * @return the memory segment
     */
    public static MemorySegment of(SegmentAllocator allocator, String[] strings, Charset charset) {
        final MemorySegment segment = allocator.allocate(ValueLayout.ADDRESS, strings.length);
        for (int i = 0; i < strings.length; i++) {
            segment.setAtIndex(ValueLayout.ADDRESS, i, allocator.allocateFrom(strings[i], charset));
        }
        return segment;
    }

    /**
     * Copies data from the given source memory segment into a string array.
     *
     * @param src     the source
     * @param dst     the destination
     * @param charset the string charset
     */
    public static void copy(MemorySegment src, String[] dst, Charset charset) {
        final int length = checkArraySize(Math.min(src.byteSize(), dst.length));
        for (int i = 0; i < length; i++) {
            dst[i] = src.getAtIndex(ValueLayout.ADDRESS.withTargetLayout(STR_LAYOUT), i).getString(0L, charset);
        }
    }

    /**
     * Converts a memory segment into a string array.
     *
     * @param segment the memory segment
     * @param charset the string charset
     * @return the string array
     */
    public static String[] toArray(MemorySegment segment, Charset charset) {
        String[] arr = new String[checkArraySize(segment.byteSize())];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = segment.getAtIndex(ValueLayout.ADDRESS.withTargetLayout(STR_LAYOUT), i).getString(0L, charset);
        }
        return arr;
    }

    private static int checkArraySize(long byteSize) {
        final long elemSize = ValueLayout.ADDRESS.byteSize();
        if (!((byteSize & (elemSize - 1)) == 0)) {
            throw new IllegalStateException(String.format("Segment size is not a multiple of %d. Size: %d", elemSize, byteSize));
        }
        long arraySize = byteSize / elemSize;
        if (arraySize > (Integer.MAX_VALUE - 8)) {
            throw new IllegalStateException(String.format("Segment is too large to wrap as %s. Size: %d", ValueLayout.ADDRESS, byteSize));
        }
        return (int) arraySize;
    }
}
