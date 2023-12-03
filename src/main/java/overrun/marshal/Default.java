/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

package overrun.marshal;

import java.lang.annotation.*;

/**
 * Marks a method that has a default return value.
 * <p>
 * A method marked as {@code @Default} will not throw an exception or be invoked
 * if it couldn't be found in the specified library.
 * <h2>Example</h2>
 * <pre>{@code
 * @Default
 * void functionThatMightBeAbsent();
 *
 * @Default("42")
 * int anotherFunction();
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Default {
    /**
     * {@return the default value of the method}
     */
    String value() default "";
}
