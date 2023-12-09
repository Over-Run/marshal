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

import java.util.ArrayList;
import java.util.Collection;
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
     * Add arguments
     *
     * @param spec the arguments
     * @return this
     */
    public InvokeSpec addArguments(Collection<Spec> spec) {
        arguments.addAll(spec);
        return this;
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
        object.append(builder, indent);
        builder.append('.').append(method).append('(');
        boolean first = true;
        for (Spec spec : arguments) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            spec.append(builder, indent);
        }
        builder.append(')');
    }
}
