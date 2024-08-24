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

import static java.lang.constant.ConstantDescs.MTD_void;
import static overrun.marshal.internal.Constants.CD_MemoryStack;

/**
 * insert code before return
 *
 * @author squid233
 * @since 0.1.0
 */
public final class BeforeReturnProcessor extends BaseProcessor<BeforeReturnProcessor.Context> {
    public record Context(CodeBuilder builder, boolean hasMemoryStack) {
    }

    @Override
    public boolean process(Context context) {
        if (context.hasMemoryStack()) {
            context.builder().invokestatic(CD_MemoryStack,
                "popLocal",
                MTD_void,
                true);
        }
        return super.process(context);
    }

    public static BeforeReturnProcessor getInstance() {
        class Holder {
            static final BeforeReturnProcessor INSTANCE = new BeforeReturnProcessor();
        }
        return Holder.INSTANCE;
    }
}
