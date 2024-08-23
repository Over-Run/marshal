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

import overrun.marshal.struct.StructAllocatorSpec;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import static java.lang.constant.ConstantDescs.*;
import static overrun.marshal.internal.Constants.CD_MemorySegment;
import static overrun.marshal.internal.Constants.CD_SegmentAllocator;

/**
 * Types to be processed
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
    static Struct struct(Class<?> typeClass, StructAllocatorSpec<?> allocatorSpec) {
        return new Struct(typeClass, allocatorSpec);
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
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }
    }

    /**
     * Primitive types, including {@link MemorySegment}
     */
    enum Value implements ProcessorType {
        /**
         * {@code boolean} type
         */
        BOOLEAN(CD_boolean, ValueLayout.JAVA_BOOLEAN),
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
        DOUBLE(CD_double, ValueLayout.JAVA_DOUBLE),
        /**
         * {@link MemorySegment} type
         */
        ADDRESS(CD_MemorySegment, ValueLayout.ADDRESS);

        private final ClassDesc classDesc;
        private final TypeKind typeKind;
        private final ValueLayout layout;

        Value(ClassDesc classDesc, ValueLayout layout) {
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

        /**
         * {@return the layout of this type}
         */
        public ValueLayout layout() {
            return layout;
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return classDesc;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
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

        /**
         * {@return the layout of this type}
         */
        public ValueLayout layout() {
            return layout;
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return classDesc;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
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
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE; // this is invalid
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
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.STACK;
        }
    }

    /**
     * {@link overrun.marshal.struct.Struct Struct}
     */
    final class Struct implements ProcessorType {
        private final Class<?> typeClass;
        private final StructAllocatorSpec<?> allocatorSpec;

        private Struct(Class<?> typeClass, StructAllocatorSpec<?> allocatorSpec) {
            this.typeClass = typeClass;
            this.allocatorSpec = allocatorSpec;
        }

        public Class<?> typeClass() {
            return typeClass;
        }

        public StructAllocatorSpec<?> allocatorSpec() {
            return allocatorSpec;
        }

        public void checkAllocator() {
            Objects.requireNonNull(allocatorSpec);
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.NONE;
        }
    }

    /**
     * {@link overrun.marshal.Upcall Upcall}
     */
    final class Upcall implements ProcessorType {
        /**
         * The instance
         */
        public static final Upcall INSTANCE = new Upcall();

        private Upcall() {
        }

        @Override
        public ClassDesc downcallClassDesc() {
            return CD_MemorySegment;
        }

        @Override
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.ARENA;
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
        public AllocatorRequirement allocationRequirement() {
            return AllocatorRequirement.stricter(AllocatorRequirement.STACK, componentType.allocationRequirement());
        }
    }

    /**
     * Custom type
     */
    non-sealed interface Custom extends ProcessorType {
    }
}
