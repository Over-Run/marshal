/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

package overrun.marshal.gen;

/**
 * Spec
 *
 * @author squid233
 * @since 0.1.0
 */
public interface Spec {
    /**
     * Get the indent string
     *
     * @param indent indent count
     * @return the string
     */
    static String indentString(int indent) {
        return " ".repeat(indent);
    }

    /**
     * Append document to the string builder
     *
     * @param builder   the string builder
     * @param document  the document
     * @param indentStr the indent string
     */
    static void appendDocument(StringBuilder builder, String document, String indentStr) {
        if (document != null) {
            builder.append(indentStr).append("/**\n");
            document.lines().forEach(s -> builder.append(indentStr).append(" *").append(s).append('\n'));
            builder.append(indentStr).append(" */\n");
        }
    }

    /**
     * Append to the string builder
     *
     * @param builder the string builder
     * @param indent  the indent
     */
    void append(StringBuilder builder, int indent);
}
