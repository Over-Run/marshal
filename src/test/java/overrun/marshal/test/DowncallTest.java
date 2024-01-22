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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
    void beforeEach() {
        stdout = System.out;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void afterEach() {
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
        Assertions.assertEquals("test", outputStream.toString());
    }

    @Test
    void testWithEntrypoint() {
        d.testWithEntrypoint();
        Assertions.assertEquals("test", outputStream.toString());
    }

    @Test
    void testSkip() {
        d.testSkip();
        Assertions.assertEquals("testSkip", outputStream.toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testDefault(boolean testDefaultNull) {
        IDowncall.getInstance(testDefaultNull).testDefault();
        if (testDefaultNull) {
            Assertions.assertEquals("testDefault in interface", outputStream.toString());
        } else {
            Assertions.assertEquals("testDefault", outputStream.toString());
        }
    }

    @Test
    void test_int() {
        d.test_int(42);
        Assertions.assertEquals("42", outputStream.toString());
    }

    @Test
    void test_String() {
        d.test_String("Hello world");
        Assertions.assertEquals("Hello world", outputStream.toString());
    }

    @Test
    void test_UTF16String() {
        d.test_UTF16String(new String("Hello UTF-16 world".getBytes(StandardCharsets.UTF_16), StandardCharsets.UTF_16));
        Assertions.assertEquals("Hello UTF-16 world", outputStream.toString());
    }

    @Test
    void test_CEnum() {
        d.test_CEnum(MyEnum.A);
        d.test_CEnum(MyEnum.B);
        d.test_CEnum(MyEnum.C);
        Assertions.assertEquals("024", outputStream.toString());
    }
}
