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

import io.github.overrun.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import overrun.marshal.Downcall;
import overrun.marshal.DowncallOption;
import overrun.marshal.gen.CType;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author squid233
 * @since 0.1.0
 */
public class CTypeTest {
    private static Functions instance;

    interface Functions {
        @CType(value = "long", canonical = true)
        long returnLong();

        long acceptLong(@CType(value = "long", canonical = true) long value);
    }

    static long returnLong() {
        return 42L;
    }

    static int returnInt() {
        return 41;
    }

    static long acceptLong(long value) {
        return value * 2L;
    }

    static long acceptInt(int value) {
        return value * 3L;
    }

    @BeforeAll
    static void beforeAll() throws NoSuchMethodException, IllegalAccessException {
        Linker linker = Linker.nativeLinker();
        MethodHandles.Lookup handles = MethodHandles.lookup();
        Arena arena = Arena.ofAuto();

        MemorySegment _returnLong = linker.upcallStub(handles.findStatic(CTypeTest.class, "returnLong", MethodType.methodType(long.class)), FunctionDescriptor.of(ValueLayout.JAVA_LONG), arena);
        MemorySegment _returnInt = linker.upcallStub(handles.findStatic(CTypeTest.class, "returnInt", MethodType.methodType(int.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT), arena);
        MemorySegment _acceptLong = linker.upcallStub(handles.findStatic(CTypeTest.class, "acceptLong", MethodType.methodType(long.class, long.class)), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), arena);
        MemorySegment _acceptInt = linker.upcallStub(handles.findStatic(CTypeTest.class, "acceptInt", MethodType.methodType(long.class, int.class)), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT), arena);
        SymbolLookup lookup = name -> switch (name) {
            case "returnLong" -> Optional.of(Platform.current() instanceof Platform.Windows ? _returnInt : _returnLong);
            case "acceptLong" -> Optional.of(Platform.current() instanceof Platform.Windows ? _acceptInt : _acceptLong);
            default -> Optional.empty();
        };
        instance = Downcall.load(handles, lookup, DowncallOption.targetClass(Functions.class));
    }

    @Test
    void testReturnLong() {
        if (Platform.current() instanceof Platform.Windows) {
            assertEquals(41L, instance.returnLong());
        } else {
            assertEquals(42L, instance.returnLong());
        }
    }

    @Test
    void testAcceptLong() {
        if (Platform.current() instanceof Platform.Windows) {
            assertEquals(126L, instance.acceptLong(42L));
        } else {
            assertEquals(84L, instance.acceptLong(42L));
        }
    }
}
