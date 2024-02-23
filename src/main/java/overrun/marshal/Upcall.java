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

package overrun.marshal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * An upcall interface.
 * <p>
 * There must be only one upcall stub provider in its implementation;
 * if there is no, throw an exception;
 * if there is more than one, choose the first one.
 * <h2>Example</h2>
 * <pre>{@code
 * // The implementation must be public if you use Type
 * // The interface doesn't have to be a functional interface
 * @FunctionalInterface
 * public interface MyCallback extends Upcall {
 *     // Create a type wrapper
 *     Type<MyCallback> TYPE = Upcall.type("invoke", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
 *
 *     // The function to be invoked in C
 *     int invoke(int i);
 *
 *     // Create an upcall stub segment with Type
 *     @Override
 *     default MemorySegment stub(Arena arena) {
 *         return TYPE.of(arena, this);
 *     }
 *
 *     // Create an optional wrap method for others to invoke
 *     static int invoke(MemorySegment stub, int i) {
 *         try {
 *             return (int) TYPE.downcallTarget().invokeExact(stub, i);
 *         } catch (Throwable e) {
 *             throw new RuntimeException(e);
 *         }
 *     }
 * }
 *
 * // C downcall
 * void setMyCallback(MyCallback cb);
 * setMyCallback(i -> i * 2);
 * }</pre>
 *
 * @author squid233
 * @see #stub(Arena)
 * @see Type
 * @since 0.1.0
 */
public interface Upcall {
    /**
     * Creates an upcall stub.
     *
     * @param arena the arena associated with the returned upcall stub segment
     * @return a zero-length segment whose address is the address of the upcall stub
     * @see Linker#upcallStub(MethodHandle, FunctionDescriptor, Arena, Linker.Option...) upcallStub
     */
    MemorySegment stub(Arena arena);

    /**
     * Creates {@link Type} with the caller class.
     *
     * @param targetName the name of the target method
     * @param descriptor the function descriptor of the target method
     * @param <T>        the type of the upcall interface
     * @return the created {@link Type}
     * @see #type(Class, String, FunctionDescriptor)
     */
    @SuppressWarnings("unchecked")
    static <T extends Upcall> Type<T> type(String targetName, FunctionDescriptor descriptor) {
        final class Walker {
            private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        }
        final Class<?> callerClass = Walker.STACK_WALKER.getCallerClass();
        if (!Upcall.class.isAssignableFrom(callerClass)) {
            throw new ClassCastException(callerClass.getName());
        }
        return type((Class<T>) callerClass, targetName, descriptor);
    }

    /**
     * Creates {@link Type}.
     *
     * @param tClass     the class of the upcall interface
     * @param targetName the name of the target method
     * @param descriptor the function descriptor of the target method
     * @param <T>        the type of the upcall interface
     * @return the created {@link Type}
     * @see #type(String, FunctionDescriptor)
     */
    static <T extends Upcall> Type<T> type(Class<T> tClass, String targetName, FunctionDescriptor descriptor) {
        return new Type<>(tClass, targetName, descriptor);
    }

    /**
     * Marks a method as an upcall stub provider. The marked method <strong>must not</strong> throw any exception.
     *
     * @author squid233
     * @see Upcall
     * @since 0.1.0
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Stub {
    }

    /**
     * The type wrapper of an upcall interface.
     * The constructor uses heavy reflection, and you should always cache it as a static field.
     *
     * @param <T> The type of the upcall interface.
     * @author squid233
     * @see Upcall
     * @since 0.1.0
     */
    final class Type<T extends Upcall> {
        private static final Linker LINKER = Linker.nativeLinker();
        private final MethodHandle target;
        private final FunctionDescriptor descriptor;
        private final MethodHandle downcallTarget;

        private Type(Class<T> tClass, String targetName, FunctionDescriptor descriptor) {
            try {
                target = MethodHandles.publicLookup().findVirtual(tClass, targetName, descriptor.toMethodType());
            } catch (IllegalAccessException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            this.descriptor = descriptor;
            this.downcallTarget = LINKER.downcallHandle(descriptor);
        }

        /**
         * Creates an upcall stub with the given upcall implementation.
         *
         * @param arena  the arena associated with the returned upcall stub segment
         * @param upcall the implementation
         * @return a zero-length segment whose address is the address of the upcall stub
         * @see Linker#upcallStub(MethodHandle, FunctionDescriptor, Arena, Linker.Option...) upcallStub
         */
        public MemorySegment of(Arena arena, T upcall) {
            return LINKER.upcallStub(target.bindTo(upcall), descriptor(), arena);
        }

        /**
         * Creates a downcall handle with the given upcall stub segment and this type.
         *
         * @param stub the upcall stub segment
         * @return the downcall method handle
         */
        public MethodHandle downcall(MemorySegment stub) {
            return downcallTarget.bindTo(stub);
        }

        /**
         * {@return the descriptor of this type}
         */
        public FunctionDescriptor descriptor() {
            return descriptor;
        }

        /**
         * {@return the downcall method handle}
         */
        public MethodHandle downcallTarget() {
            return downcallTarget;
        }
    }
}
