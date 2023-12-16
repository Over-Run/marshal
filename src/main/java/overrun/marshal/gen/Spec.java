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

import java.util.stream.Collectors;

/**
 * Spec
 *
 * @author squid233
 * @since 0.1.0
 */
public interface Spec {
    /**
     * Create simple class name spec
     *
     * @param clazz class
     * @return spec
     */
    static Spec simpleClassName(Class<?> clazz) {
        return literal(clazz.getSimpleName());
    }

    /**
     * Create class name spec
     *
     * @param clazz class
     * @return spec
     */
    static Spec className(Class<?> clazz) {
        return literal(clazz.getCanonicalName());
    }

    /**
     * Create access spec
     *
     * @param object object
     * @param member member
     * @return spec
     */
    static Spec accessSpec(Class<?> object, String member) {
        return literal(object.getSimpleName() + '.' + member);
    }

    /**
     * Create access spec
     *
     * @param object object
     * @param member member
     * @return spec
     */
    static Spec accessSpec(Class<?> object, Class<?> member) {
        return literal(object.getSimpleName() + '.' + member.getSimpleName());
    }

    /**
     * Create access spec
     *
     * @param object object
     * @param member member
     * @return spec
     */
    static Spec accessSpec(String object, String member) {
        return literal(object + '.' + member);
    }

    /**
     * Create ternary operator spec
     *
     * @param condition  condition
     * @param trueValue  true value
     * @param falseValue false value
     * @return spec
     */
    static Spec ternaryOp(Spec condition, Spec trueValue, Spec falseValue) {
        return (builder, indent) -> {
            builder.append('(');
            condition.append(builder, indent);
            builder.append(") ? (");
            trueValue.append(builder, indent);
            builder.append(") : (");
            falseValue.append(builder, indent);
            builder.append(')');
        };
    }

    /**
     * Create not null spec
     *
     * @param value value
     * @return spec
     */
    static Spec notNullSpec(String value) {
        return literal(value + " != null");
    }

    /**
     * Create an expression casting
     *
     * @param type type
     * @param exp  expression
     * @return cast
     */
    static Spec cast(String type, Spec exp) {
        return (builder, indent) -> {
            builder.append('(').append(type).append(") ");
            exp.append(builder, indent);
        };
    }

    /**
     * Create a throw statement
     *
     * @param exception exception
     * @return statement
     */
    static Spec throwStatement(Spec exception) {
        return (builder, indent) -> {
            builder.append(indentString(indent)).append("throw ");
            exception.append(builder, indent);
            builder.append(";\n");
        };
    }

    /**
     * Create a return statement
     *
     * @param value return value
     * @return statement
     */
    static Spec returnStatement(Spec value) {
        return (builder, indent) -> {
            builder.append(indentString(indent)).append("return ");
            value.append(builder, indent);
            builder.append(";\n");
        };
    }

    /**
     * Create a statement
     *
     * @param spec the code
     * @return the statement
     */
    static Spec statement(Spec spec) {
        return (builder, indent) -> {
            builder.append(indentString(indent));
            spec.append(builder, indent);
            builder.append(";\n");
        };
    }

    /**
     * Create a literal string
     *
     * @param s the string
     * @return the spec
     */
    static Spec literal(String s) {
        return (builder, indent) -> builder.append(s);
    }

    /**
     * Create an indented literal string
     *
     * @param s the string
     * @return the spec
     */
    static Spec indented(String s) {
        return (builder, indent) -> builder.append(s.indent(indent));
    }

    /**
     * Create an indented literal string
     *
     * @param s the string
     * @return the spec
     */
    static Spec indentedExceptFirstLine(String s) {
        return (builder, indent) -> {
            final String indentString = indentString(indent);
            s.lines().findFirst().ifPresentOrElse(s1 -> {
                builder.append(s1);
                if (s.lines().count() > 1) {
                    builder.append(s.lines().skip(1).map(s2 -> indentString + s2).collect(Collectors.joining("\n", "\n", "")));
                }
            }, () -> {
            });
        };
    }

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
