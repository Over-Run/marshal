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

import overrun.marshal.Addressable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

/**
 * A struct provider.
 *
 * @author squid233
 * @since 0.1.0
 */
public interface IStruct extends Addressable {
    /**
     * {@return the memory segment of this struct}
     */
    @Override
    MemorySegment segment();

    /**
     * {@return the layout of this struct}
     */
    StructLayout layout();

    /**
     * {@return the element count of this struct}
     */
    long elementCount();

    /**
     * Infers how many this struct is there in the given segment.
     *
     * @param segment the segment
     * @param layout  the struct layout
     * @return the count
     */
    static long inferCount(MemorySegment segment, StructLayout layout) {
        return segment.byteSize() / layout.byteSize();
    }
}
