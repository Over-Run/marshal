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

import java.lang.reflect.AnnotatedElement;

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
     * Checks the given annotation
     *
     * @param strCharset strCharset
     * @return {@code true} if {@code strCharset} is not null and has value
     */
    public static boolean hasCharset(StrCharset strCharset) {
        return strCharset != null && !strCharset.value().isBlank();
    }

    /**
     * Gets the charset from the given element
     *
     * @param element element
     * @return the charset
     */
    public static String getCharset(AnnotatedElement element) {
        final StrCharset strCharset = element.getDeclaredAnnotation(StrCharset.class);
        return hasCharset(strCharset) ? strCharset.value() : null;
    }
}
