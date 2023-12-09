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
    private final String className;
    private String document = null;
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private boolean isFinal = false;
    private final List<FieldSpec> fieldSpecs = new ArrayList<>();

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
     * Add a field
     *
     * @param fieldSpec field
     */
    public void addField(FieldSpec fieldSpec) {
        fieldSpecs.add(fieldSpec);
    }

    /**
     * Add a field and perform the action
     *
     * @param fieldSpec field
     * @param consumer  action
     */
    public void addField(FieldSpec fieldSpec, Consumer<FieldSpec> consumer) {
        consumer.accept(fieldSpec);
        addField(fieldSpec);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        // document
        Spec.appendDocument(builder, document, indentString);
        // declaration
        builder.append(indentString).append(accessModifier);
        if (isFinal) {
            builder.append(" final");
        }
        builder.append(" class ").append(className).append(" {\n");
        // body
        fieldSpecs.forEach(fieldSpec -> fieldSpec.append(builder, indent + 4));
        builder.append('\n');
        // end
        builder.append("}\n");
    }
}
