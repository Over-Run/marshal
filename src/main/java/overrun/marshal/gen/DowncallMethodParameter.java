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

/// A parameter of a downcall method.
///
/// @param type          the type of the parameter
/// @param byValue       `true` if pass-by-value
/// @param ref           `true` if returns from native function
/// @param sized         value of [Sized]
/// @param charset       value of [StrCharset]
/// @author squid233
/// @since 0.1.0
public record DowncallMethodParameter(
    ConvertedClassType type,
    boolean byValue,
    boolean ref,
    long sized,
    String charset
) implements Constable {
    /// Creates a downcall method parameter from the given parameter.
    ///
    /// @param parameter the parameter
    /// @return the downcall method parameter
    public static DowncallMethodParameter of(Parameter parameter) {
        Sized anSized = parameter.getDeclaredAnnotation(Sized.class);
        StrCharset anStrCharset = parameter.getDeclaredAnnotation(StrCharset.class);

        return new DowncallMethodParameter(ConvertedClassType.parameterType(parameter),
            parameter.getDeclaredAnnotation(ByValue.class) != null,
            parameter.getDeclaredAnnotation(Ref.class) != null,
            anSized != null ? anSized.value() : -1,
            anStrCharset != null ? anStrCharset.value() : null);
    }

    @Override
    public Optional<Desc> describeConstable() {
        return Optional.of(new Desc(type.describeConstable().orElseThrow(), byValue, ref, sized, charset));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (byValue) {
            sb.append("[ByValue]");
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
        sb.append(type);
        return sb.toString();
    }

    /// The constant desc
    public static final class Desc extends DynamicConstantDesc<DowncallMethodParameter> {
        private final ConvertedClassType.Desc type;
        private final boolean byValue;
        private final boolean ref;
        private final long sized;
        private final String charset;

        /// constructor
        ///
        /// @param type          type
        /// @param byValue       byValue
        /// @param ref           ref
        /// @param sized         sized
        /// @param charset       charset
        public Desc(ConvertedClassType.Desc type, boolean byValue, boolean ref, long sized, String charset) {
            super(Constants.BSM_DowncallFactory_createDowncallMethodParameter,
                ConstantDescs.DEFAULT_NAME,
                Constants.CD_DowncallMethodParameter,
                type,
                byValue ? ConstantDescs.TRUE : ConstantDescs.FALSE,
                ref ? ConstantDescs.TRUE : ConstantDescs.FALSE,
                sized,
                charset != null ? charset : ConstantDescs.NULL);
            this.type = type;
            this.byValue = byValue;
            this.ref = ref;
            this.sized = sized;
            this.charset = charset;
        }

        @Override
        public DowncallMethodParameter resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new DowncallMethodParameter(type.resolveConstantDesc(lookup), byValue, ref, sized, charset);
        }
    }
}
