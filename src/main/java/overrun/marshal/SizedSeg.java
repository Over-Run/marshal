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

package overrun.marshal;

import java.lang.annotation.*;

/**
 * Marks a memory segment as fix-sized.
 * <h2>Example</h2>
 * <pre>{@code
 * @SizedSeg(0x7FFFFFFFFFFFFFFFL)
 * MemorySegment segment;
 * }</pre>
 *
 * @author squid233
 * @see Configurations#CHECK_ARRAY_SIZE
 * @since 0.1.0
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SizedSeg {
    /**
     * {@return the size of the memory segment}
     */
    long value();
}
