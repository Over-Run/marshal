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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * test inherit downcall
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallInheritTest {
    @Test
    void testInheritDowncall() {
        final ID3 d = ID3.INSTANCE;
        assertEquals(84, d.mul2(42));
        int[] arr = {0};
        d.get(arr);
        assertArrayEquals(new int[]{42}, arr);

        assertEquals(1, d.get1());
        assertEquals(1, d.get2());
        assertEquals(3, d.get3());

        assertEquals(3, assertDoesNotThrow(() -> (int) d.directAccessData().methodHandles().get("get3").invokeExact()));
    }
}
