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
public final class MethodSpec implements Annotatable, Spec, StatementBlock {
    private final String returnType;
    private final String name;
    private String document = null;
    private final List<AnnotationSpec> annotations = new ArrayList<>();
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private final List<ParameterSpec> parameters = new ArrayList<>();
    private final List<Spec> statements = new ArrayList<>();
    private boolean isStatic = false;
    private boolean hasMethodBody = true;
    private boolean isDefault = false;

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
    @Override
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
    public void addParameter(Class<?> type, String name) {
        addParameter(type.getSimpleName(), name);
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
     * Set static
     *
     * @param isStatic static
     */
    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    /**
     * Set has method body
     *
     * @param hasMethodBody has method body
     */
    public void setHasMethodBody(boolean hasMethodBody) {
        this.hasMethodBody = hasMethodBody;
    }

    /**
     * Set default
     *
     * @param isDefault default
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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
        builder.append(indentString).append(accessModifier);
        if (accessModifier != AccessModifier.PACKAGE_PRIVATE) {
            builder.append(' ');
        }
        if (isStatic) {
            builder.append("static ");
        }
        if (isDefault) {
            builder.append("default ");
        }
        if (returnType != null) {
            builder.append(returnType).append(' ');
        }
        builder.append(name).append('(');
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
        builder.append(')');
        if (hasMethodBody) {
            builder.append(" {");
            builder.append('\n');
            statements.forEach(spec -> spec.append(builder, indent + 4));
            builder.append(indentString);
            builder.append('}');
        } else {
            builder.append(';');
        }
        builder.append("\n\n");
    }
}
