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
 * Marks a field as a reference of a struct.
 * <h2>Example</h2>
 * <pre>{@code
 * @StructRef("org.example.Vector3")
 * int vec;
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface StructRef {
    /**
     * {@return the full class name of the target struct}
     */
    String value();
}
