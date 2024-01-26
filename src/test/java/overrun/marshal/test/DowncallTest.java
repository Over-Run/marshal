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

import org.junit.jupiter.api.*;
import overrun.marshal.MemoryStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;
import static overrun.marshal.test.TestUtil.*;

/**
 * Test downcall
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallTest {
    private static IDowncall d;
    private static PrintStream stdout = null;
    private static ByteArrayOutputStream outputStream = null;

    @BeforeAll
    static void beforeAll() {
        d = IDowncall.getInstance(false);
    }

    @BeforeEach
    void setUp() {
        stdout = System.out;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(stdout);
        outputStream.reset();
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (outputStream != null) {
            outputStream.close();
        }
    }

    @Test
    void test() {
        d.test();
        assertEquals("test", outputStream.toString());
    }

    @Test
    void testWithEntrypoint() {
        d.testWithEntrypoint();
        assertEquals("test", outputStream.toString());
    }

    @Test
    void testSkip() {
        d.testSkip();
        assertEquals("testSkip", outputStream.toString());
    }

    @Test
    void testDefaultInInterface() {
        IDowncall.getInstance(true).testDefault();
        assertEquals("testDefault in interface", outputStream.toString());
    }

    @Test
    void testDefault() {
        IDowncall.getInstance(false).testDefault();
        assertEquals("testDefault", outputStream.toString());
    }

    @Test
    void testInt() {
        d.testInt(42);
        assertEquals("42", outputStream.toString());
    }

    @Test
    void testString() {
        d.testString(TEST_STRING);
        assertEquals(TEST_STRING, outputStream.toString());
    }

    @Test
    void testUTF16String() {
        d.testUTF16String(utf16Str(TEST_UTF16_STRING));
        assertEquals(TEST_UTF16_STRING, outputStream.toString());
    }

    @Test
    void testCEnum() {
        d.testCEnum(MyEnum.A);
        d.testCEnum(MyEnum.B);
        d.testCEnum(MyEnum.C);
        assertEquals("024", outputStream.toString());
    }

    @Test
    void testUpcall() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(84, d.testUpcall(arena, i -> i * 2));
        }
    }

    @Test
    void testIntArray() {
        d.testIntArray(new int[]{4, 2});
        try (Arena arena = Arena.ofConfined()) {
            d.testIntArray((SegmentAllocator) arena, new int[]{4, 2});
            d.testIntArray(arena, new int[]{4, 2});
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            d.testIntArray(stack, new int[]{4, 2});
        }
        d.testVarArgsJava(2, 4, 2);
        d.testVarArgsJava(0);
        assertEquals("[4, 2][4, 2][4, 2][4, 2][4, 2][]", outputStream.toString());
    }

    @Test
    void testStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 vector3 = new Vector3(arena);
            d.testStruct(vector3);
            assertEquals(1, vector3.x.get());
            assertEquals(2, vector3.y.get());
            assertEquals(3, vector3.z.get());
        }
    }

    @Test
    void testReturnInt() {
        assertEquals(42, d.testReturnInt());
    }

    @Test
    void testReturnString() {
        assertEquals(TEST_STRING, d.testReturnString());
        assertEquals(TEST_UTF16_STRING, d.testReturnUTF16String());
    }

    @Test
    void testReturnCEnum() {
        assertEquals(MyEnum.B, d.testReturnCEnum());
    }

    @Test
    void testReturnUpcall() {
        try (Arena arena = Arena.ofConfined()) {
            final SimpleUpcall upcall = d.testReturnUpcall(arena);
            assertEquals(84, upcall.invoke(42));
        }
    }

    @Test
    void testReturnStruct() {
        try (Arena arena = Arena.ofConfined()) {
            final Vector3 returnStruct = d.testReturnStruct();
            assertEquals(4, returnStruct.x.get());
            assertEquals(5, returnStruct.y.get());
            assertEquals(6, returnStruct.z.get());
            final Vector3 returnStructByValue = d.testReturnStructByValue(arena);
            assertEquals(7, returnStructByValue.x.get());
            assertEquals(8, returnStructByValue.y.get());
            assertEquals(9, returnStructByValue.z.get());
        }
    }

    @Test
    void testReturnStructSized() {
        assertStructSized(d.testReturnStructSizedSeg());
        assertStructSized(d.testReturnStructSized());
    }

    private void assertStructSized(Vector3 vector3) {
        assertEquals(1, vector3.x.get());
        assertEquals(2, vector3.y.get());
        assertEquals(3, vector3.z.get());
        assertEquals(4, vector3.x.get(1L));
        assertEquals(5, vector3.y.get(1L));
        assertEquals(6, vector3.z.get(1L));
    }

    @Test
    void testReturnIntArray() {
        assertArrayEquals(new int[]{4, 2}, d.testReturnIntArray());
    }

    @Test
    void testSizedIntArray() {
        assertThrowsExactly(IllegalArgumentException.class, () -> d.testSizedIntArray(new int[0]));
        assertDoesNotThrow(() -> d.testSizedIntArray(new int[]{4, 2}));
        assertEquals("[4, 2]", outputStream.toString());
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
}
