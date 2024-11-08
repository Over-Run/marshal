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

package overrun.marshal.gen;

import overrun.marshal.internal.Constants;

import java.lang.constant.*;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * @author squid233
 * @since 0.1.0
 */
public record DowncallMethodParameter(
    Class<?> type,
    boolean ref,
    long sized,
    String charset
) implements Constable {
    @Override
    public Optional<? extends ConstantDesc> describeConstable() {
        return Optional.of(new Desc(ClassDesc.ofDescriptor(type.descriptorString()), ref, sized, charset));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ref) {
            sb.append("[Ref]");
        }
        if (sized >= 0) {
            sb.append("[Sized=").append(sized).append(']');
        }
        if (charset != null) {
            sb.append("[StrCharset=").append(charset).append(']');
        }
        sb.append(type.getSimpleName());
        return sb.toString();
    }

    public static final class Desc extends DynamicConstantDesc<DowncallMethodParameter> {
        private final boolean ref;
        private final long sized;
        private final String charset;

        public Desc(ClassDesc type, boolean ref, long sized, String charset) {
            super(Constants.BSM_DowncallFactory_createDowncallMethodParameter,
                ConstantDescs.DEFAULT_NAME,
                type,
                ref ? ConstantDescs.TRUE : ConstantDescs.FALSE,
                sized,
                charset);
            this.ref = ref;
            this.sized = sized;
            this.charset = charset;
        }

        @Override
        public DowncallMethodParameter resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new DowncallMethodParameter(constantType().resolveConstantDesc(lookup), ref, sized, charset);
        }
    }
}
