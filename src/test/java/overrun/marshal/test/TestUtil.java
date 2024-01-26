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

import java.nio.charset.StandardCharsets;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class TestUtil {
    public static final String TEST_STRING = "Hello world";
    public static final String TEST_UTF16_STRING = "Hello UTF-16 world";

    public static String utf16Str(String utf8Str) {
        return new String(utf8Str.getBytes(StandardCharsets.UTF_16), StandardCharsets.UTF_16);
    }
}
