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

import overrun.marshal.gen.Sized;

import java.lang.classfile.CodeBuilder;
import java.lang.reflect.Parameter;
import java.util.List;

import static overrun.marshal.internal.Constants.*;

/**
 * Insert check methods at the beginning of the method body.
 * <p>
 * The default operation inserts {@link overrun.marshal.Checks#checkArraySize(long, int) Checks::checkArraySize}
 * for arrays annotated with {@link Sized @Sized}.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class CheckProcessor extends CodeInserter<CheckProcessor.Context> {
    private CheckProcessor() {
    }

    /**
     * The context.
     *
     * @param parameters the parameters
     */
    public record Context(List<Parameter> parameters) {
    }

    @Override
    public void process(CodeBuilder builder, Context context) {
        List<Parameter> parameters = context.parameters();
        for (int i = 0, size = parameters.size(); i < size; i++) {
            Parameter parameter = parameters.get(i);
            if (parameter.getType().isArray()) {
                Sized sized = parameter.getDeclaredAnnotation(Sized.class);
                if (sized != null) {
                    builder.ldc(sized.value())
                        .aload(builder.parameterSlot(i))
                        .arraylength()
                        .invokestatic(CD_Checks,
                            "checkArraySize",
                            MTD_void_long_int);
                }
            }
        }
        super.process(builder, context);
    }

    /**
     * {@return the instance}
     */
    public static CheckProcessor getInstance() {
        class Holder {
            static final CheckProcessor INSTANCE = new CheckProcessor();
        }
        return Holder.INSTANCE;
    }
}
