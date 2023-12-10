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
 * Variable statement
 *
 * @author squid233
 * @since 0.1.0
 */
public final class VariableStatement implements Spec {
    private final String type;
    private final String name;
    private final Spec value;
    private String document = null;
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private boolean isStatic = false;
    private boolean isFinal = false;

    /**
     * Constructor
     *
     * @param type  type
     * @param name  name
     * @param value value
     */
    public VariableStatement(String type, String name, Spec value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    /**
     * Set document
     *
     * @param document document
     * @return this
     */
    public VariableStatement setDocument(String document) {
        this.document = document;
        return this;
    }

    /**
     * Set access modifier
     *
     * @param accessModifier access modifier
     * @return this
     */
    public VariableStatement setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
        return this;
    }

    /**
     * Set static
     *
     * @param isStatic static
     * @return this
     */
    public VariableStatement setStatic(boolean isStatic) {
        this.isStatic = isStatic;
        return this;
    }

    /**
     * Set final
     *
     * @param isFinal final
     * @return this
     */
    public VariableStatement setFinal(boolean isFinal) {
        this.isFinal = isFinal;
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        Spec.appendDocument(builder, document, indentString);
        builder.append(indentString).append(accessModifier);
        if (accessModifier != AccessModifier.PACKAGE_PRIVATE) {
            builder.append(' ');
        }
        if (isStatic) {
            builder.append("static ");
        }
        if (isFinal) {
            builder.append("final ");
        }
        builder.append(type).append(' ').append(name)
            .append(" = ");
        value.append(builder, indent);
        builder.append(";\n");
    }
}
