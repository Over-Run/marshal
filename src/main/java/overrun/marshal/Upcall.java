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

import overrun.marshal.gen.SizedSeg;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An upcall interface.
 * <p>
 * There must be only one upcall stub provider in its implementation;
 * if there is no, throw an exception;
 * if there is more than one, choose the first one.
 * <h2>Example</h2>
 * <pre>{@code
 * // The implementation must be public if you use Type
 * // It is not necessary to mark it as a functional interface. However, you can mark it
 * @FunctionalInterface
 * public interface MyCallback extends Upcall {
 *     // Create a type wrapper
 *     Type<MyCallback> TYPE = Upcall.type();
 *
 *     // The function to be invoked in C
 *     // Also the stub provider
 *     @Stub
 *     int invoke(int i);
 *
 *     // Create an upcall stub segment with Type
 *     @Override
 *     default MemorySegment stub(Arena arena) {
 *         return TYPE.of(arena, this);
 *     }
 *
 *     // Create an optional wrap method
 *     @Wrapper
 *     static MyCallback wrap(Arena arena, MemorySegment stub) {
 *         return TYPE.wrap(stub, mh -> i -> {
 *             try {
 *                 return (int) mh.invokeExact(i);
 *             } catch (Throwable e) {
 *                 throw new RuntimeException(e);
 *             }
 *         });
 *     }
 * }
 *
 * // C downcall
 * setMyCallback(i -> i * 2);
 * }</pre>
 *
 * @author squid233
 * @see #stub(Arena)
 * @see Stub
 * @see Type
 * @see Wrapper
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
     * @param <T> the type of the upcall interface
     * @return the created {@link Type}
     * @see #type(Class)
     */
    @SuppressWarnings("unchecked")
    static <T extends Upcall> Type<T> type() {
        final class Walker {
            private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        }
        final Class<?> callerClass = Walker.STACK_WALKER.getCallerClass();
        if (!Upcall.class.isAssignableFrom(callerClass)) {
            throw new ClassCastException(callerClass.getName());
        }
        return type((Class<T>) callerClass);
    }

    /**
     * Creates {@link Type}.
     *
     * @param tClass the class of the upcall interface
     * @param <T>    the type of the upcall interface
     * @return the created {@link Type}
     * @see #type()
     */
    static <T extends Upcall> Type<T> type(Class<T> tClass) {
        return new Type<>(tClass);
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
     * Marks a <strong>static</strong> method as an upcall wrapper.
     * <p>
     * The parameters of marked method must be only one {@link Arena} and one {@link MemorySegment}.
     *
     * @author squid233
     * @see Upcall
     * @since 0.1.0
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Wrapper {
    }

    /**
     * The type wrapper of an upcall interface.
     * The constructor uses heavy reflective, and you should always cache it as a static field.
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

        private Type(Class<T> tClass) {
            final Method method = Arrays.stream(tClass.getDeclaredMethods())
                .filter(m -> m.getDeclaredAnnotation(Stub.class) != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Couldn't find any upcall stub provider in " + tClass));
            try {
                target = MethodHandles.publicLookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            final var returnType = method.getReturnType();
            final MemoryLayout[] memoryLayouts = Arrays.stream(method.getParameters())
                .map(p -> withSizedSeg(toMemoryLayout(p.getType()), p.getDeclaredAnnotation(SizedSeg.class)))
                .toArray(MemoryLayout[]::new);
            if (returnType == void.class) {
                descriptor = FunctionDescriptor.ofVoid(memoryLayouts);
            } else {
                descriptor = FunctionDescriptor.of(withSizedSeg(toMemoryLayout(returnType), method.getDeclaredAnnotation(SizedSeg.class)),
                    memoryLayouts);
            }
        }

        private static MemoryLayout withSizedSeg(MemoryLayout layout, SizedSeg sizedSeg) {
            if (sizedSeg != null && layout instanceof AddressLayout addressLayout) {
                return addressLayout.withTargetLayout(MemoryLayout.sequenceLayout(sizedSeg.value(), ValueLayout.JAVA_BYTE));
            }
            return layout;
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
            return LINKER.downcallHandle(stub, descriptor());
        }

        /**
         * Wraps the given upcall stub segment.
         *
         * @param stub     the upcall stub segment
         * @param function the function that transforms the given method handle into the downcall type.
         *                 The {@link Arena} is wrapped in a {@link Supplier} and you should store it with a variable
         * @return the downcall type
         */
        public T wrap(MemorySegment stub, Function<MethodHandle, T> function) {
            if (stub.address() == 0L) return null;
            return function.apply(downcall(stub));
        }

        /**
         * {@return the descriptor of this type}
         */
        public FunctionDescriptor descriptor() {
            return descriptor;
        }

        private static MemoryLayout toMemoryLayout(Class<?> carrier) {
            if (carrier == boolean.class) {
                return ValueLayout.JAVA_BOOLEAN;
            } else if (carrier == char.class) {
                return ValueLayout.JAVA_CHAR;
            } else if (carrier == byte.class) {
                return ValueLayout.JAVA_BYTE;
            } else if (carrier == short.class) {
                return ValueLayout.JAVA_SHORT;
            } else if (carrier == int.class) {
                return ValueLayout.JAVA_INT;
            } else if (carrier == float.class) {
                return ValueLayout.JAVA_FLOAT;
            } else if (carrier == long.class) {
                return ValueLayout.JAVA_LONG;
            } else if (carrier == double.class) {
                return ValueLayout.JAVA_DOUBLE;
            } else if (carrier == MemorySegment.class) {
                return ValueLayout.ADDRESS;
            } else {
                throw new IllegalArgumentException("Unsupported carrier: " + carrier.getName());
            }
        }
    }

    /**
     * The base container of {@link Arena} and {@link Upcall}.
     *
     * @param <T> the type of the upcall
     * @author squid233
     * @since 0.1.0
     */
    abstract class BaseContainer<T extends Upcall> implements Upcall {
        /**
         * The arena.
         */
        protected final Arena arena;
        /**
         * The upcall delegate.
         */
        protected final T delegate;

        /**
         * Creates a base container.
         *
         * @param arena    the arena
         * @param delegate the upcall delegate
         */
        public BaseContainer(Arena arena, T delegate) {
            this.arena = arena;
            this.delegate = delegate;
        }
    }
}
