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

package overrun.marshal.struct;

import java.lang.annotation.*;

/**
 * <h2>Returning-by-value structure</h2>
 * Marks a method that returns a struct by value.
 * <p>
 * The annotated method must contain a segment allocator as the first parameter.
 * <h2>Passing-by-value structure</h2>
 * Marks a parameter that passes struct to C function by value.
 * <h2>Example</h2>
 * <pre>{@code
 * @ByValue
 * MyStruct returnStruct(SegmentAllocator allocator);
 *
 * void passByValue(@ByValue MyStruct struct);
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ByValue {
}
