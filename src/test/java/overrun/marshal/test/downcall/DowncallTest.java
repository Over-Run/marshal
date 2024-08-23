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

package overrun.marshal.test.downcall;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import overrun.marshal.test.TestUtil;
import overrun.marshal.test.struct.Vector3;
import overrun.marshal.test.upcall.SimpleUpcall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test downcall
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallTest {
    private static IDowncall d;

    @BeforeAll
    static void beforeAll() {
        d = IDowncall.getInstance(false);
    }

    @Test
    void testDefault() {
        assertEquals(42, IDowncall.getInstance(false).testDefault());
        assertEquals(84, IDowncall.getInstance(true).testDefault());
    }

    @Test
    void testUpcall() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(84, d.testUpcall(arena, i -> i * 2));
        }
    }

    @Test
    void testStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = Vector3.OF.of(arena);
            d.testStruct(vector3);
            assertEquals(1, vector3.x());
            assertEquals(2, vector3.y());
            assertEquals(3, vector3.z());
        }
    }

    @Test
    void testReturnInt() {
        assertEquals(42, d.testReturnInt());
    }

    @Test
    void testReturnString() {
        Assertions.assertEquals(TestUtil.TEST_STRING, d.testReturnString());
        assertEquals(TestUtil.TEST_UTF16_STRING, d.testReturnUTF16String());
    }

    @Test
    void testReturnUpcall() {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment upcall = d.testReturnUpcall(arena);
            assertEquals(84, SimpleUpcall.invoke(upcall, 42));
        }
    }

    @Test
    void testReturnStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 returnStruct = d.testReturnStruct();
            assertEquals(4, returnStruct.x());
            assertEquals(5, returnStruct.y());
            assertEquals(6, returnStruct.z());
            final Vector3 returnStructByValue = d.testReturnStructByValue(arena);
            assertEquals(7, returnStructByValue.x());
            assertEquals(8, returnStructByValue.y());
            assertEquals(9, returnStructByValue.z());
        }
    }

    @Test
    void testReturnStructSized() {
        assertStructSized(d.testReturnStructSizedSeg());
        assertStructSized(d.testReturnStructSized());
    }

    private void assertStructSized(Vector3 vector3) {
        assertEquals(1, vector3.x());
        assertEquals(2, vector3.y());
        assertEquals(3, vector3.z());
        assertEquals(4, vector3.slice(1L).x());
        assertEquals(5, vector3.slice(1L).y());
        assertEquals(6, vector3.slice(1L).z());
    }

    @Test
    void testReturnIntArray() {
        assertArrayEquals(new int[]{4, 2}, d.testReturnIntArray());
    }

    @Test
    void testReturnSizedSeg() {
        final MemorySegment segment = d.testReturnSizedSeg();
        assertEquals(4L, segment.byteSize());
        assertEquals(8, segment.get(ValueLayout.JAVA_INT, 0));
    }

    @Test
    void testRefIntArray() {
        int[] arr = {0};
        d.testRefIntArray(arr);
        assertArrayEquals(new int[]{8}, arr);
    }

    @Test
    void testCritical() {
        d.testCriticalFalse();
        int[] arr = {0};
        d.testCriticalTrue(arr);
        assertArrayEquals(new int[]{8}, arr);
    }

    @Test
    void testConvertBoolean() {
        assertTrue(d.testConvertBoolean(false));
        assertFalse(d.testConvertBoolean(true));
    }

    @Test
    void testDuplicateLoading() {
        assertEquals(42, IDowncall.getInstance(false).testReturnInt());
        assertEquals(42, IDowncall.getInstance(false).testReturnInt());
    }

    @Test
    void testDefaultMethodHandle() {
        assertEquals(42, IDowncall.getInstance(false).testDefaultMethodHandle());
        assertEquals("Thrown from interface",
            assertThrows(RuntimeException.class, () -> IDowncall.getInstance(true).testDefaultMethodHandle())
                .getCause()
                .getMessage());
    }
}
