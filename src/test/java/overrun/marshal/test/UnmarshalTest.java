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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static overrun.marshal.Marshal.marshal;
import static overrun.marshal.Unmarshal.*;
import static overrun.marshal.test.TestUtil.*;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class UnmarshalTest {
    @Test
    void testString() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(TEST_STRING, unmarshalAsString(arena.allocateFrom(TEST_STRING)));
            assertEquals(TEST_UTF16_STRING, unmarshalAsString(arena.allocateFrom(utf16Str(TEST_UTF16_STRING), StandardCharsets.UTF_16), StandardCharsets.UTF_16));
        }
    }

    @Test
    void testPrimitiveArray() {
        try (Arena arena = Arena.ofConfined()) {
            assertArrayEquals(new boolean[]{false, true}, unmarshalAsBooleanArray(marshal(arena, new boolean[]{false, true})));
            assertArrayEquals(new char[]{'1', '2'}, unmarshalAsCharArray(marshal(arena, new char[]{'1', '2'})));
            assertArrayEquals(new byte[]{1, 2}, unmarshalAsByteArray(marshal(arena, new byte[]{1, 2})));
            assertArrayEquals(new short[]{3, 4}, unmarshalAsShortArray(marshal(arena, new short[]{3, 4})));
            assertArrayEquals(new int[]{5, 6}, unmarshalAsIntArray(marshal(arena, new int[]{5, 6})));
            assertArrayEquals(new long[]{7L, 8L}, unmarshalAsLongArray(marshal(arena, new long[]{7L, 8L})));
            assertArrayEquals(new float[]{9F, 10F}, unmarshalAsFloatArray(marshal(arena, new float[]{9F, 10F})));
            assertArrayEquals(new double[]{11D, 12D}, unmarshalAsDoubleArray(marshal(arena, new double[]{11D, 12D})));
        }
    }

    @Test
    void testObjectArray() {
        try (Arena arena = Arena.ofConfined()) {
            assertArrayEquals(new MemorySegment[]{MemorySegment.ofAddress(1L), MemorySegment.ofAddress(2L)}, unmarshalAsAddressArray(marshal(arena, new MemorySegment[]{MemorySegment.ofAddress(1L), MemorySegment.ofAddress(2L)})));
            assertArrayEquals(new String[]{"Hello", "world"}, unmarshalAsStringArray(marshal(arena, new String[]{"Hello", "world"})));
            assertArrayEquals(new String[]{"Hello", "UTF-16", "world"}, unmarshalAsStringArray(marshal(arena, new String[]{utf16Str("Hello"), utf16Str("UTF-16"), utf16Str("world")}, StandardCharsets.UTF_16), StandardCharsets.UTF_16));
        }
    }
}
