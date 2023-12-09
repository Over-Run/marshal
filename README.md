# Marshal

[![Java CI with Gradle](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml/badge.svg?event=push)](https://github.com/Over-Run/marshal/actions/workflows/gradle.yml)

Marshal allows you to conveniently create native library bindings with [FFM API](https://openjdk.org/jeps/454).

See [wiki](https://github.com/Over-Run/marshal/wiki) for more information.

This library requires JDK 22 or newer.

## Overview

```java
import overrun.marshal.*;

import java.lang.foreign.MemorySegment;

@NativeApi(libname = "libglfw.so", name = "GLFW")
interface CGLFW {
    @Doc("""
        A field""")
    int GLFW_KEY_A = 65;

    @Doc("""
        Sets swap interval.
        <p>
        You can set the access modifier.
                
        @param interval the interval""")
    @Access(AccessModifier.PROTECTED)
    void glfwSwapInterval(int interval);

    @Doc("""
        Custom method body""")
    @Custom("""
        glfwSwapInterval(1);""")
    void glfwEnableVSync();

    @Doc("""
        {@return default value if the function was not found}""")
    @Default("0")
    @Entrypoint("glfwGetTime")
    double getTime();

    @Doc("""
        Fixed size array.
        Note: this method doesn't exist in GLFW""")
    void fixedSizeArray(@FixedSize(2) int[] arr);

    @Doc("""
        A simple method""")
    void glfwSetWindowPos(MemorySegment window, MemorySegment posX, MemorySegment posY);

    @Doc("""
        Overload""")
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
