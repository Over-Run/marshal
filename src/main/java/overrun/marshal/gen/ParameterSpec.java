/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Method parameter
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ParameterSpec implements Annotatable, Spec {
    private final String type;
    private final String name;
    private final List<AnnotationSpec> annotations = new ArrayList<>();

    /**
     * Constructor
     *
     * @param type type
     * @param name name
     */
    public ParameterSpec(String type, String name) {
        this.type = type;
        this.name = name;
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
     * Also runs the action
     *
     * @param consumer the action
     * @return this
     */
    public ParameterSpec also(Consumer<ParameterSpec> consumer) {
        consumer.accept(this);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        annotations.forEach(annotationSpec -> {
            annotationSpec.append(builder, indent);
            builder.append(' ');
        });
        builder.append(type).append(' ').append(name);
    }
}
