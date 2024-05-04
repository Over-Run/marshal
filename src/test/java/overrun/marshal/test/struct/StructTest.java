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
            final Vector3 vector3 = Vector3.OF.of(arena);
            assertEquals(0, vector3.slice(0L).x());
            assertEquals(0, vector3.slice(0L).y());
            assertEquals(0, vector3.slice(0L).z());
            assertEquals(0, vector3.x());
            assertEquals(0, vector3.y());
            assertEquals(0, vector3.z());
            assertDoesNotThrow(() -> {
                vector3.x(1)
                    .y(2);
            });
            assertEquals(1, vector3.slice(0L).x());
            assertEquals(2, vector3.slice(0L).y());
            assertEquals(1, vector3.x());
            assertEquals(2, vector3.y());
            assertDoesNotThrow(() -> {
                vector3.slice(0L)
                    .x(3)
                    .y(4);
            });
            assertEquals(3, vector3.slice(0L).x());
            assertEquals(4, vector3.slice(0L).y());
            assertEquals(3, vector3.x());
            assertEquals(4, vector3.y());
        }
    }

    @Test
    void testInitializedStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment segment = arena.allocate(Vector3.OF.layout());
            segment.set(ValueLayout.JAVA_INT, 0L, 1);
            segment.set(ValueLayout.JAVA_INT, 4L, 2);
            segment.set(ValueLayout.JAVA_INT, 8L, 3);
            final Vector3 vector3 = Vector3.OF.of(segment, 1L);
            assertEquals(1, vector3.x());
            assertEquals(2, vector3.y());
            assertEquals(3, vector3.z());
        }
    }

    @Test
    void testMultipleStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = Vector3.OF.of(arena, 2L);
            vector3.slice(0L).x(1).y(2);
            vector3.slice(1L).x(3).y(4);
            assertEquals(1, vector3.x());
            assertEquals(2, vector3.y());
            assertEquals(3, vector3.slice(1L).x());
            assertEquals(4, vector3.slice(1L).y());
        }
    }

    @Test
    void testComplexStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = Vector3.OF.of(arena);
            vector3.x(11);
            vector3.y(12);

            final ComplexStruct struct = ComplexStruct.OF.of(arena);
            struct.Bool(true)
                .Char('1')
                .Byte((byte) 1)
                .Short((short) 2)
                .Int(3)
                .Long(4L)
                .Float(5F)
                .Double(6D)
                .Address(MemorySegment.ofAddress(7L))
                .javaStr(arena, TEST_STRING)
                .javaUTF16Str(arena, utf16Str(TEST_UTF16_STRING))
                .javaAddressable(vector3)
                .javaUpcall(arena, i -> i * 2)
                .javaIntArray(arena, new int[]{8, 9});

            assertTrue(struct.Bool());
            assertEquals('1', struct.Char());
            assertEquals((byte) 1, struct.Byte());
            assertEquals((short) 2, struct.Short());
            assertEquals(3, struct.Int());
            assertEquals(4L, struct.Long());
            assertEquals(5F, struct.Float());
            assertEquals(6D, struct.Double());
            assertEquals(MemorySegment.ofAddress(7L), struct.Address());
            assertEquals(TEST_STRING, struct.javaStr());
            assertEquals(TEST_UTF16_STRING, struct.javaUTF16Str());
            final Vector3 getVector = struct.javaAddressable();
            assertEquals(11, getVector.x());
            assertEquals(12, getVector.y());
            assertEquals(0, getVector.z());
            assertEquals(84, struct.javaUpcall().invoke(42));
            assertArrayEquals(new int[]{8, 9}, struct.javaIntArray(2));

            assertEquals(3, struct.slice(0L).Int());
        }
    }

    @Test
    void testSizedArrayInStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final SizedArrayInStruct struct = SizedArrayInStruct.OF.of(arena);
            assertEquals(0, struct.arr(0L));
            assertEquals(0, struct.arr(1L));
            struct.arr(0L, 4)
                .arr(1L, 2);
            assertEquals(4, struct.arr(0L));
            assertEquals(2, struct.arr(1L));
        }
    }

    @Test
    void testNestedStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final NestedStruct struct = NestedStruct.OF.of(arena);
            assertEquals(0, struct.vec3$x());
            assertEquals(0, struct.vec3$y());
            assertEquals(0, struct.vec3$z());
            assertEquals(0, struct.vec3arr$x(0));
            assertEquals(0, struct.vec3arr$y(0));
            assertEquals(0, struct.vec3arr$z(0));
            assertEquals(0, struct.vec3arr$x(1));
            assertEquals(0, struct.vec3arr$y(1));
            assertEquals(0, struct.vec3arr$z(1));
            struct.vec3$x(1)
                .vec3$y(2)
                .vec3$z(3)
                .vec3arr$x(0, 4)
                .vec3arr$y(0, 5)
                .vec3arr$z(0, 6)
                .vec3arr$x(1, 7)
                .vec3arr$y(1, 8)
                .vec3arr$z(1, 9);
            assertEquals(1, struct.vec3$x());
            assertEquals(2, struct.vec3$y());
            assertEquals(3, struct.vec3$z());
            assertEquals(4, struct.vec3arr$x(0));
            assertEquals(5, struct.vec3arr$y(0));
            assertEquals(6, struct.vec3arr$z(0));
            assertEquals(7, struct.vec3arr$x(1));
            assertEquals(8, struct.vec3arr$y(1));
            assertEquals(9, struct.vec3arr$z(1));
        }
    }
}
