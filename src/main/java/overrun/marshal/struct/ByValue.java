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
 * Marks a method that returns a struct by value.
 * <p>
 * This makes the generator insert a segment allocator before the first parameter.
 * <h2>Example</h2>
 * <pre>{@code
 * @ByValue
 * @Entrypoint("returnStruct")
 * @StructRef("org.example.MyStruct")
 * MemorySegment nreturnStruct();
 *
 * @ByValue
 * @Overload("nreturnStruct")
 * @StructRef("org.example.MyStruct")
 * Object returnStruct();
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface ByValue {
}
