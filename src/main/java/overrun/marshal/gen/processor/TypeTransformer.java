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

import java.util.ArrayList;
import java.util.List;

/**
 * Type transformers transforms the given context to a specific type.
 *
 * @param <T> the type of the context
 * @param <R> the type of the return value
 * @author squid233
 * @since 0.1.0
 */
public abstract class TypeTransformer<T, R> implements Processor<TypeTransformer<T, R>> {
    private final List<TypeTransformer<T, R>> list = new ArrayList<>();

    /**
     * constructor
     */
    protected TypeTransformer() {
    }

    /**
     * Runs alternative processors. If an alternative processor has returned a non-null value,
     * then it returns the value; otherwise, it continues running until there is no alternative processor.
     *
     * @param context the context
     * @return a value
     */
    public R process(T context) {
        for (var transformer : list) {
            R r = transformer.process(context);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Override
    public void addProcessor(TypeTransformer<T, R> transformer) {
        list.add(transformer);
    }
}
