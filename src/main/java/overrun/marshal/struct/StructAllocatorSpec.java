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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

/**
 * Specification of {@link StructAllocator}.
 *
 * @param <T> the type of the struct
 * @author squid233
 * @since 0.1.0
 */
public interface StructAllocatorSpec<T> {
    /**
     * Creates a struct with the given segment.
     *
     * @param segment the segment
     * @return the instance of the struct
     */
    T of(MemorySegment segment);

    /**
     * {@return the layout of this struct}
     */
    StructLayout layout();
}
