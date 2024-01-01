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

import overrun.marshal.SizedSeg;
import overrun.marshal.Sized;
import overrun.marshal.Skip;
import overrun.marshal.StrCharset;
import overrun.marshal.struct.Const;
import overrun.marshal.struct.Padding;
import overrun.marshal.struct.Struct;

import java.lang.foreign.MemorySegment;

/**
 * {@linkplain #_LAYOUT Layout}
 *
 * @author squid233
 * @since 0.1.0
 */
@Struct
final class CStructTest {
    @Skip
    int _LAYOUT;
    int x;
    @Const
    int y;
    int index;
    int[] segmentAllocator;
    GLFWErrorCallback arena;
    /**
     * the timestamp
     */
    long stamp;
    byte b;
    @Padding(7)
    int padding;
    MemorySegment segment;
    @SizedSeg(16L)
    MemorySegment sizedSegment;
    String name;
    @StrCharset("UTF-16")
    String utf16Name;
    int[] arr;
    @Sized(4)
    int[] sizedArr;
    boolean[] boolArr;
    String[] strArr;
    GLFWErrorCallback upcall;
}
