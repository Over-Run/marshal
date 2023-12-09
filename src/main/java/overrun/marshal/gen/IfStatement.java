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
import java.util.function.Consumer;

/**
 * If
 *
 * @author squid233
 * @since 0.1.0
 */
public final class IfStatement implements Spec, StatementBlock {
    private final ConditionSpec condition;
    private final List<Spec> statements = new ArrayList<>();
    private final List<ElseClause> elseClauses = new ArrayList<>();

    /**
     * Constructor
     *
     * @param condition condition
     */
    public IfStatement(ConditionSpec condition) {
        this.condition = condition;
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
     * Add an else clause and perform the action
     *
     * @param elseClause else clause
     * @param consumer   action
     */
    public void addElseClause(ElseClause elseClause, Consumer<ElseClause> consumer) {
        consumer.accept(elseClause);
        elseClauses.add(elseClause);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append(indentString).append("if (");
        condition.append(builder, indent);
        builder.append(") {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append('}');
        if (!elseClauses.isEmpty()) {
            builder.append(' ');
            elseClauses.forEach(elseClause -> elseClause.append(builder, indent));
        }
        builder.append('\n');
    }
}
