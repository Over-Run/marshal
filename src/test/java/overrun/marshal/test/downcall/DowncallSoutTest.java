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

import org.junit.jupiter.api.*;
import overrun.marshal.MemoryStack;
import overrun.marshal.test.MyEnum;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SegmentAllocator;

import static org.junit.jupiter.api.Assertions.*;
import static overrun.marshal.test.TestUtil.*;

/**
 * Test standard output
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallSoutTest {
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
    void testSizedIntArray() {
        assertThrowsExactly(IllegalArgumentException.class, () -> d.testSizedIntArray(new int[0]));
        assertDoesNotThrow(() -> d.testSizedIntArray(new int[]{4, 2}));
        assertEquals("[4, 2]", outputStream.toString());
    }
}
