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

package overrun.marshal.test;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test upcall
 *
 * @author squid233
 * @since 0.1.0
 */
public final class UpcallTest {
    private int invokeSimpleUpcall(MemorySegment upcall) {
        return SimpleUpcall.invoke(upcall, 42);
    }

    private int[] invokeComplexUpcall(MemorySegment upcall) {
        return ComplexUpcall.invoke(upcall, new int[]{4, 2});
    }

    @Test
    void testSimpleUpcall() {
        final Arena arena = Arena.ofAuto();
        final SimpleUpcall upcall = i -> i * 2;
        final MemorySegment stub = upcall.stub(arena);
        assertEquals(84, invokeSimpleUpcall(stub));
    }

    @Test
    void testComplexUpcall() {
        final Arena arena = Arena.ofAuto();
        final ComplexUpcall upcall = arr -> new int[]{arr[0] * 4, arr[1] * 2};
        final MemorySegment stub = upcall.stub(arena);
        assertArrayEquals(new int[]{16, 4}, invokeComplexUpcall(stub));
    }
}
