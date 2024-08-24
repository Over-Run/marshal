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

import overrun.marshal.gen.Ref;
import overrun.marshal.internal.StringCharset;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

/**
 * insert codes before invoke
 *
 * @author squid233
 * @since 0.1.0
 */
public final class BeforeInvokeProcessor extends BaseProcessor<BeforeInvokeProcessor.Context> {
    public record Context(
        CodeBuilder builder,
        List<Parameter> parameters,
        Map<Parameter, Integer> refSlot,
        int allocatorSlot
    ) {
    }

    @Override
    public boolean process(Context context) {
        CodeBuilder builder = context.builder();
        List<Parameter> parameters = context.parameters();
        for (int i = 0, size = parameters.size(); i < size; i++) {
            Parameter parameter = parameters.get(i);
            if (parameter.getType().isArray() &&
                parameter.getDeclaredAnnotation(Ref.class) != null) {
                int local = builder.allocateLocal(TypeKind.ReferenceType);
                context.refSlot().put(parameter, local);
                MarshalProcessor marshalProcessor = MarshalProcessor.getInstance();
                MarshalProcessor.Context context1 = new MarshalProcessor.Context(builder,
                    ProcessorTypes.fromParameter(parameter),
                    StringCharset.getCharset(parameter),
                    builder.parameterSlot(i),
                    context.allocatorSlot()
                );
                marshalProcessor.checkProcessed(marshalProcessor.process(context1), context1);
                builder.astore(local);
            }
        }
        return super.process(context);
    }

    public static BeforeInvokeProcessor getInstance() {
        class Holder {
            static final BeforeInvokeProcessor INSTANCE = new BeforeInvokeProcessor();
        }
        return Holder.INSTANCE;
    }
}
