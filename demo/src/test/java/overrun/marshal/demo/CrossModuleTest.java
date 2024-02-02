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

package overrun.marshal.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import overrun.marshal.Downcall;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * Test cross module
 *
 * @author squid233
 * @since 0.1.0
 */
public final class CrossModuleTest {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MemorySegment s_get;

    static {
        try {
            s_get = LINKER.upcallStub(MethodHandles.lookup().findStatic(CrossModuleTest.class, "get", MethodType.methodType(int.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT), Arena.ofAuto());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final SymbolLookup LOOKUP = name -> "get".equals(name) ? Optional.of(s_get) : Optional.empty();

    private static int get() {
        return 1;
    }

    public interface I {
        int get();
    }

    @Test
    void testCrossModule() {
        Assertions.assertEquals(1, Downcall.load(MethodHandles.lookup(), I.class, LOOKUP).get());
    }
}
