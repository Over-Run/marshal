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

package overrun.marshal.test.upcall;

import io.github.overrun.memstack.MemoryStack;
import overrun.marshal.Marshal;
import overrun.marshal.Unmarshal;
import overrun.marshal.Upcall;
import overrun.marshal.gen.Sized;

import java.lang.foreign.*;

/**
 * A complex upcall
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface ComplexUpcall extends Upcall {
    AddressLayout ARG_LAYOUT = ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_INT));
    Type<ComplexUpcall> TYPE = Upcall.type("invoke", FunctionDescriptor.of(ARG_LAYOUT, ARG_LAYOUT));

    @Sized(2)
    int[] invoke(@Sized(2) int[] arr);

    default MemorySegment invoke(MemorySegment arr) {
        try (MemoryStack stack = MemoryStack.pushLocal()) {
            return Marshal.marshal(stack, invoke(Unmarshal.unmarshalAsIntArray(arr)));
        }
    }

    static int[] invoke(MemorySegment stub, int[] arr) {
        try (MemoryStack stack = MemoryStack.pushLocal()) {
            return Unmarshal.unmarshalAsIntArray((MemorySegment) TYPE.downcallTarget().invokeExact(stub, Marshal.marshal(stack, arr)));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return TYPE.of(arena, this);
    }
}
