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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Annotation
 *
 * @author squid233
 * @since 0.1.0
 */
public final class AnnotationSpec implements Spec {
    private final String type;
    private final Map<String, String> arguments = new LinkedHashMap<>();

    /**
     * Constructor
     *
     * @param type type
     */
    public AnnotationSpec(String type) {
        this.type = type;
    }

    /**
     * Add a argument
     *
     * @param name  name
     * @param value value
     * @return this
     */
    public AnnotationSpec addArgument(String name, String value) {
        arguments.put(name, value);
        return this;
    }

    /**
     * Also runs the action
     *
     * @param consumer the action
     * @return this
     */
    public AnnotationSpec also(Consumer<AnnotationSpec> consumer) {
        consumer.accept(this);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        builder.append('@').append(type);
        if (!arguments.isEmpty()) {
            builder.append('(');
            if (arguments.size() == 1 && "value".equals(arguments.keySet().stream().findFirst().orElse(null))) {
                builder.append(arguments.get("value"));
            } else {
                builder.append(arguments.entrySet().stream()
                    .map(e -> e.getKey() + " = " + e.getValue())
                    .collect(Collectors.joining(", ")));
            }
            builder.append(')');
        }
    }
}
