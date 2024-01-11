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
 * catch clause
 *
 * @author squid233
 * @since 0.1.0
 */
public final class CatchClause implements Spec, StatementBlock {
    private final Spec type;
    private final String name;
    private final List<Spec> statements = new ArrayList<>();

    /**
     * Constructor
     *
     * @param type exception type
     * @param name variable name
     */
    public CatchClause(Spec type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Constructor
     *
     * @param type exception type
     * @param name variable name
     */
    public CatchClause(String type, String name) {
        this(Spec.literal(type), name);
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
     * {@return name}
     */
    public String name() {
        return name;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append("catch (");
        type.append(builder, indent);
        builder.append(' ').append(name).append(") {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append('}');
    }
}
