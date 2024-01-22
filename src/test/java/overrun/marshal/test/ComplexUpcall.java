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
import overrun.marshal.gen.SizedSeg;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * A complex upcall
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface ComplexUpcall extends Upcall {
    int[] invoke(int[] arr);

    /**
     * the container
     *
     * @author squid233
     * @since 0.1.0
     */
    sealed class Container extends BaseContainer<ComplexUpcall> implements ComplexUpcall {
        public static final Type<Container> TYPE = Upcall.type();

        public Container(Arena arena, ComplexUpcall delegate) {
            super(arena, delegate);
        }

        @Stub
        @SizedSeg(2 * Integer.BYTES)
        public MemorySegment invoke(@SizedSeg(2 * Integer.BYTES) MemorySegment arr) {
            return arena.allocateFrom(ValueLayout.JAVA_INT, invoke(arr.toArray(ValueLayout.JAVA_INT)));
        }

        @Override
        public int[] invoke(int[] arr) {
            return delegate.invoke(arr);
        }

        @Override
        public MemorySegment stub(Arena arena) {
            return TYPE.of(arena, this);
        }
    }

    /**
     * the wrapper container
     *
     * @author squid233
     * @since 0.1.0
     */
    final class WrapperContainer extends Container {
        private final MethodHandle handle;

        public WrapperContainer(Arena arena, MemorySegment stub) {
            super(arena, null);
            this.handle = TYPE.downcall(stub);
        }

        @Override
        public MemorySegment invoke(MemorySegment arr) {
            try {
                return (MemorySegment) handle.invokeExact(arr);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int[] invoke(int[] arr) {
            return invoke(arena.allocateFrom(ValueLayout.JAVA_INT, arr)).reinterpret(2 * Integer.BYTES).toArray(ValueLayout.JAVA_INT);
        }
    }

    @Wrapper
    static Container wrap(Arena arena, MemorySegment stub) {
        return new WrapperContainer(arena, stub);
    }

    @Override
    default MemorySegment stub(Arena arena) {
        return new Container(arena, this).stub(arena);
    }
}
