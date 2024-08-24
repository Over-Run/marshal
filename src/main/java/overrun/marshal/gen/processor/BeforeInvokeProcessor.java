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
public final class BeforeInvokeProcessor extends CodeInserter<BeforeInvokeProcessor.Context> {
    public record Context(
        List<Parameter> parameters,
        Map<Parameter, Integer> refSlot,
        int allocatorSlot
    ) {
    }

    @SuppressWarnings("preview")
    @Override
    public void process(CodeBuilder builder, Context context) {
        List<Parameter> parameters = context.parameters();
        for (int i = 0, size = parameters.size(); i < size; i++) {
            Parameter parameter = parameters.get(i);
            if (parameter.getType().isArray() &&
                parameter.getDeclaredAnnotation(Ref.class) != null) {
                ProcessorType type = ProcessorTypes.fromParameter(parameter);
                ProcessorType refType = RefTypeTransformer.getInstance().process(type);
                TypeKind refTypeKind = TypeKind.from(refType.downcallClassDesc());
                int local = builder.allocateLocal(refTypeKind);
                MarshalProcessor.getInstance().process(builder, type, new MarshalProcessor.Context(
                    StringCharset.getCharset(parameter),
                    builder.parameterSlot(i),
                    context.allocatorSlot()
                ));
                builder.storeLocal(refTypeKind, local);
                context.refSlot().put(parameter, local);
            }
        }
        super.process(builder, context);
    }

    public static BeforeInvokeProcessor getInstance() {
        class Holder {
            static final BeforeInvokeProcessor INSTANCE = new BeforeInvokeProcessor();
        }
        return Holder.INSTANCE;
    }
}
