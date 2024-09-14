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
import java.lang.constant.ClassDesc;

import static overrun.marshal.internal.Constants.*;

/**
 * Insert unmarshal (C-to-Java) method.
 * <p>
 * The inserted code must represent the original type in the context.
 *
 * @deprecated to be replaced with filter method handle
 * @author squid233
 * @since 0.1.0
 */
@Deprecated(since = "0.1.0-alpha.33")
public final class UnmarshalProcessor extends TypedCodeProcessor<UnmarshalProcessor.Context> {
    private UnmarshalProcessor() {
    }

    /**
     * The context.
     *
     * @param originalType the original type of the value. this is useful for invoking
     *                     {@link ProcessorTypes#fromClass(Class) ProcessorTypes::fromClass} in bytecode.
     * @param variableSlot the slot of the value
     * @param charset      the charset annotation value
     */
    public record Context(
        Class<?> originalType,
        int variableSlot,
        String charset
    ) {
    }

    @SuppressWarnings("preview")
    @Override
    public boolean process(CodeBuilder builder, ProcessorType type, Context context) {
        int variableSlot = context.variableSlot();
        switch (type) {
            case ProcessorType.Allocator _, ProcessorType.Custom _ -> {
                return super.process(builder, type, context);
            }
            case ProcessorType.Void _ -> {
            }
            case ProcessorType.Array array -> {
                switch (array.componentType()) {
                    case ProcessorType.Allocator _, ProcessorType.Array _, ProcessorType.BoolConvert _,
                         ProcessorType.Custom _, ProcessorType.Struct _, ProcessorType.Upcall<?> _ -> {
                        return super.process(builder, type, context);
                    }
                    case ProcessorType.Void _ -> throw new AssertionError("should not reach here");
                    case ProcessorType.Str _ -> builder
                        .aload(variableSlot)
                        .invokestatic(CD_Unmarshal,
                            "unmarshalAsStringArray",
                            CharsetProcessor.process(builder, context.charset()) ?
                                MTD_StringArray_MemorySegment_Charset :
                                MTD_StringArray_MemorySegment);
                    case ProcessorType.Value value -> builder
                        .aload(variableSlot)
                        .invokestatic(CD_Unmarshal,
                            switch (value) {
                                case BOOLEAN -> "unmarshalAsBooleanArray";
                                case CHAR -> "unmarshalAsCharArray";
                                case BYTE -> "unmarshalAsByteArray";
                                case SHORT -> "unmarshalAsShortArray";
                                case INT -> "unmarshalAsIntArray";
                                case LONG -> "unmarshalAsLongArray";
                                case FLOAT -> "unmarshalAsFloatArray";
                                case DOUBLE -> "unmarshalAsDoubleArray";
                                case ADDRESS -> "unmarshalAsAddressArray";
                            },
                            switch (value) {
                                case BOOLEAN -> MTD_booleanArray_MemorySegment;
                                case CHAR -> MTD_charArray_MemorySegment;
                                case BYTE -> MTD_byteArray_MemorySegment;
                                case SHORT -> MTD_shortArray_MemorySegment;
                                case INT -> MTD_intArray_MemorySegment;
                                case LONG -> MTD_longArray_MemorySegment;
                                case FLOAT -> MTD_floatArray_MemorySegment;
                                case DOUBLE -> MTD_doubleArray_MemorySegment;
                                case ADDRESS -> MTD_MemorySegmentArray_MemorySegment;
                            });
                }
            }
            case ProcessorType.BoolConvert boolConvert -> builder
                .loadLocal(boolConvert.typeKind(), variableSlot)
                .invokestatic(CD_Unmarshal,
                    "unmarshalAsBoolean",
                    switch (boolConvert) {
                        case CHAR -> MTD_boolean_char;
                        case BYTE -> MTD_boolean_byte;
                        case SHORT -> MTD_boolean_short;
                        case INT -> MTD_boolean_int;
                        case LONG -> MTD_boolean_long;
                        case FLOAT -> MTD_boolean_float;
                        case DOUBLE -> MTD_boolean_double;
                    });
            case ProcessorType.Str _ -> builder
                .aload(variableSlot)
                .invokestatic(CD_Unmarshal,
                    "unboundString",
                    CharsetProcessor.process(builder, context.charset()) ?
                        MTD_String_MemorySegment_Charset :
                        MTD_String_MemorySegment);
            case ProcessorType.Struct _ -> builder
                .ldc(ClassDesc.ofDescriptor(context.originalType().descriptorString()))
                .invokestatic(CD_ProcessorTypes, "fromClass", MTD_ProcessorType_Class)
                .checkcast(CD_ProcessorType$Struct)
                .invokevirtual(CD_ProcessorType$Struct, "checkAllocator", MTD_StructAllocatorSpec)
                .aload(variableSlot)
                .invokeinterface(CD_StructAllocatorSpec, "of", MTD_Object_MemorySegment);
            case ProcessorType.Upcall<?> _ -> builder
                .ldc(ClassDesc.ofDescriptor(context.originalType().descriptorString()))
                .invokestatic(CD_ProcessorTypes, "fromClass", MTD_ProcessorType_Class)
                .checkcast(CD_ProcessorType$Upcall)
                .invokevirtual(CD_ProcessorType$Upcall, "checkFactory", MTD_ProcessorType$Upcall$Factory)
                .aload(variableSlot)
                .invokeinterface(CD_ProcessorType$Upcall$Factory, "create", MTD_Upcall_MemorySegment);
            case ProcessorType.Value value -> builder.loadLocal(value.typeKind(), variableSlot);
        }
        return true;
    }

    /**
     * {@return the instance}
     */
    public static UnmarshalProcessor getInstance() {
        class Holder {
            static final UnmarshalProcessor INSTANCE = new UnmarshalProcessor();
        }
        return Holder.INSTANCE;
    }
}
