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
 * @author squid233
 * @since 0.1.0
 */
public final class ClassSpec implements Spec {
    private final String className;
    private String document = null;
    private AccessModifier accessModifier = AccessModifier.PUBLIC;
    private boolean isFinal = false;
    private final List<FieldSpec> fieldSpecs = new ArrayList<>();

    public ClassSpec(String className) {
        this.className = className;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public void setAccessModifier(AccessModifier accessModifier) {
        this.accessModifier = accessModifier;
    }

    public void setFinal(boolean beFinal) {
        this.isFinal = beFinal;
    }

    public void addField(FieldSpec fieldSpec) {
        fieldSpecs.add(fieldSpec);
    }

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
        builder.append(isFinal).append("}\n");
    }
}
