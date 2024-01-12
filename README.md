# Marshal

[![Java CI with Gradle](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml)

Marshal allows you to conveniently create native library bindings with [FFM API](https://openjdk.org/jeps/454).

See [wiki](https://github.com/Over-Run/marshal/wiki) for more information.

This library requires JDK 22 or newer.

## Overview

```java
import overrun.marshal.gen.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

/**
 * GLFW constants and functions
 * <p>
 * The documentation will be automatically copied
 * into the generated file
 * <p>
 * You don't have to specify the name if the name of the class or interface starts with 'C'
 */
@Downcall(libname = "libglfw.so")
interface CGLFW {
    /**
     * A field
     */
    int GLFW_KEY_A = 65;

    /**
     * Sets the swap interval.
     *
     * @param interval the interval
     */
    void glfwSwapInterval(int interval);

    /**
     * Gets the window position.
     * Marks the array parameter as a place to store the value returned by C
     *
     * @param window the window
     * @param posX   the position x
     * @param posY   the position y
     */
    void glfwGetWindowPos(MemorySegment window, @Ref int[] posX, @Ref int[] posY);

    /**
     * Creates a window.
     * Requires a segment allocator to allocate the string;
     * if the first parameter is not segment allocator, then the memory stack is used
     *
     * @param allocator the segment allocator
     * @param width     the width
     * @param height    the height
     * @param title     the title
     * @param monitor   the monitor
     * @param share     the share
     * @return the window
     */
    MemorySegment glfwCreateWindow(SegmentAllocator allocator, int width, int height, String title, MemorySegment monitor, MemorySegment share);
}

class Main {
    public static void main(String[] args) {
        int key = GLFW.GLFW_KEY_A;
        GLFW.glfwSwapInterval(1);

        // Arena
        try (var arena = Arena.ofConfined()) {
            MemorySegment windowHandle = glfwCreateWindow(arena,
                800,
                600,
                "The title",
                MemorySegment.NULL,
                MemorySegment.NULL);

            // ref
            // 1. by array
            int[] ax = {0}, ay = {0};
            glfwGetWindowPos(windowHandle, ax, ay);
            // 2. by segment
            var sx = arena.allocate(ValueLayout.JAVA_INT),
                sy = arena.allocate(ValueLayout.JAVA_INT);
            glfwGetWindowPos(windowHandle, sx, sy);
        }
    }
}
```

## Import

Import as a Gradle dependency:

```groovy
dependencies {
    def marshalVersion = "0.1.0-alpha.6"
    annotationProcessor("io.github.over-run:marshal:$marshalVersion")
    implementation("io.github.over-run:marshal:$marshalVersion")
}
```
