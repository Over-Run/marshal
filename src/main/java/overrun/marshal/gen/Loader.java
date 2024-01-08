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
import java.lang.foreign.SymbolLookup;

/**
 * Marks a <strong>static</strong> method as the library loader.
 * <p>
 * The target method must only contain a parameter of type {@link String} and returns {@link SymbolLookup}.
 * <h2>Example</h2>
 * <pre>{@code
 * @Loader
 * static SymbolLookup load(String name) {
 *     //...
 * }
 * }</pre>
 *
 * @author squid233
 * @see Downcall
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Loader {
}
