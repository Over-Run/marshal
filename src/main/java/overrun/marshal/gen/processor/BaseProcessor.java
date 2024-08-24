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
 * A processor with a list of processors
 *
 * @param <C> context type
 * @author squid233
 * @since 0.1.0
 */
public abstract class BaseProcessor<C> implements Processor<C> {
    /**
     * processors
     */
    protected final List<Processor<C>> processors = new ArrayList<>();

    @Override
    public boolean process(C context) {
        for (var processor : processors) {
            if (processor.process(context)) {
                return true;
            }
        }
        return false;
    }

    public void addProcessor(Processor<C> processor) {
        processors.add(processor);
    }
}
