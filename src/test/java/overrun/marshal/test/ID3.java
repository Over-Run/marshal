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

import overrun.marshal.Downcall;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * @author squid233
 * @since 0.1.0
 */
public interface ID3 extends ID1, ID2 {
    final class Provider {
        private static final Linker LINKER = Linker.nativeLinker();
        private static final Arena ARENA = Arena.ofAuto();
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        private static final MemorySegment s_mul2, s_get;

        static {
            try {
                s_mul2 = segment(LOOKUP.findStatic(Provider.class, "mul2_", MethodType.methodType(int.class, int.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                s_get = segment(LOOKUP.findStatic(Provider.class, "get", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private static int mul2_(int i) {
            return i * 2;
        }

        private static void get(MemorySegment arr) {
            arr.reinterpret(ValueLayout.JAVA_INT.byteSize()).setAtIndex(ValueLayout.JAVA_INT, 0, 42);
        }

        private static MemorySegment segment(MethodHandle handle, FunctionDescriptor fd) {
            return LINKER.upcallStub(handle, fd, ARENA);
        }

        static SymbolLookup load() {
            return name -> switch (name) {
                case "mul2" -> Optional.of(s_mul2);
                case "get" -> Optional.of(s_get);
                default -> Optional.empty();
            };
        }
    }

    ID3 INSTANCE = Downcall.load(Provider.load());
}
