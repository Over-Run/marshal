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

/**
 * Lambda
 *
 * @author squid233
 * @since 0.1.0
 */
public final class LambdaSpec implements Spec, StatementBlock {
    private final String[] parameters;
    private final List<Spec> statements = new ArrayList<>();

    /**
     * Constructor
     *
     * @param parameters parameters
     */
    public LambdaSpec(String... parameters) {
        this.parameters = parameters;
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

    /**
     * Add a statement
     *
     * @param spec statement
     * @return this
     */
    public LambdaSpec addStatementThis(Spec spec) {
        addStatement(spec);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final boolean multi = statements.size() > 1;
        builder.append(switch (parameters.length) {
            case 0 -> "()";
            case 1 -> parameters[0];
            default -> '(' + String.join(", ", parameters) + ')';
        }).append(" -> ");
        if (multi) {
            builder.append("{\n");
        }
        statements.forEach(spec -> spec.append(builder, multi ? indent + 4 : indent));
        if (multi) {
            builder.append(Spec.indentString(indent)).append('}');
        }
    }
}
