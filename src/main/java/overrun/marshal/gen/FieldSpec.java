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

import overrun.marshal.AccessModifier;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class FieldSpec implements Spec {
    private final String type;
    private final String name;
    private final String value;
    private String document = null;
    private AccessModifier accessModifier = AccessModifier.PUBLIC;

    public FieldSpec(String type, String name, String value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        Spec.appendDocument(builder, document, indentString);
        builder.append(indentString)
            .append(accessModifier).append(" static final ").append(type).append(' ').append(name)
            .append(" = ")
            .append(value)
            .append(";\n");
    }
}
