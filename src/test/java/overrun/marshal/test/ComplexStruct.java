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
    public static final StructHandle.Bool Bool = StructHandle.ofBoolean(LAYOUT, "Bool");
    public static final StructHandle.Char Char = StructHandle.ofChar(LAYOUT, "Char");
    public static final StructHandle.Byte Byte = StructHandle.ofByte(LAYOUT, "Byte");
    public static final StructHandle.Short Short = StructHandle.ofShort(LAYOUT, "Short");
    public static final StructHandle.Int Int = StructHandle.ofInt(LAYOUT, "Int");
    public static final StructHandle.Float Float = StructHandle.ofFloat(LAYOUT, "Float");
    public static final StructHandle.Long Long = StructHandle.ofLong(LAYOUT, "Long");
    public static final StructHandle.Double Double = StructHandle.ofDouble(LAYOUT, "Double");
    public static final StructHandle.Address Address = StructHandle.ofAddress(LAYOUT, "Address");
    public static final StructHandle.Str Str = StructHandle.ofString(LAYOUT, "Str");
    public static final StructHandle.Str UTF16Str = StructHandle.ofString(LAYOUT, "UTF16Str", StandardCharsets.UTF_16);
    public static final StructHandle.Addressable<Vector3> Addressable = StructHandle.ofAddressable(LAYOUT, "Addressable", Vector3::new);
    public static final StructHandle.Upcall<SimpleUpcall> Upcall = StructHandle.ofUpcall(LAYOUT, "Upcall", segment -> i -> SimpleUpcall.invoke(segment, i));
    public static final StructHandle.Array<int[]> IntArray = StructHandle.ofArray(LAYOUT, "IntArray", Marshal::marshal, Unmarshal::unmarshalAsIntArray);

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
