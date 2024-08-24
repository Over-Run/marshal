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

import overrun.marshal.internal.StringCharset;

import java.lang.classfile.CodeBuilder;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static overrun.marshal.internal.Constants.*;

/**
 * insert code after invoke
 *
 * @author squid233
 * @since 0.1.0
 */
public final class AfterInvokeProcessor extends CodeInserter<AfterInvokeProcessor.Context> {
    public record Context(
        List<Parameter> parameters,
        Map<Parameter, Integer> refSlotMap
    ) {
    }

    @Override
    public void process(CodeBuilder builder, Context context) {
        List<Parameter> parameters = context.parameters();
        var refSlotMap = context.refSlotMap();
        for (int i = 0, size = parameters.size(); i < size; i++) {
            Parameter parameter = parameters.get(i);
            if (refSlotMap.containsKey(parameter)) {
                ProcessorType type = ProcessorTypes.fromParameter(parameter);
                // TODO: ref processor
                if (type instanceof ProcessorType.Array array) {
                    int parameterSlot = builder.parameterSlot(i);
                    int refSlot = refSlotMap.get(parameter);
                    builder
                        .aload(refSlot)
                        .aload(parameterSlot)
                        .invokestatic(CD_Unmarshal, "copy", switch (array.componentType()) {
                            case ProcessorType.Str _ ->
                                StringCharset.getCharset(builder, StringCharset.getCharset(parameter)) ?
                                    MTD_void_MemorySegment_StringArray_Charset :
                                    MTD_void_MemorySegment_StringArray;
                            case ProcessorType.Value value -> switch (value) {
                                case BOOLEAN -> MTD_void_MemorySegment_booleanArray;
                                case CHAR -> MTD_void_MemorySegment_charArray;
                                case BYTE -> MTD_void_MemorySegment_byteArray;
                                case SHORT -> MTD_void_MemorySegment_shortArray;
                                case INT -> MTD_void_MemorySegment_intArray;
                                case LONG -> MTD_void_MemorySegment_longArray;
                                case FLOAT -> MTD_void_MemorySegment_floatArray;
                                case DOUBLE -> MTD_void_MemorySegment_doubleArray;
                                case ADDRESS -> MTD_void_MemorySegment_MemorySegmentArray;
                            };
                            default -> throw new IllegalStateException("Unexpected value: " + array.componentType());
                        });
                }
            }
        }
        super.process(builder, context);
    }

    public static AfterInvokeProcessor getInstance() {
        class Holder {
            static final AfterInvokeProcessor INSTANCE = new AfterInvokeProcessor();
        }
        return Holder.INSTANCE;
    }
}
