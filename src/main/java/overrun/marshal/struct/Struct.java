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

import overrun.marshal.Addressable;

import java.lang.foreign.*;

/**
 * The presentation of C struct.
 *
 * @author squid233
 * @since 0.1.0
 */
public class Struct implements Addressable {
    private final MemorySegment segment;
    private final StructLayout layout;
    private final SequenceLayout sequenceLayout;

    /**
     * Creates a struct with the given layout.
     *
     * @param segment      the segment
     * @param elementCount the element count
     * @param layout       the struct layout
     */
    public Struct(MemorySegment segment, long elementCount, StructLayout layout) {
        this.segment = segment;
        this.layout = layout;
        this.sequenceLayout = MemoryLayout.sequenceLayout(elementCount, layout);
    }

    /**
     * Allocates a struct with the given layout.
     *
     * @param allocator    the allocator
     * @param elementCount the element count
     * @param layout       the struct layout
     */
    public Struct(SegmentAllocator allocator, long elementCount, StructLayout layout) {
        this(allocator.allocate(layout, elementCount), elementCount, layout);
    }

    /**
     * Creates a struct with the given layout.
     *
     * @param segment the segment
     * @param layout  the struct layout
     */
    public Struct(MemorySegment segment, StructLayout layout) {
        this(segment, estimateCount(segment, layout), layout);
    }

    /**
     * Allocates a struct with the given layout.
     *
     * @param allocator the allocator
     * @param layout    the struct layout
     */
    public Struct(SegmentAllocator allocator, StructLayout layout) {
        this(allocator, 1L, layout);
    }

    /**
     * Estimates the struct count of the given segment.
     *
     * @param segment the segment
     * @param layout  the struct layout
     * @return the count
     */
    public static long estimateCount(MemorySegment segment, StructLayout layout) {
        return segment.spliterator(layout).estimateSize();
    }

    /**
     * {@return the segment of this struct}
     */
    @Override
    public MemorySegment segment() {
        return segment;
    }

    /**
     * {@return the layout of this struct}
     */
    public StructLayout layout() {
        return layout;
    }

    /**
     * {@return the sequence layout of this struct buffer}
     */
    public SequenceLayout sequenceLayout() {
        return sequenceLayout;
    }

    /**
     * {@return the element count of this struct buffer}
     */
    public long elementCount() {
        return sequenceLayout().elementCount();
    }
}
