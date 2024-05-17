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

package overrun.marshal.internal;

import overrun.marshal.gen.StrCharset;

import java.lang.classfile.CodeBuilder;
import java.lang.reflect.AnnotatedElement;
import java.util.Locale;

import static overrun.marshal.internal.Constants.*;

/**
 * String charsets
 *
 * @author squid233
 * @since 0.1.0
 */
public final class StringCharset {
    private StringCharset() {
    }

    /**
     * {@return hasCharset}
     *
     * @param strCharset strCharset
     */
    public static boolean hasCharset(StrCharset strCharset) {
        return strCharset != null && !strCharset.value().isBlank();
    }

    /**
     * {@return getCharset}
     *
     * @param element element
     */
    public static String getCharset(AnnotatedElement element) {
        final StrCharset strCharset = element.getDeclaredAnnotation(StrCharset.class);
        return hasCharset(strCharset) ? strCharset.value() : null;
    }

    /**
     * getCharset
     *
     * @param codeBuilder codeBuilder
     * @param charset     charset
     */
    public static void getCharset(CodeBuilder codeBuilder, String charset) {
        final String upperCase = charset.toUpperCase(Locale.ROOT);
        switch (upperCase) {
            case "UTF-8", "ISO-8859-1", "US-ASCII",
                 "UTF-16", "UTF-16BE", "UTF-16LE",
                 "UTF-32", "UTF-32BE", "UTF-32LE" ->
                codeBuilder.getstatic(CD_StandardCharsets, upperCase.replace('-', '_'), CD_Charset);
            case "UTF_8", "ISO_8859_1", "US_ASCII",
                 "UTF_16", "UTF_16BE", "UTF_16LE",
                 "UTF_32", "UTF_32BE", "UTF_32LE" -> codeBuilder.getstatic(CD_StandardCharsets, upperCase, CD_Charset);
            default -> codeBuilder.ldc(charset)
                .invokestatic(CD_Charset, "forName", MTD_Charset_String);
        }
    }

    /**
     * {@return getCharset}
     *
     * @param codeBuilder codeBuilder
     * @param element     element
     */
    public static boolean getCharset(CodeBuilder codeBuilder, AnnotatedElement element) {
        final String charset = getCharset(element);
        if (charset != null) {
            getCharset(codeBuilder, charset);
            return true;
        }
        return false;
    }
}
