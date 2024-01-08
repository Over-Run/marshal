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
 * Changes the access modifier of a method.
 * <h2>Example</h2>
 * <pre>{@code
 * @Access(AccessModifier.PROTECTED)
 * int ngetStatus(MemorySegment dst);
 * }</pre>
 *
 * @author squid233
 * @see AccessModifier
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Access {
    /**
     * {@return the access modifier of the method}
     */
    AccessModifier value() default AccessModifier.PUBLIC;
}
