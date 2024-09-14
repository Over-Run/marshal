# Marshal

[![Java CI with Gradle](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml)

Marshal allows you to conveniently create native library bindings with [FFM API](https://openjdk.org/jeps/454).

~~See [wiki](https://github.com/Over-Run/marshal/wiki) for more information.~~

This library requires JDK 23 ~~or newer~~.

## Overview

```java
import overrun.marshal.*;
import overrun.marshal.gen.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandles;

/**
 * GLFW constants and functions
 */
interface GLFW {
    /**
     * The instance of the loaded library
     */
    GLFW INSTANCE = Downcall.load(MethodHandles.lookup(), "libglfw3.so");

    /**
     * A field
     */
    int GLFW_KEY_A = 65;

    /**
     * A normal function
     */
    void glfwSwapInterval(int interval);

    /**
     * A function with ref parameters.
     * Marks the array parameter as a place to store the value returned by C
     */
    void glfwGetWindowPos(MemorySegment window, @Ref int[] posX, @Ref int[] posY);

    /**
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
        GLFW glfw = GLFW.INSTANCE;
        int key = GLFW.GLFW_KEY_A;
        glfw.glfwSwapInterval(1);

        // Arena
        try (var arena = Arena.ofConfined()) {
            MemorySegment windowHandle = glfw.glfwCreateWindow(arena,
                800,
                600,
                "The title",
                MemorySegment.NULL,
                MemorySegment.NULL);

            // ref by array
            int[] ax = {0}, ay = {0};
            glfw.glfwGetWindowPos(windowHandle, ax, ay);
        }
    }
}
```

## Import

Import as a Gradle dependency:

```groovy
dependencies {
    implementation("io.github.over-run:marshal:0.1.0-alpha.33-jdk23")
}
```

and add this VM argument to enable native access: `--enable-native-access=io.github.overrun.marshal`  
or this if you don't use modules: `--enable-native-access=ALL-UNNAMED`

## Additions

- [OverrunGL](https://github.com/Over-Run/overrungl), which is using Marshal
