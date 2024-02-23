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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
            final Vector3 vector3 = new Vector3(arena);
            d.testStruct(vector3);
            assertEquals(1, Vector3.x.get(vector3));
            assertEquals(2, Vector3.y.get(vector3));
            assertEquals(3, Vector3.z.get(vector3));
        }
    }

    @Test
    void testReturnInt() {
        assertEquals(42, d.testReturnInt());
    }

    @Test
    void testReturnString() {
        assertEquals(TestUtil.TEST_STRING, d.testReturnString());
        assertEquals(TestUtil.TEST_UTF16_STRING, d.testReturnUTF16String());
    }

    @Test
    void testReturnCEnum() {
        assertEquals(MyEnum.B, d.testReturnCEnum());
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
            assertEquals(4, Vector3.x.get(returnStruct));
            assertEquals(5, Vector3.y.get(returnStruct));
            assertEquals(6, Vector3.z.get(returnStruct));
            final Vector3 returnStructByValue = d.testReturnStructByValue(arena);
            assertEquals(7, Vector3.x.get(returnStructByValue));
            assertEquals(8, Vector3.y.get(returnStructByValue));
            assertEquals(9, Vector3.z.get(returnStructByValue));
        }
    }

    @Test
    void testReturnStructSized() {
        assertStructSized(d.testReturnStructSizedSeg());
        assertStructSized(d.testReturnStructSized());
    }

    private void assertStructSized(Vector3 vector3) {
        assertEquals(1, Vector3.x.get(vector3));
        assertEquals(2, Vector3.y.get(vector3));
        assertEquals(3, Vector3.z.get(vector3));
        assertEquals(4, Vector3.x.get(vector3, 1L));
        assertEquals(5, Vector3.y.get(vector3, 1L));
        assertEquals(6, Vector3.z.get(vector3, 1L));
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
