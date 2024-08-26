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
 * @param <T> the type of the context
 * @author squid233
 * @since 0.1.0
 */
public abstract class CodeInserter<T> implements Processor<CodeInserter<T>> {
    private final List<CodeInserter<T>> list = new ArrayList<>();

    /**
     * constructor
     */
    protected CodeInserter() {
    }

    public void process(CodeBuilder builder, T context) {
        for (CodeInserter<T> processor : list) {
            processor.process(builder, context);
        }
    }

    @Override
    public void addProcessor(CodeInserter<T> processor) {
        list.add(processor);
    }
}
