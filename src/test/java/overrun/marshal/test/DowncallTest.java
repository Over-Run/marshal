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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import overrun.marshal.MemoryStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static overrun.marshal.test.DowncallProvider.TEST_STRING;
import static overrun.marshal.test.DowncallProvider.TEST_UTF16_STRING;

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

    @ParameterizedTest(name = "testDefault(testDefaultNull = [" + ParameterizedTest.INDEX_PLACEHOLDER + "] " + ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER + ")")
    @ValueSource(booleans = {false, true})
    void testDefault(boolean testDefaultNull) {
        IDowncall.getInstance(testDefaultNull).testDefault();
        if (testDefaultNull) {
            assertEquals("testDefault in interface", outputStream.toString());
        } else {
            assertEquals("testDefault", outputStream.toString());
        }
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
        d.testUTF16String(new String(TEST_UTF16_STRING.getBytes(StandardCharsets.UTF_16), StandardCharsets.UTF_16));
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
