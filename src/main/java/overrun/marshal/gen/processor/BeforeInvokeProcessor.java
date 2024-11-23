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

import overrun.marshal.gen.ConvertedClassType;
import overrun.marshal.gen.DowncallMethodParameter;
import overrun.marshal.gen.DowncallMethodType;
import overrun.marshal.gen.Ref;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

/**
 * Insert codes before invoking the downcall handle.
 * <p>
 * The default operation transforms {@link Ref @Ref} annotated arrays with {@link MarshalProcessor}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class BeforeInvokeProcessor extends CodeInserter<BeforeInvokeProcessor.Context> {
    private BeforeInvokeProcessor() {
    }

    /**
     * The context.
     *
     * @param methodType    the method type
     * @param refSlotList   the ref slots
     * @param allocatorSlot the slot of the allocator
     */
    public record Context(
        DowncallMethodType methodType,
        int[] refSlotList,
        int allocatorSlot
    ) {
    }

    @SuppressWarnings("preview")
    @Override
    public void process(CodeBuilder builder, Context context) {
        var parameters = context.methodType.parameters();
        for (int i = 0, size = parameters.size(); i < size; i++) {
            DowncallMethodParameter parameter = parameters.get(i);
            ConvertedClassType type1 = parameter.type();
            Class<?> type = type1.javaClass();
            if (type.isArray() &&
                parameter.ref()) {
                ProcessorType processorType = ProcessorTypes.fromClass(type);
                int local = builder.allocateLocal(TypeKind.ReferenceType);
                MarshalProcessor.getInstance().process(builder, processorType, new MarshalProcessor.Context(
                    context.allocatorSlot(),
                    builder.parameterSlot(i),
                    parameter.charset(),
                    type1.downcallClass()
                ));
                builder.astore(local);
                context.refSlotList[i] = local;
            }
        }
        super.process(builder, context);
    }

    /**
     * {@return the instance}
     */
    public static BeforeInvokeProcessor getInstance() {
        class Holder {
            static final BeforeInvokeProcessor INSTANCE = new BeforeInvokeProcessor();
        }
        return Holder.INSTANCE;
    }
}
