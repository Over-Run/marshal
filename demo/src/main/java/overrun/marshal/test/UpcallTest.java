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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class UpcallTest {
    private static void glfwSetErrorCallback(Arena arena, GLFWErrorCallback callback) {
        // simulates native code
        final var mhTest1 = GLFWErrorCallback.wrap(callback.stub(arena));
        mhTest1.invoke(0x0, arena.allocateFrom("No error"));
        mhTest1.invoke(0xffff, arena.allocateFrom("Some error"));
    }

    public static void main(String[] args) {
        final Arena arena = Arena.ofAuto();

        glfwSetErrorCallback(arena, (error, description) ->
            System.out.println("0x" + Integer.toHexString(error) + ": " + description));

        Upcall2 upcall2 = (segment, arr) -> {
            System.out.println("segment = " + segment);
            System.out.println("arr = " + Arrays.toString(arr));
            return MemorySegment.NULL;
        };
        System.out.println("descriptor = " + Upcall2.TYPE.descriptor());
        System.out.println("return = " + Upcall2.wrap(upcall2.stub(arena)).invoke(MemorySegment.ofAddress(0x20L), arena.allocateFrom(ValueLayout.JAVA_INT, 1, 2, 3, 4)));
    }
}
