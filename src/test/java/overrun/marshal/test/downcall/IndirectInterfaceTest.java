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

package overrun.marshal.test.downcall;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import overrun.marshal.Downcall;
import overrun.marshal.DowncallOption;

import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandles;

/**
 * Test indirect interface
 *
 * @author squid233
 * @since 0.1.0
 */
public final class IndirectInterfaceTest {
    public interface I1 {
        default int fun1() {
            return 1;
        }
    }

    public interface I2 extends I1 {
    }

    I2 INSTANCE = Downcall.load(MethodHandles.lookup(), SymbolLookup.loaderLookup(), DowncallOption.targetClass(I2.class));

    @Test
    void testIndirectInterface() {
        Assertions.assertEquals(1, INSTANCE.fun1());
    }
}
