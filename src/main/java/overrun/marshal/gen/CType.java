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

package overrun.marshal.gen;

import java.lang.annotation.*;

/**
 * This marker annotation is for methods and parameters to mark the native type of them.
 * <h2>Example</h2>
 * <pre>{@code
 * @CType("size_t")
 * long strlen(@CType("const char*") String s);
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface CType {
    /**
     * {@return the native (C) type of the marked type}
     */
    String value();
}
