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

package overrun.marshal.struct;

import java.lang.annotation.*;

/**
 * Marks a class or interface as a struct provider.
 * <p>
 * The generated class contains a {@link java.lang.foreign.StructLayout StructLayout} with name "{@code _LAYOUT}".
 * You can link it in the documentation.
 * <h2>Example</h2>
 * <pre><code>
 * &#47;**
 *  * {&#64;linkplain _LAYOUT Layout}
 *  *&#47;
 * &#64;Struct
 * class Point {
 *     &#64;Skip
 *     int _LAYOUT;
 *     int x, y;
 * }</code></pre>
 *
 * @author squid233
 * @see Const
 * @since 0.1.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Struct {
    /**
     * {@return the name of the generated class}
     */
    String name() default "";

    /**
     * {@return {@code true} if the generated class should not be {@code final}; {@code false} otherwise}
     */
    boolean nonFinal() default false;
}
