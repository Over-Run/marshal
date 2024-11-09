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
import overrun.marshal.struct.ByValue;

import java.lang.constant.*;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * @author squid233
 * @since 0.1.0
 */
public record DowncallMethodParameter(
    Class<?> type,
    boolean byValue,
    boolean ref,
    long sized,
    String charset,
    String canonicalType
) implements Constable {
    public static DowncallMethodParameter of(Parameter parameter) {
        CanonicalType anCanonicalType = parameter.getDeclaredAnnotation(CanonicalType.class);
        Sized anSized = parameter.getDeclaredAnnotation(Sized.class);
        StrCharset anStrCharset = parameter.getDeclaredAnnotation(StrCharset.class);

        return new DowncallMethodParameter(parameter.getType(),
            parameter.getDeclaredAnnotation(ByValue.class) != null,
            parameter.getDeclaredAnnotation(Ref.class) != null,
            anSized != null ? anSized.value() : -1,
            anStrCharset != null ? anStrCharset.value() : null,
            anCanonicalType != null ? anCanonicalType.value() : null);
    }

    @Override
    public Optional<Desc> describeConstable() {
        return Optional.of(new Desc(ClassDesc.ofDescriptor(type.descriptorString()), byValue, ref, sized, charset, canonicalType));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (byValue) {
            sb.append("[ByValue]");
        }
        if (canonicalType != null) {
            sb.append("[CanonicalType=").append(canonicalType).append(']');
        }
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
        private final ClassDesc type;
        private final boolean byValue;
        private final boolean ref;
        private final long sized;
        private final String charset;
        private final String canonicalType;

        public Desc(ClassDesc type, boolean byValue, boolean ref, long sized, String charset, String canonicalType) {
            super(Constants.BSM_DowncallFactory_createDowncallMethodParameter,
                ConstantDescs.DEFAULT_NAME,
                Constants.CD_DowncallMethodParameter,
                type,
                byValue ? ConstantDescs.TRUE : ConstantDescs.FALSE,
                ref ? ConstantDescs.TRUE : ConstantDescs.FALSE,
                sized,
                charset != null ? charset : ConstantDescs.NULL,
                canonicalType != null ? canonicalType : ConstantDescs.NULL);
            this.type = type;
            this.byValue = byValue;
            this.ref = ref;
            this.sized = sized;
            this.charset = charset;
            this.canonicalType = canonicalType;
        }

        @Override
        public DowncallMethodParameter resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new DowncallMethodParameter(type.resolveConstantDesc(lookup), byValue, ref, sized, charset, canonicalType);
        }
    }
}
