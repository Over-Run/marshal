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

import overrun.marshal.SizedSeg;
import overrun.marshal.Upcall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface Upcall2 extends Upcall {
    Type<Upcall2> TYPE = Upcall.type();

    MemorySegment invoke(MemorySegment segment, int[] arr);

    @SizedSeg(16L)
    @Stub
    default MemorySegment invoke(@SizedSeg(8L) MemorySegment segment, @SizedSeg(4L * Integer.BYTES) MemorySegment arr) {
        return invoke(segment, arr.toArray(ValueLayout.JAVA_INT));
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return TYPE.of(arena, this);
    }

    @Wrapper
    static Upcall2 wrap(MemorySegment stub) {
        return TYPE.wrap(stub, (arenaSupplier, methodHandle) -> (segment, arr) -> {
            try {
                return (MemorySegment) methodHandle.invokeExact(segment, arenaSupplier.get().allocateFrom(ValueLayout.JAVA_INT, arr));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
