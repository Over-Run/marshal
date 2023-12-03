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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

/**
 * Helper of boolean.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class BoolHelper {
    private BoolHelper() {
        //no instance
    }

    /**
     * Allocates a memory segment with the given boolean array.
     *
     * @param allocator the segment allocator
     * @param arr       the boolean array
     * @return the memory segment
     */
    public static MemorySegment of(SegmentAllocator allocator, boolean[] arr) {
        final MemorySegment segment = allocator.allocate(ValueLayout.JAVA_BOOLEAN, arr.length);
        for (int i = 0; i < arr.length; i++) {
            segment.setAtIndex(ValueLayout.JAVA_BOOLEAN, i, arr[i]);
        }
        return segment;
    }

    /**
     * Copies data from the given source memory segment into a boolean array.
     *
     * @param src the source
     * @param dst the destination
     */
    public static void copy(MemorySegment src, boolean[] dst) {
        final int length = checkArraySize(Math.min(src.byteSize(), dst.length));
        for (int i = 0; i < length; i++) {
            dst[i] = src.getAtIndex(ValueLayout.JAVA_BOOLEAN, i);
        }
    }

    /**
     * Converts a memory segment into a boolean array.
     *
     * @param segment the memory segment
     * @return the boolean array
     */
    public static boolean[] toArray(MemorySegment segment) {
        boolean[] b = new boolean[checkArraySize(segment.byteSize())];
        for (int i = 0; i < b.length; i++) {
            b[i] = segment.getAtIndex(ValueLayout.JAVA_BOOLEAN, i);
        }
        return b;
    }

    private static int checkArraySize(long byteSize) {
        if (byteSize > (Integer.MAX_VALUE - 8)) {
            throw new IllegalStateException(String.format("Segment is too large to wrap as %s. Size: %d", ValueLayout.JAVA_BOOLEAN, byteSize));
        }
        return (int) byteSize;
    }
}
