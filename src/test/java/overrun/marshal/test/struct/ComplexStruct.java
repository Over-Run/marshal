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

package overrun.marshal.test.struct;

import overrun.marshal.LayoutBuilder;
import overrun.marshal.Marshal;
import overrun.marshal.Unmarshal;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructAllocator;
import overrun.marshal.test.upcall.SimpleUpcall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

/**
 * @author squid233
 * @since 0.1.0
 */
public interface ComplexStruct extends Struct<ComplexStruct> {
    StructAllocator<ComplexStruct> OF = new StructAllocator<>(MethodHandles.lookup(), LayoutBuilder.struct()
        .cAddress("IntArray")
        .cAddress("Upcall")
        .cAddress("Addressable", Vector3.OF.layout())
        .cAddress("UTF16Str")
        .cAddress("Str")
        .cAddress("Address")
        .cLong("Long")
        .cDouble("Double")
        .cInt("Int")
        .cFloat("Float")
        .cShort("Short")
        .cChar("Char")
        .cByte("Byte")
        .cBoolean("Bool")
        .build());

    @Override
    ComplexStruct slice(long index, long count);

    @Override
    ComplexStruct slice(long index);

    boolean Bool();

    ComplexStruct Bool(boolean val);

    char Char();

    ComplexStruct Char(char val);

    byte Byte();

    ComplexStruct Byte(byte val);

    short Short();

    ComplexStruct Short(short val);

    int Int();

    ComplexStruct Int(int val);

    long Long();

    ComplexStruct Long(long val);

    float Float();

    ComplexStruct Float(float val);

    double Double();

    ComplexStruct Double(double val);

    MemorySegment Address();

    ComplexStruct Address(MemorySegment val);

    MemorySegment Str();

    ComplexStruct Str(MemorySegment val);

    default String javaStr() {
        return Unmarshal.unboundString(Str());
    }

    default ComplexStruct javaStr(SegmentAllocator allocator, String val) {
        return Str(Marshal.marshal(allocator, val));
    }

    MemorySegment UTF16Str();

    ComplexStruct UTF16Str(MemorySegment val);

    default String javaUTF16Str() {
        return Unmarshal.unboundString(UTF16Str(), StandardCharsets.UTF_16);
    }

    default ComplexStruct javaUTF16Str(SegmentAllocator allocator, String val) {
        return UTF16Str(Marshal.marshal(allocator, val, StandardCharsets.UTF_16));
    }

    MemorySegment Addressable();

    ComplexStruct Addressable(MemorySegment val);

    default Vector3 javaAddressable() {
        return Vector3.OF.of(Addressable());
    }

    default ComplexStruct javaAddressable(Vector3 val) {
        return Addressable(Marshal.marshal(val));
    }

    MemorySegment Upcall();

    ComplexStruct Upcall(MemorySegment val);

    default SimpleUpcall javaUpcall() {
        return i -> SimpleUpcall.invoke(Upcall(), i);
    }

    default ComplexStruct javaUpcall(Arena arena, SimpleUpcall val) {
        return Upcall(Marshal.marshal(arena, val));
    }

    MemorySegment IntArray();

    ComplexStruct IntArray(MemorySegment val);

    default int[] javaIntArray(int size) {
        return Unmarshal.unmarshalAsIntArray(IntArray().reinterpret(ValueLayout.JAVA_INT.scale(0L, size)));
    }

    default ComplexStruct javaIntArray(SegmentAllocator allocator, int[] val) {
        return IntArray(Marshal.marshal(allocator, val));
    }
}
