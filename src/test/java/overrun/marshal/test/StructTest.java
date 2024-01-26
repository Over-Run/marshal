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
            assertEquals(0, vector3.x.get(0L));
            assertEquals(0, vector3.y.get(0L));
            assertEquals(0, vector3.z.get(0L));
            assertEquals(0, vector3.x.get());
            assertEquals(0, vector3.y.get());
            assertEquals(0, vector3.z.get());
            assertDoesNotThrow(() -> {
                vector3.x.set(1);
                vector3.y.set(2);
            });
            assertEquals(1, vector3.x.get(0L));
            assertEquals(2, vector3.y.get(0L));
            assertEquals(1, vector3.x.get());
            assertEquals(2, vector3.y.get());
            assertDoesNotThrow(() -> {
                vector3.x.set(0L, 3);
                vector3.y.set(0L, 4);
            });
            assertEquals(3, vector3.x.get(0L));
            assertEquals(4, vector3.y.get(0L));
            assertEquals(3, vector3.x.get());
            assertEquals(4, vector3.y.get());
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
            assertEquals(1, vector3.x.get());
            assertEquals(2, vector3.y.get());
            assertEquals(3, vector3.z.get());
        }
    }

    @Test
    void testMultipleStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena, 2L);
            vector3.x.set(0L, 1);
            vector3.y.set(0L, 2);
            vector3.x.set(1L, 3);
            vector3.y.set(1L, 4);
            assertEquals(1, vector3.x.get());
            assertEquals(2, vector3.y.get());
            assertEquals(3, vector3.x.get(1L));
            assertEquals(4, vector3.y.get(1L));
        }
    }

    @Test
    void testComplexStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena);
            vector3.x.set(11);
            vector3.y.set(12);

            final ComplexStruct struct = new ComplexStruct(arena);
            struct.Bool.set(true);
            struct.Char.set('1');
            struct.Byte.set((byte) 1);
            struct.Short.set((short) 2);
            struct.Int.set(3);
            struct.Long.set(4L);
            struct.Float.set(5F);
            struct.Double.set(6D);
            struct.Address.set(MemorySegment.ofAddress(7L));
            struct.Str.set(arena, TEST_STRING);
            struct.UTF16Str.set(arena, utf16Str(TEST_UTF16_STRING));
            struct.Addressable.set(vector3);
            struct.Upcall.set(arena, i -> i * 2);

            assertTrue(struct.Bool.get());
            assertEquals('1', struct.Char.get());
            assertEquals((byte) 1, struct.Byte.get());
            assertEquals((short) 2, struct.Short.get());
            assertEquals(3, struct.Int.get());
            assertEquals(4L, struct.Long.get());
            assertEquals(5F, struct.Float.get());
            assertEquals(6D, struct.Double.get());
            assertEquals(MemorySegment.ofAddress(7L), struct.Address.get());
            assertEquals(TEST_STRING, struct.Str.get(TEST_STRING.getBytes(StandardCharsets.UTF_8).length + 1));
            assertEquals(TEST_UTF16_STRING, struct.UTF16Str.get(utf16Str(TEST_UTF16_STRING).getBytes(StandardCharsets.UTF_16).length + 2));
            final Vector3 getVector = struct.Addressable.get();
            assertEquals(11, getVector.x.get());
            assertEquals(12, getVector.y.get());
            assertEquals(0, getVector.z.get());
            assertEquals(84, struct.Upcall.get(arena).invoke(42));
        }
    }
}
