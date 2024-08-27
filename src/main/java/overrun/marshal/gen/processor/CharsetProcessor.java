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

package overrun.marshal.gen.processor;

import java.lang.classfile.CodeBuilder;
import java.util.Locale;

import static overrun.marshal.internal.Constants.*;

/**
 * Insert bytecode representing a charset instance.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class CharsetProcessor {
    private CharsetProcessor() {
    }

    /**
     * Insert bytecode representing a charset instance of the given charset name.
     *
     * @param builder the code builder
     * @param charset the charset name
     * @return {@code true} if the charset is not null; otherwise {@code false}
     */
    public static boolean process(CodeBuilder builder, String charset) {
        if (charset == null) {
            return false;
        }
        String upperCase = charset.toUpperCase(Locale.ROOT);
        switch (upperCase) {
            case "UTF-8", "ISO-8859-1", "US-ASCII",
                 "UTF-16", "UTF-16BE", "UTF-16LE",
                 "UTF-32", "UTF-32BE", "UTF-32LE" ->
                builder.getstatic(CD_StandardCharsets, upperCase.replace('-', '_'), CD_Charset);
            case "UTF_8", "ISO_8859_1", "US_ASCII",
                 "UTF_16", "UTF_16BE", "UTF_16LE",
                 "UTF_32", "UTF_32BE", "UTF_32LE" -> builder.getstatic(CD_StandardCharsets, upperCase, CD_Charset);
            default -> builder.ldc(upperCase)
                .invokestatic(CD_Charset, "forName", MTD_Charset_String);
        }
        return true;
    }
}
