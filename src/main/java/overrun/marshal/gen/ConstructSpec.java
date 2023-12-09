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
import java.util.List;

/**
 * Constructor
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ConstructSpec implements Spec {
    private final String object;
    private final List<Spec> arguments = new ArrayList<>();

    /**
     * Constructor
     *
     * @param object the caller object
     */
    public ConstructSpec(String object) {
        this.object = object;
    }

    /**
     * Add argument
     *
     * @param spec the argument
     * @return this
     */
    public ConstructSpec addArgument(Spec spec) {
        arguments.add(spec);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        builder.append("new ").append(object).append('(');
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
