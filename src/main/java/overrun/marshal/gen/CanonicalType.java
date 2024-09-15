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

import overrun.marshal.gen.processor.DescriptorTransformer;

import java.lang.annotation.*;
import java.lang.foreign.Linker;

/**
 * Marks a method or a parameter, as {@link DescriptorTransformer} will use
 * the {@linkplain Linker#canonicalLayouts() canonical layout} mapped from the linker of the current operating system.
 * <p>
 * This annotation is not {@link Documented}. To display the native type, use {@link CType}.
 * <h2>Example</h2>
 * <pre>{@code
 * @CanonicalType("size_t")
 * long strlen(String s);
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanonicalType {
    /**
     * {@return the canonical type of the marked type}
     */
    String value();
}
