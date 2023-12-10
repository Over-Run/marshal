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
 * Else
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ElseClause implements Spec, StatementBlock {
    private final Spec condition;
    private final List<Spec> statements = new ArrayList<>();

    private ElseClause(Spec condition) {
        this.condition = condition;
    }

    /**
     * Create else clause
     *
     * @return else clause
     */
    public static ElseClause of() {
        return new ElseClause(null);
    }

    /**
     * Create else clause
     *
     * @param condition condition
     * @return else clause
     */
    public static ElseClause ofIf(Spec condition) {
        return new ElseClause(condition);
    }

    @Override
    public void addStatement(Spec spec) {
        statements.add(spec);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append("else");
        if (condition != null) {
            builder.append(" if (");
            condition.append(builder, indent);
            builder.append(')');
        }
        builder.append(" {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append('}');
    }
}
