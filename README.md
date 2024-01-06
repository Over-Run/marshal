# Marshal

[![Java CI with Gradle](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml)

Marshal allows you to conveniently create native library bindings with [FFM API](https://openjdk.org/jeps/454).

See [wiki](https://github.com/Over-Run/marshal/wiki) for more information.

This library requires JDK 22 or newer.

## Overview

```java
import overrun.marshal.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

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
     * <p> 
     * You can set the access modifier.
     *
     * @param interval the interval
     */
    @Access(AccessModifier.PROTECTED)
    void glfwSwapInterval(int interval);

    /**
     * Custom method body
     */
    @Custom("""
        glfwSwapInterval(1);""")
    void glfwEnableVSync();

    /**
     * {@return default value if the function was not found}
     */
    @Default("0")
    @Entrypoint("glfwGetTime")
    double getTime();

    /**
     * Sized array.
     * Note: this method doesn't exist in GLFW
     * <p>
     * You can mark methods with Critical
     *
     * @param arr The array
     */
    @Critical(allowHeapAccess = true)
    void sizedArray(@Sized(2) int[] arr);

    /**
     * A simple method
     * <p>
     * Note: annotation Ref in this method is unnecessary;
     * however, you can use it to mark
     *
     * @param window the window
     * @param posX   the position x
     * @param posY   the position y
     */
    void glfwGetWindowPos(MemorySegment window, @Ref MemorySegment posX, @Ref MemorySegment posY);

    /**
     * Overload another method with the same name
     *
     * @param window the window
     * @param posX   the array where to store the position x
     * @param posY   the array where to store the position y
     */
    @Overload
    void glfwGetWindowPos(MemorySegment window, @Ref int[] posX, @Ref int[] posY);

    /**
     * {@return a UTF-16 string}
     */
    @StrCharset("UTF-16")
    String returnString();
}

class Main {
    public static void main(String[] args) {
        int key = GLFW.GLFW_KEY_A;
        GLFW.glfwSwapInterval(1);
        GLFW.glfwEnableVSync();
        double time = GLFW.getTime();
        GLFW.sizedArray(new int[]{4, 2});
        MemorySegment windowHandle = /*...*/createWindow();
        // MemoryStack is a placeholder type
        try (MemoryStack stack = /*...*/stackPush()) {
            MemorySegment bufX1 = stack.callocInt(1);
            MemorySegment bufY1 = stack.callocInt(1);
            int[] bufX2 = {0};
            int[] bufY2 = {0};
            GLFW.glfwGetWindowPos(windowHandle, bufX1, bufY1);
            GLFW.glfwGetWindowPos(windowHandle, bufX2, bufY2);
        }
        String s = GLFW.returnString();
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
