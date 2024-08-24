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

import overrun.marshal.Unmarshal;
import overrun.marshal.gen.processor.ProcessorTypes;
import overrun.marshal.gen.processor.UnmarshalProcessor;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.util.Objects;

/**
 * The representation of a C structure.
 * <p>
 * Returning a {@code Struct} from a downcall method requires a
 * {@linkplain ProcessorTypes#registerStruct(Class, StructAllocatorSpec) registration} to tell
 * {@link UnmarshalProcessor} how to create an instance of the {@code Struct}.
 *
 * @param <T> the type of the actual structure interface
 * @author squid233
 * @see StructAllocator
 * @see overrun.marshal.LayoutBuilder LayoutBuilder
 * @since 0.1.0
 */
public interface Struct<T extends Struct<T>> {
    /**
     * Estimates the struct count of the given segment.
     *
     * @param segment the segment
     * @param layout  the struct layout
     * @return the count
     */
    static long estimateCount(MemorySegment segment, StructLayout layout) {
        if (Unmarshal.isNullPointer(segment)) return 0L;
        return Math.divideExact(segment.byteSize(), Objects.requireNonNull(layout).byteSize());
    }

    /**
     * Makes a slice of this structure starts at the given index.
     *
     * @param index the start index
     * @param count the count
     * @return the slice of this structure
     */
    T slice(long index, long count);

    /**
     * Makes a slice of this structure with the given index.
     *
     * @param index the index
     * @return the slice of this structure
     */
    T slice(long index);

    /**
     * {@return the segment of this struct}
     */
    MemorySegment segment();

    /**
     * {@return the layout of this struct}
     */
    StructLayout layout();

    /**
     * {@return the sequence layout of this struct buffer}
     */
    default SequenceLayout sequenceLayout() {
        return MemoryLayout.sequenceLayout(elementCount(), layout());
    }

    /**
     * {@return the element count of this struct buffer}
     */
    long elementCount();
}
