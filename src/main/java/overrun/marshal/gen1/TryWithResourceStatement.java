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
import java.util.function.Consumer;

/**
 * try-with-resource
 *
 * @author squid233
 * @since 0.1.0
 */
public final class TryWithResourceStatement implements Spec, StatementBlock {
    private final Spec resource;
    private final List<Spec> statements = new ArrayList<>();

    /**
     * Construct
     *
     * @param resource resource
     */
    public TryWithResourceStatement(Spec resource) {
        this.resource = resource;
    }

    @Override
    public void addStatement(Spec spec) {
        statements.add(spec);
    }

    /**
     * Also runs the action
     *
     * @param consumer the action
     * @return this
     */
    public TryWithResourceStatement also(Consumer<TryWithResourceStatement> consumer) {
        consumer.accept(this);
        return this;
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        final String indentString = Spec.indentString(indent);
        builder.append(indentString).append("try (");
        resource.append(builder, indent);
        builder.append(") {\n");
        statements.forEach(spec -> spec.append(builder, indent + 4));
        builder.append(indentString).append("}\n");
    }
}
