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

import overrun.marshal.Configurations;

import java.lang.annotation.*;

/**
 * Marks an array parameter as a fixed size array.
 * <p>
 * The generated code will try to check the size of a passing array.
 * <h2>Example</h2>
 * <pre>{@code
 * void set(@Sized(3) int[] vec);
 * }</pre>
 *
 * @author squid233
 * @see Configurations#CHECK_ARRAY_SIZE
 * @since 0.1.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sized {
    /**
     * {@return the size of the array}
     */
    int value();
}
