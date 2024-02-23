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

package overrun.marshal.test;

import overrun.marshal.Marshal;
import overrun.marshal.Unmarshal;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructHandle;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class ComplexStruct extends Struct {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("IntArray"),
        ValueLayout.ADDRESS.withName("Upcall"),
        ValueLayout.ADDRESS.withName("Addressable").withTargetLayout(Vector3.LAYOUT),
        ValueLayout.ADDRESS.withName("UTF16Str"),
        ValueLayout.ADDRESS.withName("Str"),
        ValueLayout.ADDRESS.withName("Address"),
        ValueLayout.JAVA_LONG.withName("Long"),
        ValueLayout.JAVA_DOUBLE.withName("Double"),
        ValueLayout.JAVA_INT.withName("Int"),
        ValueLayout.JAVA_FLOAT.withName("Float"),
        ValueLayout.JAVA_SHORT.withName("Short"),
        ValueLayout.JAVA_CHAR.withName("Char"),
        ValueLayout.JAVA_BYTE.withName("Byte"),
        ValueLayout.JAVA_BOOLEAN.withName("Bool"),
        MemoryLayout.paddingLayout(2L)
    );
    public final StructHandle.Bool Bool = StructHandle.ofBoolean(this, "Bool");
    public final StructHandle.Char Char = StructHandle.ofChar(this, "Char");
    public final StructHandle.Byte Byte = StructHandle.ofByte(this, "Byte");
    public final StructHandle.Short Short = StructHandle.ofShort(this, "Short");
    public final StructHandle.Int Int = StructHandle.ofInt(this, "Int");
    public final StructHandle.Float Float = StructHandle.ofFloat(this, "Float");
    public final StructHandle.Long Long = StructHandle.ofLong(this, "Long");
    public final StructHandle.Double Double = StructHandle.ofDouble(this, "Double");
    public final StructHandle.Address Address = StructHandle.ofAddress(this, "Address");
    public final StructHandle.Str Str = StructHandle.ofString(this, "Str");
    public final StructHandle.Str UTF16Str = StructHandle.ofString(this, "UTF16Str", StandardCharsets.UTF_16);
    public final StructHandle.Addressable<Vector3> Addressable = StructHandle.ofAddressable(this, "Addressable", Vector3::new);
    public final StructHandle.Upcall<SimpleUpcall> Upcall = StructHandle.ofUpcall(this, "Upcall", segment -> i -> SimpleUpcall.invoke(segment, i));
    public final StructHandle.Array<int[]> IntArray = StructHandle.ofArray(this, "IntArray", Marshal::marshal, Unmarshal::unmarshalAsIntArray);

    /**
     * Creates a struct with the given layout.
     *
     * @param segment      the segment
     * @param elementCount the element count
     */
    public ComplexStruct(MemorySegment segment, long elementCount) {
        super(segment, elementCount, LAYOUT);
    }

    /**
     * Allocates a struct with the given layout.
     *
     * @param allocator    the allocator
     * @param elementCount the element count
     */
    public ComplexStruct(SegmentAllocator allocator, long elementCount) {
        super(allocator, elementCount, LAYOUT);
    }

    /**
     * Creates a struct with the given layout.
     *
     * @param segment the segment
     */
    public ComplexStruct(MemorySegment segment) {
        super(segment, LAYOUT);
    }

    /**
     * Allocates a struct with the given layout.
     *
     * @param allocator the allocator
     */
    public ComplexStruct(SegmentAllocator allocator) {
        super(allocator, LAYOUT);
    }
}
