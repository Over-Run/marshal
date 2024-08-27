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

package overrun.marshal.gen.processor;

import java.lang.classfile.CodeBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed code processors process a given type and consumes a value to insert code.
 * <p>
 * The inserted bytecode represents a specific type.
 * See subclasses for more information.
 *
 * @param <T> the type of the context
 * @author squid233
 * @since 0.1.0
 */
public abstract class TypedCodeProcessor<T> implements Processor<TypedCodeProcessor<T>> {
    private final List<TypedCodeProcessor<T>> list = new ArrayList<>();

    /**
     * constructor
     */
    protected TypedCodeProcessor() {
    }

    /**
     * Runs alternative processors. If an alternative processor has run successfully, then it stops running others.
     * <p>
     * Except for Marshal's classes, subclasses should not directly call this method
     * if they failed to or did not process; instead, they should return {@code false}.
     *
     * @param builder the code builder
     * @param type    the type of the value to be processed
     * @param context the context
     * @return {@code true} if successfully processed the value; otherwise {@code false}
     * @throws IllegalStateException if the value of {@code type} was not processed
     */
    public boolean process(CodeBuilder builder, ProcessorType type, T context) {
        for (var processor : list) {
            if (processor.process(builder, type, context)) {
                return true;
            }
        }
        throw new IllegalStateException(this.getClass().getSimpleName() + ": type '" + type + "' was not processed");
    }

    @Override
    public void addProcessor(TypedCodeProcessor<T> processor) {
        list.add(processor);
    }
}
