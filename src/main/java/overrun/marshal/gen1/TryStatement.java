/*
 * MIT License
 *
 * Copyright (c) 2024 Overrun Organization
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
 * Try
 *
 * @author squid233
 * @since 0.1.0
 */
public final class TryStatement implements Spec, StatementBlock {
    private final List<Spec> resources = new ArrayList<>();
    private final List<Spec> statements = new ArrayList<>();
    private final List<CatchClause> catchClauses = new ArrayList<>();

    /**
     * Constructor
     */
    public TryStatement() {
    }

    @Override
    public void addStatement(Spec spec) {
        statements.add(spec);
    }

    /**
     * Add a resource
     *
     * @param resource resource
     */
    public void addResource(Spec resource) {
        resources.add(resource);
    }

    /**
     * Add a catch clause
     *
     * @param catchClause catch clause
     */
    public void addCatchClause(CatchClause catchClause) {
        catchClauses.add(catchClause);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append(indentString).append("try ");
        if (!resources.isEmpty()) {
            builder.append('(');
            for (int i = 0, s = resources.size(); i < s; i++) {
                Spec resource = resources.get(i);
                if (i > 0) {
                    builder.append(";\n");
                }
                resource.append(builder, indent + 4);
            }
            builder.append(") ");
        }
        builder.append("{\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append('}');
        if (!catchClauses.isEmpty()) {
            builder.append(' ');
            catchClauses.forEach(catchClause -> catchClause.append(builder, indent));
        }
        builder.append('\n');
    }
}
