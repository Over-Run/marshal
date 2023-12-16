/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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
import java.lang.invoke.MethodHandle;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class UpcallTest {
    private static void glfwSetErrorCallback(Arena arena, GLFWErrorCallback callback) throws Throwable {
        // simulates native code
        final MethodHandle mhTest1 = GLFWErrorCallback.TYPE.downcall(callback.stub(arena));
        mhTest1.invokeExact(0x0, arena.allocateFrom("No error"));
        mhTest1.invokeExact(0xffff, arena.allocateFrom("Some error"));
    }

    public static void main(String[] args) throws Throwable {
        final Arena arena = Arena.ofAuto();

        glfwSetErrorCallback(arena, (error, description) ->
            System.out.println("0x" + Integer.toHexString(error) + ": " + description));
    }
}
