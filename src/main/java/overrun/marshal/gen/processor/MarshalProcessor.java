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

import java.lang.classfile.CodeBuilder;

import static overrun.marshal.internal.Constants.*;

/**
 * Insert marshal (Java-to-C) method.
 * <p>
 * The inserted bytecode must represent a type specified by the given processor type with
 * {@link ProcessorType#downcallClassDesc() downcallClassDesc}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class MarshalProcessor extends TypedCodeProcessor<MarshalProcessor.Context> {
    private MarshalProcessor() {
    }

    /**
     * The context.
     *
     * @param allocatorSlot the slot of the allocator
     * @param variableSlot  the slot of the value
     * @param charset       the charset annotation value
     */
    public record Context(
        int allocatorSlot,
        int variableSlot,
        String charset,
        Class<?> downcallClass
    ) {
    }

    @SuppressWarnings("preview")
    @Override
    public boolean process(CodeBuilder builder, ProcessorType type, Context context) {
        final int allocatorSlot = context.allocatorSlot();
        final int variableSlot = context.variableSlot();
        switch (type) {
            case ProcessorType.Allocator _ -> builder.aload(variableSlot);
            case ProcessorType.Custom _ -> {
                return super.process(builder, type, context);
            }
            case ProcessorType.Void _ -> throw new AssertionError("should not reach here");
            case ProcessorType.Array array -> {
                switch (array.componentType()) {
                    case ProcessorType.Allocator _, ProcessorType.Array _, ProcessorType.Custom _ -> {
                        return super.process(builder, type, context);
                    }
                    case ProcessorType.Void _ -> throw new AssertionError("should not reach here");
                    case ProcessorType.Str _ -> builder
                        .aload(allocatorSlot)
                        .aload(variableSlot)
                        .invokestatic(CD_Marshal,
                            "marshal",
                            CharsetProcessor.process(builder, context.charset()) ?
                                MTD_MemorySegment_SegmentAllocator_StringArray_Charset :
                                MTD_MemorySegment_SegmentAllocator_StringArray);
                    case ProcessorType.Struct _ -> builder
                        .aload(allocatorSlot)
                        .aload(variableSlot)
                        .invokestatic(CD_Marshal,
                            "marshal",
                            MTD_MemorySegment_SegmentAllocator_StructArray);
                    case ProcessorType.Upcall<?> _ -> builder
                        .aload(allocatorSlot)
                        .aload(variableSlot)
                        .invokestatic(CD_Marshal,
                            "marshal",
                            MTD_MemorySegment_Arena_UpcallArray);
                    case ProcessorType.Value value -> builder
                        .aload(allocatorSlot)
                        .aload(variableSlot)
                        .invokestatic(CD_Marshal,
                            "marshal",
                            switch (value) {
                                case BOOLEAN -> MTD_MemorySegment_SegmentAllocator_booleanArray;
                                case CHAR -> MTD_MemorySegment_SegmentAllocator_charArray;
                                case BYTE -> MTD_MemorySegment_SegmentAllocator_byteArray;
                                case SHORT -> MTD_MemorySegment_SegmentAllocator_shortArray;
                                case INT -> MTD_MemorySegment_SegmentAllocator_intArray;
                                case LONG -> MTD_MemorySegment_SegmentAllocator_longArray;
                                case FLOAT -> MTD_MemorySegment_SegmentAllocator_floatArray;
                                case DOUBLE -> MTD_MemorySegment_SegmentAllocator_doubleArray;
                                case ADDRESS -> MTD_MemorySegment_SegmentAllocator_MemorySegmentArray;
                            });
                }
            }
            case ProcessorType.Str _ -> builder
                .aload(allocatorSlot)
                .aload(variableSlot)
                .invokestatic(CD_Marshal,
                    "marshal",
                    CharsetProcessor.process(builder, context.charset()) ?
                        MTD_MemorySegment_SegmentAllocator_String_Charset :
                        MTD_MemorySegment_SegmentAllocator_String);
            case ProcessorType.Struct _ -> builder
                .aload(variableSlot)
                .invokestatic(CD_Marshal,
                    "marshal",
                    MTD_MemorySegment_Struct);
            case ProcessorType.Upcall<?> _ -> builder
                .aload(allocatorSlot)
                .aload(variableSlot)
                .invokestatic(CD_Marshal,
                    "marshal",
                    MTD_MemorySegment_Arena_Upcall);
            case ProcessorType.Value value -> {
                builder.loadLocal(value.typeKind(), variableSlot);
                if (value == ProcessorType.Value.BOOLEAN) {
                    if (context.downcallClass == char.class)
                        builder.invokestatic(CD_Marshal, "marshalAsChar", MTD_char_boolean);
                    else if (context.downcallClass == byte.class)
                        builder.invokestatic(CD_Marshal, "marshalAsByte", MTD_byte_boolean);
                    else if (context.downcallClass == short.class)
                        builder.invokestatic(CD_Marshal, "marshalAsShort", MTD_short_boolean);
                    else if (context.downcallClass == int.class)
                        builder.invokestatic(CD_Marshal, "marshalAsInt", MTD_int_boolean);
                    else if (context.downcallClass == long.class)
                        builder.invokestatic(CD_Marshal, "marshalAsLong", MTD_long_boolean);
                    else if (context.downcallClass == float.class)
                        builder.invokestatic(CD_Marshal, "marshalAsFloat", MTD_float_boolean);
                    else if (context.downcallClass == double.class)
                        builder.invokestatic(CD_Marshal, "marshalAsDouble", MTD_double_boolean);
                }
            }
        }
        return true;
    }

    /**
     * {@return the instance}
     */
    public static MarshalProcessor getInstance() {
        class Holder {
            static final MarshalProcessor INSTANCE = new MarshalProcessor();
        }
        return Holder.INSTANCE;
    }
}
