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

package overrun.marshal.test;

import org.junit.jupiter.api.Test;
import overrun.marshal.Downcall;
import overrun.marshal.gen.Entrypoint;
import overrun.marshal.gen.Skip;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * test with custom function descriptor and client-side method handle invocation
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DescriptorMapTest {
    static final Linker LINKER = Linker.nativeLinker();
    static final MemorySegment _returnInt, _returnDouble, _acceptInt, _acceptLong;

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        final Arena arena = Arena.ofAuto();
        try {
            _returnInt = LINKER.upcallStub(lookup.findStatic(DescriptorMapTest.class, "returnInt", MethodType.methodType(int.class)), FunctionDescriptor.of(JAVA_INT), arena);
            _returnDouble = LINKER.upcallStub(lookup.findStatic(DescriptorMapTest.class, "returnDouble", MethodType.methodType(double.class)), FunctionDescriptor.of(JAVA_DOUBLE), arena);
            _acceptInt = LINKER.upcallStub(lookup.findStatic(DescriptorMapTest.class, "acceptInt", MethodType.methodType(boolean.class, int.class)), FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT), arena);
            _acceptLong = LINKER.upcallStub(lookup.findStatic(DescriptorMapTest.class, "acceptLong", MethodType.methodType(boolean.class, long.class)), FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG), arena);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static SymbolLookup lookup(ValueLayout returnLayout, ValueLayout acceptLayout) {
        return name -> switch (name) {
            case "testReturn" -> switch (returnLayout) {
                case ValueLayout.OfInt _ -> Optional.of(_returnInt);
                case ValueLayout.OfDouble _ -> Optional.of(_returnDouble);
                default -> Optional.empty();
            };
            case "testAccept" -> switch (acceptLayout) {
                case ValueLayout.OfInt _ -> Optional.of(_acceptInt);
                case ValueLayout.OfLong _ -> Optional.of(_acceptLong);
                default -> Optional.empty();
            };
            default -> Optional.empty();
        };
    }

    static int returnInt() {
        return 42;
    }

    static double returnDouble() {
        return 84.0;
    }

    static boolean acceptInt(int i) {
        return i == 42;
    }

    static boolean acceptLong(long d) {
        return d == 84L;
    }

    public interface Interface {
        static Interface getInstance(ValueLayout returnLayout, ValueLayout acceptLayout) {
            return Downcall.load(lookup(returnLayout, acceptLayout), Map.of(
                "testReturn", FunctionDescriptor.of(returnLayout),
                "testAccept", FunctionDescriptor.of(JAVA_BOOLEAN, acceptLayout)
            ));
        }

        @Entrypoint("testReturn")
        MethodHandle mh_testReturn();

        @Entrypoint("testAccept")
        MethodHandle mh_testAccept();

        @Skip
        default Object testReturn() {
            try {
                return mh_testReturn().invoke();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Skip
        default boolean testAccept(boolean usingLong, long o) {
            try {
                if (usingLong) {
                    return (boolean) mh_testAccept().invokeExact(o);
                }
                return (boolean) mh_testAccept().invokeExact(Math.toIntExact(o));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void testReturn() {
        // this layout pass to 2 methods!!
        assertEquals(42, (int) Interface.getInstance(JAVA_INT, JAVA_INT).testReturn());
        assertEquals(84.0, (double) Interface.getInstance(JAVA_DOUBLE, JAVA_INT).testReturn());
    }

    @Test
    void testAccept() {
        assertTrue(Interface.getInstance(JAVA_INT, JAVA_INT).testAccept(false, 42));
        assertTrue(Interface.getInstance(JAVA_INT, JAVA_LONG).testAccept(true, 84L));
    }
}
