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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import overrun.marshal.DirectAccess;
import overrun.marshal.Downcall;
import overrun.marshal.gen.Skip;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * test global variable
 *
 * @author squid233
 * @since 0.1.0
 */
public final class GlobalVarTest {
    private static final MemorySegment globalVar = Arena.ofAuto().allocate(ValueLayout.JAVA_INT);
    private static final SymbolLookup LOOKUP = name -> "globalVar".equals(name) ? Optional.of(globalVar) : Optional.empty();

    static {
        globalVar.set(ValueLayout.JAVA_INT, 0L, 42 );
    }

    interface I extends DirectAccess {
        I INSTANCE = Downcall.load(MethodHandles.lookup(), LOOKUP);

        @Skip
        default int testGlobalVariable() {
            return symbolLookup().find("globalVar")
                .orElseThrow()
                .reinterpret(ValueLayout.JAVA_INT.byteSize())
                .get(ValueLayout.JAVA_INT, 0L);
        }
    }

    @Test
    void testGlobalVariable() {
        Assertions.assertEquals(42, I.INSTANCE.testGlobalVariable());
    }
}
