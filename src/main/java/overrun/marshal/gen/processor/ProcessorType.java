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

package overrun.marshal.gen.processor;

import org.jetbrains.annotations.Nullable;
import overrun.marshal.struct.StructAllocatorSpec;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Locale;

import static java.lang.constant.ConstantDescs.*;
import static overrun.marshal.internal.Constants.CD_MemorySegment;
import static overrun.marshal.internal.Constants.CD_SegmentAllocator;

/**
 * Processor types are used to remember the type of value to be processed in {@link Processor}.
 * <h2>Builtin type</h2>
 * Builtin types include 8 primitive types, {@link MemorySegment}, {@link String},
 * {@link overrun.marshal.struct.Struct Struct} without allocator and {@link overrun.marshal.Upcall Upcall}
 * without factory.
 * <h2>Custom type</h2>
 * For a custom type, you should implement the general superinterface {@link Custom} and register it via
 * {@link ProcessorTypes#register(Class, ProcessorType) ProcessorTypes::register}.
 * Common custom types include subclasses of {@link overrun.marshal.struct.Struct Struct}
 * and {@link overrun.marshal.Upcall Upcall}.
 * <p>
 * For {@code Struct} and {@code Upcall}, {@code ProcessorTypes} also provides
 * {@link ProcessorTypes#registerStruct(Class, StructAllocatorSpec) registerStruct} and
 * {@link ProcessorTypes#registerUpcall(Class, Upcall.Factory) registerUpcall} to conveniently register them.
 *
 * @author squid233
 * @since 0.1.0
 */
public sealed interface ProcessorType {
    /**
     * {@return the class desc for method handles (functions in C)}
     */
    ClassDesc downcallClassDesc();

    /**
     * {@return the memory layout for functions in C}
     */
    MemoryLayout downcallLayout();

    /**
     * {@return the allocator requirement}
     */
    AllocatorRequirement allocationRequirement();

    /**
     * Creates a struct processor type with the given type class and struct allocator.
     *
     * @param typeClass     the type class
     * @param allocatorSpec the struct allocator
     * @return a new processor type
     */
    static Struct struct(Class<?> typeClass, @Nullable StructAllocatorSpec<?> allocatorSpec) {
        return new Struct(typeClass, allocatorSpec);
    }

    /**
     * Creates an upcall processor type with the given type class and factory.
     *
     * @param typeClass the type class
     * @param factory   the factory
     * @param <T>       the upcall type
     * @return a new processor type
     */
    static <T extends overrun.marshal.Upcall> Upcall<T> upcall(Class<T> typeClass, @Nullable Upcall.Factory<T> factory) {
        return new Upcall<>(typeClass, factory);
    }

    /**
     * {@code void} type
     */
    final class Void implements ProcessorType {
        /**
         * The instance
         */
        public static final Void INSTANCE = new Void();

        private Void() {
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_void;
        }

        @Override
        public MemoryLayout downcallLayout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }

        @Override
        public String toString() {
            return "void";
        }
    }

    /**
     * Primitive types, including {@link MemorySegment}
     */
    enum Value implements ProcessorType {
        /**
         * {@code boolean} type
         */
        BOOLEAN(CD_boolean, ValueLayout.JAVA_BOOLEAN, "boolean"),
        /**
         * {@code char} type
         */
        CHAR(CD_char, ValueLayout.JAVA_CHAR, "char"),
        /**
         * {@code byte} type
         */
        BYTE(CD_byte, ValueLayout.JAVA_BYTE, "byte"),
        /**
         * {@code short} type
         */
        SHORT(CD_short, ValueLayout.JAVA_SHORT, "short"),
        /**
         * {@code int} type
         */
        INT(CD_int, ValueLayout.JAVA_INT, "int"),
        /**
         * {@code long} type
         */
        LONG(CD_long, ValueLayout.JAVA_LONG, "long"),
        /**
         * {@code float} type
         */
        FLOAT(CD_float, ValueLayout.JAVA_FLOAT, "float"),
        /**
         * {@code double} type
         */
        DOUBLE(CD_double, ValueLayout.JAVA_DOUBLE, "double"),
        /**
         * {@link MemorySegment} type
         */
        ADDRESS(CD_MemorySegment, ValueLayout.ADDRESS, MemorySegment.class.getSimpleName());

        private final ClassDesc classDesc;
        private final TypeKind typeKind;
        private final ValueLayout layout;
        private final String toStringValue;

        Value(ClassDesc classDesc, ValueLayout layout, String toStringValue) {
            this.classDesc = classDesc;
            this.typeKind = TypeKind.from(classDesc);
            this.layout = layout;
            this.toStringValue = toStringValue;
        }

        /**
         * {@return the type kind of this type}
         */
        public TypeKind typeKind() {
            return typeKind;
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return classDesc;
        }

        @Override
        public ValueLayout downcallLayout() {
            return layout;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }

        @Override
        public String toString() {
            return toStringValue;
        }
    }

    /**
     * Primitive types that are convertible with {@code boolean}.
     */
    enum BoolConvert implements ProcessorType {
        /**
         * {@code char} type
         */
        CHAR(CD_char, ValueLayout.JAVA_CHAR),
        /**
         * {@code byte} type
         */
        BYTE(CD_byte, ValueLayout.JAVA_BYTE),
        /**
         * {@code short} type
         */
        SHORT(CD_short, ValueLayout.JAVA_SHORT),
        /**
         * {@code int} type
         */
        INT(CD_int, ValueLayout.JAVA_INT),
        /**
         * {@code long} type
         */
        LONG(CD_long, ValueLayout.JAVA_LONG),
        /**
         * {@code float} type
         */
        FLOAT(CD_float, ValueLayout.JAVA_FLOAT),
        /**
         * {@code double} type
         */
        DOUBLE(CD_double, ValueLayout.JAVA_DOUBLE);

        private final ClassDesc classDesc;
        private final TypeKind typeKind;
        private final ValueLayout layout;

        BoolConvert(ClassDesc classDesc, ValueLayout layout) {
            this.classDesc = classDesc;
            this.typeKind = TypeKind.from(classDesc);
            this.layout = layout;
        }

        /**
         * {@return the type kind of this type}
         */
        public TypeKind typeKind() {
            return typeKind;
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return classDesc;
        }

        @Override
        public ValueLayout downcallLayout() {
            return layout;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * {@link SegmentAllocator}
     */
    final class Allocator implements ProcessorType {
        /**
         * The instance
         */
        public static final Allocator INSTANCE = new Allocator();

        private Allocator() {
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_SegmentAllocator;
        }

        @Override
        public MemoryLayout downcallLayout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE; // this is invalid
        }

        @Override
        public String toString() {
            return SegmentAllocator.class.getSimpleName();
        }
    }

    /**
     * {@link String}
     */
    final class Str implements ProcessorType {
        /**
         * The instance
         */
        public static final Str INSTANCE = new Str();

        private Str() {
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public ValueLayout downcallLayout() {
            return ValueLayout.ADDRESS;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.STACK;
        }

        @Override
        public String toString() {
            return String.class.getSimpleName();
        }
    }

    /**
     * {@link overrun.marshal.struct.Struct Struct}
     */
    final class Struct implements ProcessorType {
        private final Class<?> typeClass;
        @Nullable
        private final StructAllocatorSpec<?> allocatorSpec;

        private Struct(Class<?> typeClass, @Nullable StructAllocatorSpec<?> allocatorSpec) {
            this.typeClass = typeClass;
            this.allocatorSpec = allocatorSpec;
        }

        /**
         * {@return an exception with "No allocator" message}
         *
         * @param typeClass the type of the struct
         */
        public static IllegalStateException noAllocatorException(Class<?> typeClass) {
            return new IllegalStateException("No allocator registered for struct " + typeClass);
        }

        /**
         * {@return the type of the struct}
         */
        public Class<?> typeClass() {
            return typeClass;
        }

        /**
         * {@return the allocator}
         */
        @Nullable
        public StructAllocatorSpec<?> allocatorSpec() {
            return allocatorSpec;
        }

        /**
         * {@return the allocator}
         * Also checks whether the allocator is null or not.
         */
        public StructAllocatorSpec<?> checkAllocator() {
            if (allocatorSpec() != null) {
                return allocatorSpec();
            }
            throw noAllocatorException(typeClass());
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public MemoryLayout downcallLayout() {
            return allocatorSpec != null ? allocatorSpec.layout() : ValueLayout.ADDRESS;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }

        @Override
        public String toString() {
            return "Struct(" + typeClass + ")";
        }
    }

    /**
     * {@link overrun.marshal.Upcall Upcall}
     *
     * @param <T> upcall type
     */
    final class Upcall<T extends overrun.marshal.Upcall> implements ProcessorType {
        private final Class<T> typeClass;
        @Nullable
        private final Factory<T> factory;

        private Upcall(Class<T> typeClass, @Nullable Factory<T> factory) {
            this.typeClass = typeClass;
            this.factory = factory;
        }

        /**
         * {@return an exception with "No factory" message}
         *
         * @param typeClass the type of the upcall
         */
        public static IllegalStateException noFactoryException(Class<?> typeClass) {
            return new IllegalStateException("No factory registered for upcall " + typeClass);
        }

        /**
         * A factory that creates the upcall instance with a given upcall stub.
         *
         * @param <T> the type of the upcall
         */
        @FunctionalInterface
        public interface Factory<T extends overrun.marshal.Upcall> {
            /**
             * Creates an upcall instance with the given stub.
             *
             * @param stub the memory segment
             * @return the upcall instance
             */
            T create(MemorySegment stub);
        }

        /**
         * {@return the type of the upcall}
         */
        public Class<T> typeClass() {
            return typeClass;
        }

        /**
         * {@return the factory}
         */
        @Nullable
        public Factory<T> factory() {
            return factory;
        }

        /**
         * {@return the factory}
         * Also checks whether the factory is null or not.
         */
        public Factory<T> checkFactory() {
            if (factory() != null) {
                return factory();
            }
            throw noFactoryException(typeClass());
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public ValueLayout downcallLayout() {
            return ValueLayout.ADDRESS;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.ARENA;
        }

        @Override
        public String toString() {
            return "Upcall(" + typeClass + ")";
        }
    }

    /**
     * Array type
     *
     * @param componentType the component type
     */
    record Array(ProcessorType componentType) implements ProcessorType {
        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public ValueLayout downcallLayout() {
            return ValueLayout.ADDRESS;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.stricter(AllocatorRequirement.STACK, componentType.allocationRequirement());
        }

        @Override
        public String toString() {
            return componentType + "[]";
        }
    }

    /**
     * General superinterface of custom processor types
     */
    non-sealed interface Custom extends ProcessorType {
    }
}
