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
 * Insert code to copy from a segment to an array.
 * <p>
 * The inserted code represents void type.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class RefCopyProcessor extends TypedCodeProcessor<RefCopyProcessor.Context> {
    private RefCopyProcessor() {
    }

    /**
     * The context.
     *
     * @param srcSegmentSlot the slot of the source memory segment
     * @param dstArraySlot   the slot of the destination array
     * @param charset        the charset annotation value
     */
    public record Context(
        int srcSegmentSlot,
        int dstArraySlot,
        String charset
    ) {
    }

    @Override
    public boolean process(CodeBuilder builder, ProcessorType type, Context context) {
        if (!(type instanceof ProcessorType.Array(ProcessorType componentType))) {
            return true;
        }
        return switch (componentType) {
            case ProcessorType.Str _ -> {
                builder.aload(context.srcSegmentSlot())
                    .aload(context.dstArraySlot())
                    .invokestatic(CD_Unmarshal,
                        "copy",
                        CharsetProcessor.process(builder, context.charset()) ?
                            MTD_void_MemorySegment_StringArray_Charset :
                            MTD_void_MemorySegment_StringArray);
                yield true;
            }
            case ProcessorType.Value value -> {
                builder.aload(context.srcSegmentSlot())
                    .aload(context.dstArraySlot())
                    .invokestatic(CD_Unmarshal,
                        "copy",
                        switch (value) {
                            case BOOLEAN -> MTD_void_MemorySegment_booleanArray;
                            case CHAR -> MTD_void_MemorySegment_charArray;
                            case BYTE -> MTD_void_MemorySegment_byteArray;
                            case SHORT -> MTD_void_MemorySegment_shortArray;
                            case INT -> MTD_void_MemorySegment_intArray;
                            case LONG -> MTD_void_MemorySegment_longArray;
                            case FLOAT -> MTD_void_MemorySegment_floatArray;
                            case DOUBLE -> MTD_void_MemorySegment_doubleArray;
                            case ADDRESS -> MTD_void_MemorySegment_MemorySegmentArray;
                        });
                yield true;
            }
            default -> super.process(builder, type, context);
        };
    }

    /**
     * {@return the instance}
     */
    public static RefCopyProcessor getInstance() {
        class Holder {
            static final RefCopyProcessor INSTANCE = new RefCopyProcessor();
        }
        return Holder.INSTANCE;
    }
}
