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

package overrun.marshal.test.struct;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static overrun.marshal.test.TestUtil.*;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class StructTest {
    @Test
    void testSingleStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena);
            assertEquals(0, Vector3.x.get(vector3, 0L));
            assertEquals(0, Vector3.y.get(vector3, 0L));
            assertEquals(0, Vector3.z.get(vector3, 0L));
            assertEquals(0, Vector3.x.get(vector3));
            assertEquals(0, Vector3.y.get(vector3));
            assertEquals(0, Vector3.z.get(vector3));
            assertDoesNotThrow(() -> {
                Vector3.x.set(vector3, 1);
                Vector3.y.set(vector3, 2);
            });
            assertEquals(1, Vector3.x.get(vector3, 0L));
            assertEquals(2, Vector3.y.get(vector3, 0L));
            assertEquals(1, Vector3.x.get(vector3));
            assertEquals(2, Vector3.y.get(vector3));
            assertDoesNotThrow(() -> {
                Vector3.x.set(vector3, 0L, 3);
                Vector3.y.set(vector3, 0L, 4);
            });
            assertEquals(3, Vector3.x.get(vector3, 0L));
            assertEquals(4, Vector3.y.get(vector3, 0L));
            assertEquals(3, Vector3.x.get(vector3));
            assertEquals(4, Vector3.y.get(vector3));
        }
    }

    @Test
    void testInitializedStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment segment = arena.allocate(Vector3.LAYOUT);
            segment.set(ValueLayout.JAVA_INT, 0L, 1);
            segment.set(ValueLayout.JAVA_INT, 4L, 2);
            segment.set(ValueLayout.JAVA_INT, 8L, 3);
            final Vector3 vector3 = new Vector3(segment, 1L);
            assertEquals(1, Vector3.x.get(vector3));
            assertEquals(2, Vector3.y.get(vector3));
            assertEquals(3, Vector3.z.get(vector3));
        }
    }

    @Test
    void testMultipleStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena, 2L);
            Vector3.x.set(vector3, 0L, 1);
            Vector3.y.set(vector3, 0L, 2);
            Vector3.x.set(vector3, 1L, 3);
            Vector3.y.set(vector3, 1L, 4);
            assertEquals(1, Vector3.x.get(vector3));
            assertEquals(2, Vector3.y.get(vector3));
            assertEquals(3, Vector3.x.get(vector3, 1L));
            assertEquals(4, Vector3.y.get(vector3, 1L));
        }
    }

    @Test
    void testComplexStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena);
            Vector3.x.set(vector3, 11);
            Vector3.y.set(vector3, 12);

            final ComplexStruct struct = new ComplexStruct(arena);
            ComplexStruct.Bool.set(struct, true);
            ComplexStruct.Char.set(struct, '1');
            ComplexStruct.Byte.set(struct, (byte) 1);
            ComplexStruct.Short.set(struct, (short) 2);
            ComplexStruct.Int.set(struct, 3);
            ComplexStruct.Long.set(struct, 4L);
            ComplexStruct.Float.set(struct, 5F);
            ComplexStruct.Double.set(struct, 6D);
            ComplexStruct.Address.set(struct, MemorySegment.ofAddress(7L));
            ComplexStruct.Str.set(struct, arena, TEST_STRING);
            ComplexStruct.UTF16Str.set(struct, arena, utf16Str(TEST_UTF16_STRING));
            ComplexStruct.Addressable.set(struct, vector3);
            ComplexStruct.Upcall.set(struct, arena, i -> i * 2);
            ComplexStruct.IntArray.set(struct, arena, new int[]{8, 9});

            assertTrue(ComplexStruct.Bool.get(struct));
            assertEquals('1', ComplexStruct.Char.get(struct));
            assertEquals((byte) 1, ComplexStruct.Byte.get(struct));
            assertEquals((short) 2, ComplexStruct.Short.get(struct));
            assertEquals(3, ComplexStruct.Int.get(struct));
            assertEquals(4L, ComplexStruct.Long.get(struct));
            assertEquals(5F, ComplexStruct.Float.get(struct));
            assertEquals(6D, ComplexStruct.Double.get(struct));
            assertEquals(MemorySegment.ofAddress(7L), ComplexStruct.Address.get(struct));
            assertEquals(TEST_STRING, ComplexStruct.Str.get(struct, TEST_STRING.getBytes(StandardCharsets.UTF_8).length + 1));
            assertEquals(TEST_UTF16_STRING, ComplexStruct.UTF16Str.get(struct, utf16Str(TEST_UTF16_STRING).getBytes(StandardCharsets.UTF_16).length + 2));
            final Vector3 getVector = ComplexStruct.Addressable.get(struct);
            assertEquals(11, Vector3.x.get(getVector));
            assertEquals(12, Vector3.y.get(getVector));
            assertEquals(0, Vector3.z.get(getVector));
            assertEquals(84, ComplexStruct.Upcall.get(struct, arena).invoke(42));
            assertArrayEquals(new int[]{8, 9}, ComplexStruct.IntArray.get(struct, ValueLayout.JAVA_INT.scale(0L, 2L)));
        }
    }

    @Test
    void testSizedArrayInStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final SizedArrayInStruct struct = new SizedArrayInStruct(arena);
            assertEquals(0, SizedArrayInStruct.arr.get(struct, 0L));
            assertEquals(0, SizedArrayInStruct.arr.get(struct, 1L));
            SizedArrayInStruct.arr.set(struct, 0L, 4);
            SizedArrayInStruct.arr.set(struct, 1L, 2);
            assertEquals(4, SizedArrayInStruct.arr.get(struct, 0L));
            assertEquals(2, SizedArrayInStruct.arr.get(struct, 1L));
        }
    }
}
