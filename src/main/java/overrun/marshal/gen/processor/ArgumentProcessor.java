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

import overrun.marshal.gen.Type;
import overrun.marshal.internal.StringCharset;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static overrun.marshal.internal.Constants.*;

/**
 * Method argument processor
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ArgumentProcessor implements Processor<ProcessorType, ArgumentProcessorContext> {
    private static final ArgumentProcessor INSTANCE = new ArgumentProcessor();
    private final List<Processor<ProcessorType, ArgumentProcessorContext>> list = new ArrayList<>(0);

    private ArgumentProcessor() {
    }

    @SuppressWarnings("preview")
    public boolean process(CodeBuilder builder, ProcessorType type, ArgumentProcessorContext context) {
        switch (type) {
            case ProcessorType.Value value -> {
                if (value == ProcessorType.Value.BOOLEAN &&
                    context.convert() != null) {
                    final Type convertType = context.convert().value();
                    builder.loadLocal(
                        value.typeKind(),
                        context.parameterSlot()
                    ).invokestatic(CD_Marshal,
                        marshalFromBooleanMethod(convertType),
                        MethodTypeDesc.of(convertType.classDesc(), CD_boolean));
                } else {
                    builder.loadLocal(
                        value.typeKind().asLoadable(),
                        context.parameterSlot()
                    );
                }
            }
            case ProcessorType.Allocator _ -> builder.aload(context.parameterSlot());
            case ProcessorType.Str _ -> {
                builder.aload(context.allocatorSlot())
                    .aload(context.parameterSlot());
                if (StringCharset.getCharset(builder, context.parameter())) {
                    builder.invokestatic(CD_Marshal,
                        "marshal",
                        MTD_MemorySegment_SegmentAllocator_String_Charset);
                } else {
                    builder.invokestatic(CD_Marshal,
                        "marshal",
                        MTD_MemorySegment_SegmentAllocator_String);
                }
            }
            case ProcessorType.Addr _ -> builder
                .aload(context.parameterSlot())
                .invokestatic(CD_Marshal,
                    "marshal",
                    MethodTypeDesc.of(type.downcallClassDesc(), type.marshalClassDesc()));
            case ProcessorType.Upcall _ -> builder
                .aload(context.allocatorSlot())
                .checkcast(CD_Arena)
                .aload(context.parameterSlot())
                .invokestatic(CD_Marshal,
                    "marshal",
                    MTD_MemorySegment_Arena_Upcall);
            case ProcessorType.Array array -> {
                final ProcessorType componentType = array.componentType();
                final boolean isStringArray = componentType instanceof ProcessorType.Str;
                final boolean isUpcallArray = componentType instanceof ProcessorType.Upcall;
                builder.aload(context.allocatorSlot());
                if (isUpcallArray) {
                    builder.checkcast(CD_Arena);
                }
                builder.aload(context.parameterSlot());
                if (isStringArray && StringCharset.getCharset(builder, context.parameter())) {
                    builder.invokestatic(CD_Marshal,
                        "marshal",
                        MTD_MemorySegment_SegmentAllocator_StringArray_Charset);
                } else {
                    builder.invokestatic(CD_Marshal,
                        "marshal",
                        MethodTypeDesc.of(CD_MemorySegment,
                            isUpcallArray ? CD_Arena : CD_SegmentAllocator,
                            array.marshalClassDesc()));
                }
            }
            default -> {
                for (var processor : list) {
                    if (!processor.process(builder, type, context)) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Registers a processor
     *
     * @param processor the processor
     */
    public void registerProcessor(Processor<ProcessorType, ArgumentProcessorContext> processor) {
        list.add(processor);
    }

    /**
     * {@return this}
     */
    public static ArgumentProcessor getInstance() {
        return INSTANCE;
    }

    private static String marshalFromBooleanMethod(Type convertType) {
        return switch (convertType) {
            case CHAR -> "marshalAsChar";
            case BYTE -> "marshalAsByte";
            case SHORT -> "marshalAsShort";
            case INT -> "marshalAsInt";
            case LONG -> "marshalAsLong";
            case FLOAT -> "marshalAsFloat";
            case DOUBLE -> "marshalAsDouble";
        };
    }
}
