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

import overrun.marshal.Upcall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A simple upcall
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface SimpleUpcall extends Upcall {
    Type<SimpleUpcall> TYPE = Upcall.register(
        Upcall.type("invoke", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)),
        stub -> i -> invoke(stub, i)
    );

    int invoke(int i);

    static int invoke(MemorySegment stub, int i) {
        try {
            return (int) TYPE.downcallTarget().invokeExact(stub, i);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return TYPE.of(arena, this);
    }
}
