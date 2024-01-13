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

package overrun.marshal.gen1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Invoke
 *
 * @author squid233
 * @since 0.1.0
 */
public final class InvokeSpec implements Spec {
    private final Spec object;
    private final String method;
    private final List<Spec> arguments = new ArrayList<>();

    /**
     * Constructor
     *
     * @param object the caller object
     * @param method the method
     */
    @Deprecated(since = "0.1.0")
    public InvokeSpec(Class<?> object, String method) {
        this(Spec.simpleClassName(object), method);
    }

    /**
     * Constructor
     *
     * @param object the caller object
     * @param method the method
     */
    public InvokeSpec(Spec object, String method) {
        this.object = object;
        this.method = method;
    }

    /**
     * Constructor
     *
     * @param object the caller object
     * @param method the method
     */
    public InvokeSpec(String object, String method) {
        this(Spec.literal(object), method);
    }

    /**
     * {@return invoke this}
     */
    public static InvokeSpec invokeThis() {
        return new InvokeSpec((Spec) null, "this");
    }

    /**
     * Add argument
     *
     * @param spec the argument
     * @return this
     */
    public InvokeSpec addArgument(Spec spec) {
        arguments.add(spec);
        return this;
    }

    /**
     * Add argument
     *
     * @param spec the argument
     * @return this
     */
    public InvokeSpec addArgument(String spec) {
        return addArgument(Spec.literal(spec));
    }

    /**
     * Runs the action
     *
     * @param consumer the action
     * @return this
     */
    public InvokeSpec also(Consumer<InvokeSpec> consumer) {
        consumer.accept(this);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent + 4);
        if (object != null) {
            object.append(builder, indent);
            if (object instanceof InvokeSpec) {
                builder.append('\n').append(indentString);
            }
            builder.append('.');
        }
        builder.append(method).append('(');
        for (int i = 0, size = arguments.size(); i < size; i++) {
            Spec spec = arguments.get(i);
            if (i != 0) {
                builder.append(",");
                if (size >= 5) {
                    builder.append('\n').append(indentString);
                } else {
                    builder.append(' ');
                }
            }
            spec.append(builder, indent + 4);
        }
        builder.append(')');
    }
}
