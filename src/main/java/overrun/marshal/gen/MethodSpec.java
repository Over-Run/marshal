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

import java.util.ArrayList;
import java.util.List;

/**
 * Method spec
 *
 * @author squid233
 * @since 0.1.0
 */
public final class MethodSpec implements Spec, StatementBlock {
    private final String returnType;
    private final String name;
    private String document = null;
    private final List<AnnotationSpec> annotations = new ArrayList<>();
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private final List<ParameterSpec> parameters = new ArrayList<>();
    private final List<Spec> statements = new ArrayList<>();

    /**
     * Constructor
     *
     * @param returnType return type
     * @param name       name
     */
    public MethodSpec(String returnType, String name) {
        this.returnType = returnType;
        this.name = name;
    }

    /**
     * Set document
     *
     * @param document document
     */
    public void setDocument(String document) {
        this.document = document;
    }

    /**
     * Add an annotation
     *
     * @param annotationSpec annotation
     */
    public void addAnnotation(AnnotationSpec annotationSpec) {
        annotations.add(annotationSpec);
    }

    /**
     * Set access modifier
     *
     * @param accessModifier access modifier
     */
    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    /**
     * Add a parameter
     *
     * @param parameterSpec parameter
     */
    public void addParameter(ParameterSpec parameterSpec) {
        parameters.add(parameterSpec);
    }

    /**
     * Add a parameter
     *
     * @param type type
     * @param name name
     */
    public void addParameter(String type, String name) {
        addParameter(new ParameterSpec(type, name));
    }

    /**
     * Add a statement
     *
     * @param spec statement
     */
    @Override
    public void addStatement(Spec spec) {
        statements.add(spec);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        final String indentString4 = Spec.indentString(indent + 4);
        final int size = parameters.size();
        final boolean separateLine = size >= 5;
        Spec.appendDocument(builder, document, indentString);
        annotations.forEach(annotationSpec -> {
            builder.append(indentString);
            annotationSpec.append(builder, indent);
            builder.append('\n');
        });
        builder.append(indentString).append(accessModifier).append(" static ").append(returnType).append(' ').append(name).append('(');
        if (separateLine) {
            builder.append('\n').append(indentString4);
        }
        for (int i = 0; i < size; i++) {
            ParameterSpec parameterSpec = parameters.get(i);
            if (i != 0) {
                builder.append(",");
                if (separateLine) {
                    builder.append("\n").append(indentString4);
                } else {
                    builder.append(' ');
                }
            }
            parameterSpec.append(builder, indent);
        }
        if (separateLine) {
            builder.append('\n').append(indentString);
        }
        builder.append(") {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append("}\n\n");
    }
}
