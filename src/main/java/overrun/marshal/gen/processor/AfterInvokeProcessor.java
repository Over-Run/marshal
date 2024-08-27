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

/**
 * Insert code after invoking the downcall handle.
 * <p>
 * The default operation runs {@link RefCopyProcessor}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class AfterInvokeProcessor extends CodeInserter<AfterInvokeProcessor.Context> {
    private AfterInvokeProcessor() {
    }

    /**
     * The context.
     *
     * @param parameters the parameter
     * @param refSlotMap the map
     */
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
                RefCopyProcessor.getInstance().process(builder,
                    type,
                    new RefCopyProcessor.Context(refSlotMap.get(parameter),
                        builder.parameterSlot(i),
                        StringCharset.getCharset(parameter)));
            }
        }
        super.process(builder, context);
    }

    /**
     * {@return the instance}
     */
    public static AfterInvokeProcessor getInstance() {
        class Holder {
            static final AfterInvokeProcessor INSTANCE = new AfterInvokeProcessor();
        }
        return Holder.INSTANCE;
    }
}
