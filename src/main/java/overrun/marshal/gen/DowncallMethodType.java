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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallMethodType implements Constable {
    private final String entrypoint;
    private final Class<?> returnType;
    private final DowncallMethodParameter[] parameters;
    private final boolean byValue;
    private final boolean critical;
    private final boolean criticalAllowHeapAccess;
    private final long sized;
    private final String charset;
    private final String canonicalType;

    public DowncallMethodType(
        String entrypoint,
        Class<?> returnType,
        DowncallMethodParameter[] parameters,
        boolean byValue,
        boolean critical,
        boolean criticalAllowHeapAccess,
        long sized,
        String charset,
        String canonicalType
    ) {
        this.entrypoint = entrypoint;
        this.returnType = returnType;
        this.parameters = parameters;
        this.byValue = byValue;
        this.critical = critical;
        this.criticalAllowHeapAccess = criticalAllowHeapAccess;
        this.sized = sized;
        this.charset = charset;
        this.canonicalType = canonicalType;
    }

    public static DowncallMethodType of(Method method) {
        CanonicalType anCanonicalType = method.getDeclaredAnnotation(CanonicalType.class);
        Convert anConvert = method.getDeclaredAnnotation(Convert.class);
        Critical anCritical = method.getDeclaredAnnotation(Critical.class);
        Sized anSized = method.getDeclaredAnnotation(Sized.class);
        StrCharset anStrCharset = method.getDeclaredAnnotation(StrCharset.class);

        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class && anConvert != null) {
            returnType = anConvert.value().javaClass();
        }

        return new DowncallMethodType(getMethodEntrypoint(method),
            returnType,
            getMethodParameters(method),
            method.getDeclaredAnnotation(ByValue.class) != null,
            anCritical != null,
            anCritical != null && anCritical.allowHeapAccess(),
            anSized != null ? anSized.value() : -1,
            anStrCharset != null ? anStrCharset.value() : null,
            anCanonicalType != null ? anCanonicalType.value() : null);
    }

    private static DowncallMethodParameter[] getMethodParameters(Method method) {
        return Arrays.stream(method.getParameters())
            .map(DowncallMethodParameter::of)
            .toArray(DowncallMethodParameter[]::new);
    }

    private static String getMethodEntrypoint(Method method) {
        final Entrypoint entrypoint = method.getDeclaredAnnotation(Entrypoint.class);
        return (entrypoint == null || entrypoint.value().isBlank()) ?
            method.getName() :
            entrypoint.value();
    }

    @Override
    public Optional<Desc> describeConstable() {
        return Optional.of(new Desc(entrypoint, ClassDesc.ofDescriptor(returnType.descriptorString()), parameters, byValue, critical, criticalAllowHeapAccess, sized, charset, canonicalType));
    }

    public String entrypoint() {
        return entrypoint;
    }

    public Class<?> returnType() {
        return returnType;
    }

    public List<DowncallMethodParameter> parameters() {
        return Arrays.asList(parameters);
    }

    public boolean byValue() {
        return byValue;
    }

    public boolean critical() {
        return critical;
    }

    public boolean criticalAllowHeapAccess() {
        return criticalAllowHeapAccess;
    }

    public long sized() {
        return sized;
    }

    public String charset() {
        return charset;
    }

    public String canonicalType() {
        return canonicalType;
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
        if (critical) {
            sb.append("[Critical allowHeapAccess=").append(criticalAllowHeapAccess).append(']');
        }
        if (sized >= 0) {
            sb.append("[Sized=").append(sized).append(']');
        }
        if (charset != null) {
            sb.append("[StrCharset=").append(charset).append(']');
        }
        sb.append(returnType.getSimpleName()).append(' ');
        sb.append(entrypoint).append('(');
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameters[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    public static final class Desc extends DynamicConstantDesc<DowncallMethodType> {
        private final ClassDesc returnType;
        private final DowncallMethodParameter[] parameters;
        private final boolean byValue;
        private final boolean critical;
        private final boolean criticalAllowHeapAccess;
        private final long sized;
        private final String charset;
        private final String canonicalType;

        public Desc(
            String entrypoint,
            ClassDesc returnType,
            DowncallMethodParameter[] parameters,
            boolean byValue,
            boolean critical,
            boolean criticalAllowHeapAccess,
            long sized,
            String charset,
            String canonicalType
        ) {
            ConstantDesc[] args = new ConstantDesc[1 + 1 + 1 + 1 + 1 + 1 + 1 + parameters.length];
            args[0] = returnType;
            args[1] = byValue ? ConstantDescs.TRUE : ConstantDescs.FALSE;
            args[2] = critical ? ConstantDescs.TRUE : ConstantDescs.FALSE;
            args[3] = criticalAllowHeapAccess ? ConstantDescs.TRUE : ConstantDescs.FALSE;
            args[4] = sized;
            args[5] = charset != null ? charset : ConstantDescs.NULL;
            args[6] = canonicalType != null ? canonicalType : ConstantDescs.NULL;
            for (int i = 0; i < parameters.length; i++) {
                args[i + 7] = parameters[i].describeConstable().orElseThrow();
            }
            super(Constants.BSM_DowncallFactory_createDowncallMethodType, entrypoint, Constants.CD_DowncallMethodType, args);
            this.returnType = returnType;
            this.parameters = parameters;
            this.byValue = byValue;
            this.critical = critical;
            this.criticalAllowHeapAccess = criticalAllowHeapAccess;
            this.sized = sized;
            this.charset = charset;
            this.canonicalType = canonicalType;
        }

        @Override
        public DowncallMethodType resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new DowncallMethodType(constantName(), returnType.resolveConstantDesc(lookup), parameters, byValue, critical, criticalAllowHeapAccess, sized, charset, canonicalType);
        }
    }
}
