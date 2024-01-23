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

import overrun.marshal.Upcall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A simple upcall
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface SimpleUpcall extends Upcall {
    Type<SimpleUpcall> TYPE = Upcall.type();

    @Stub
    int invoke(int i);

    @Wrapper
    static SimpleUpcall wrap(Arena arena, MemorySegment stub) {
        return TYPE.wrap(stub, methodHandle -> i -> {
            try {
                return (int) methodHandle.invokeExact(i);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return TYPE.of(arena, this);
    }
}
