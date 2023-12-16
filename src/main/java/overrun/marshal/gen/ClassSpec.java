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
import java.util.function.Consumer;

/**
 * Class spec
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ClassSpec implements Spec {
    /**
     * Class
     */
    public static final int CLASS = 1;
    /**
     * Interface
     */
    public static final int INTERFACE = 2;
    private final String className;
    private String document = null;
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private boolean isFinal = false;
    private int classType = CLASS;
    private final List<VariableStatement> fieldSpecs = new ArrayList<>();
    private final List<MethodSpec> methodSpecs = new ArrayList<>();
    private final List<AnnotationSpec> annotationSpecs = new ArrayList<>();
    private final List<String> superclasses = new ArrayList<>();

    /**
     * Constructor
     *
     * @param className class name
     */
    public ClassSpec(String className) {
        this.className = className;
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
     * Set access modifier
     *
     * @param accessModifier access modifier
     */
    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    /**
     * Set final
     *
     * @param beFinal final
     */
    public void setFinal(boolean beFinal) {
        this.isFinal = beFinal;
    }

    /**
     * Set class type
     *
     * @param classType class type
     */
    public void setClassType(int classType) {
        this.classType = classType;
    }

    /**
     * Add a field
     *
     * @param fieldSpec field
     */
    public void addField(VariableStatement fieldSpec) {
        fieldSpecs.add(fieldSpec);
    }

    /**
     * Add a field and perform the action
     *
     * @param fieldSpec field
     * @param consumer  action
     */
    public void addField(VariableStatement fieldSpec, Consumer<VariableStatement> consumer) {
        consumer.accept(fieldSpec);
        addField(fieldSpec);
    }

    /**
     * Add a method and perform the action
     *
     * @param methodSpec method
     * @param consumer   action
     */
    public void addMethod(MethodSpec methodSpec, Consumer<MethodSpec> consumer) {
        consumer.accept(methodSpec);
        methodSpecs.add(methodSpec);
    }

    /**
     * Add annotation
     *
     * @param annotationSpec annotation
     */
    public void addAnnotation(AnnotationSpec annotationSpec) {
        annotationSpecs.add(annotationSpec);
    }

    /**
     * Add superclass
     *
     * @param className class name
     */
    public void addSuperclass(String className) {
        superclasses.add(className);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        // document
        Spec.appendDocument(builder, document, indentString);
        // annotation
        annotationSpecs.forEach(annotationSpec -> {
            annotationSpec.append(builder, indent);
            builder.append('\n');
        });
        // declaration
        builder.append(indentString).append(accessModifier);
        if (isFinal && classType == CLASS) {
            builder.append(" final");
        }
        builder.append(' ')
            .append(switch (classType) {
                case CLASS -> "class";
                case INTERFACE -> "interface";
                default ->
                    throw new IllegalStateException("Unsupported class type for " + className + ": " + classType);
            })
            .append(' ').append(className);
        if (!superclasses.isEmpty()) {
            builder.append(" extends ");
            for (int i = 0; i < superclasses.size(); i++) {
                final String superclass = superclasses.get(i);
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(superclass);
            }
        }
        builder.append(" {\n");
        // body
        fieldSpecs.forEach(variableStatement -> variableStatement.append(builder, indent + 4));
        builder.append('\n');
        methodSpecs.forEach(methodSpec -> methodSpec.append(builder, indent + 4));
        // end
        builder.append(indentString).append("}\n");
    }
}
