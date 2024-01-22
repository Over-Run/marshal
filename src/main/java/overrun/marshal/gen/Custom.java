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
 * Marks a method that uses custom code instead of generated code.
 * <h2>Example</h2>
 * <pre>{@code
 * @Custom("""
 *     System.out.println("Hello world");""")
 * void myCode();
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Deprecated(since = "0.1.0", forRemoval = true)
public @interface Custom {
    /**
     * {@return the custom code}
     */
    String value();
}
