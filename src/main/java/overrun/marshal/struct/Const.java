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
 * Marks a struct or its member as <i>const</i>.
 * <p>
 * A const struct or its member does not generate setter.
 * <h2>Example</h2>
 * <pre>{@code
 * @Const
 * @Struct
 * record Vector2(int x, int y) {
 * }
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Const {
}
