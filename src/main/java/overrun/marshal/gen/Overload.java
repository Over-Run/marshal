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

package overrun.marshal.gen;

import java.lang.annotation.*;

/**
 * Marks a method as an overload.
 * <p>
 * An overload method keeps {@link String} and array types
 * instead of converting them into {@link java.lang.foreign.MemorySegment MemorySegment}.
 * It will also invoke another method with the same name or {@linkplain #value() the specified value}.
 * <h2>Example</h2>
 * <pre>{@code
 * void nset(MemorySegment vec);
 *
 * @Overload("nset")
 * void set(int[] vec);
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Overload {
    /**
     * {@return the name of the other method to be overloaded}
     */
    String value() default "";
}
