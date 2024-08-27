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

/**
 * Processors are used to indicate how to process a value in {@link overrun.marshal.Downcall Downcall}.
 * <p>
 * See subclasses for more information.
 *
 * @param <T> the type of this
 * @author squid233
 * @since 0.1.0
 */
public interface Processor<T extends Processor<T>> {
    /**
     * Adds an alternative processor to this.
     * <p>
     * Check {@code process} method of subclasses to see how alternative processors are used.
     *
     * @param processor the processor
     */
    void addProcessor(T processor);
}
