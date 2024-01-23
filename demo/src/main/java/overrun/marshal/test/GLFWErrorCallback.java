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

package overrun.marshal.test;

import overrun.marshal.MemoryStack;
import overrun.marshal.gen.SizedSeg;
import overrun.marshal.Upcall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Test upcall stub
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface GLFWErrorCallback extends Upcall {
    Type<GLFWErrorCallback> TYPE = Upcall.type();

    void invoke(int error, String description);

    @Stub
    default void invoke(int error, @SizedSeg(Long.MAX_VALUE) MemorySegment description) {
        invoke(error, description.getString(0));
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return TYPE.of(arena, this);
    }

    @Wrapper
    static GLFWErrorCallback wrap(MemorySegment stub) {
        return TYPE.wrap(stub, methodHandle -> (error, description) -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                methodHandle.invokeExact(error, stack.allocateFrom(description));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}
