# Marshal

[![Java CI with Gradle](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml)

Marshal allows you to conveniently create native library bindings with [FFM API](https://openjdk.org/jeps/454).

See [wiki](https://github.com/Over-Run/marshal/wiki) for more information.

This library requires JDK 22 or newer.

## Overview

```java
import overrun.marshal.*;

import java.lang.foreign.MemorySegment;

/**
 * GLFW constants and functions
 * <p>
 * The documentation will be automatically copied
 * into the generated file
 */
@NativeApi(libname = "libglfw.so", name = "GLFW")
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
     * Fixed size array.
     * Note: this method doesn't exist in GLFW
     *
     * @param arr The array
     */
    void fixedSizeArray(@FixedSize(2) int[] arr);

    /**
     * A simple method
     *
     * @param window the window
     * @param posX the position x
     * @param posY the position y
     */
    void glfwSetWindowPos(MemorySegment window, MemorySegment posX, MemorySegment posY);

    /**
     * Overload
     *
     * @param window the window
     * @param posX the array where to store the position x
     * @param posY the array where to store the position y
     */
    @Overload
    void glfwSetWindowPos(MemorySegment window, @Ref int[] posX, @Ref int[] posY);
}
```

## Import

Import as a Gradle dependency:

```groovy
dependencies {
    def marshalVersion = "0.1.0-alpha.0"
    annotationProcessor("io.github.over-run:marshal:$marshalVersion")
    implementation("io.github.over-run:marshal:$marshalVersion")
}
```

## Compiler Arguments

You can disable the warning of marshalling boolean arrays
by using the compiler argument `-Aoverrun.marshal.disableBoolArrayWarn=true`.
