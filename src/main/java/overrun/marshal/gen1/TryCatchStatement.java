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
 * Try-catch
 *
 * @author squid233
 * @since 0.1.0
 */
public final class TryCatchStatement implements Spec, StatementBlock {
    private final List<Spec> statements = new ArrayList<>();
    private final List<CatchClause> catchClauses = new ArrayList<>();

    /**
     * Constructor
     */
    public TryCatchStatement() {
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
     * Add a catch clause and perform the action
     *
     * @param catchClause catch clause
     * @param consumer    action
     */
    public void addCatchClause(CatchClause catchClause, Consumer<CatchClause> consumer) {
        catchClauses.add(catchClause);
        consumer.accept(catchClause);
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append(indentString).append("try {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append("} ");
        catchClauses.forEach(catchClause -> catchClause.append(builder, indent));
        builder.append('\n');
    }
}
