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
import java.lang.reflect.Parameter;
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

    public DowncallMethodType(String entrypoint, Class<?> returnType, DowncallMethodParameter[] parameters, boolean byValue, boolean critical, boolean criticalAllowHeapAccess, long sized, String charset) {
        this.entrypoint = entrypoint;
        this.returnType = returnType;
        this.parameters = parameters;
        this.byValue = byValue;
        this.critical = critical;
        this.criticalAllowHeapAccess = criticalAllowHeapAccess;
        this.sized = sized;
        this.charset = charset;
    }

    public static DowncallMethodType of(Method method) {
        Critical anCritical = method.getDeclaredAnnotation(Critical.class);
        Sized anSized = method.getDeclaredAnnotation(Sized.class);
        StrCharset anStrCharset = method.getDeclaredAnnotation(StrCharset.class);

        return new DowncallMethodType(getMethodEntrypoint(method),
            method.getReturnType(),
            getMethodParameters(method),
            method.getDeclaredAnnotation(ByValue.class) != null,
            anCritical != null,
            anCritical != null && anCritical.allowHeapAccess(),
            anSized != null ? anSized.value() : -1,
            anStrCharset != null && !anStrCharset.value().isBlank() ? anStrCharset.value() : null);
    }

    private static DowncallMethodParameter[] getMethodParameters(Method method) {
        Parameter[] p = method.getParameters();
        DowncallMethodParameter[] parameters = new DowncallMethodParameter[p.length];
        for (int i = 0; i < p.length; i++) {
            Parameter parameter = p[i];
            Sized anSized = parameter.getDeclaredAnnotation(Sized.class);
            StrCharset anStrCharset = parameter.getDeclaredAnnotation(StrCharset.class);

            parameters[i] = new DowncallMethodParameter(parameter.getType(),
                parameter.getDeclaredAnnotation(Ref.class) != null,
                anSized != null ? anSized.value() : -1,
                anStrCharset != null && !anStrCharset.value().isBlank() ? anStrCharset.value() : null);
        }
        return parameters;
    }

    private static String getMethodEntrypoint(Method method) {
        final Entrypoint entrypoint = method.getDeclaredAnnotation(Entrypoint.class);
        return (entrypoint == null || entrypoint.value().isBlank()) ?
            method.getName() :
            entrypoint.value();
    }

    @Override
    public Optional<? extends ConstantDesc> describeConstable() {
        return Optional.of(new Desc(entrypoint, ClassDesc.ofDescriptor(returnType.descriptorString()), parameters, byValue, critical, criticalAllowHeapAccess, sized, charset));
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (byValue) {
            sb.append("[ByValue]");
        }
        if (sized >= 0) {
            sb.append("[Sized=").append(sized).append(']');
        }
        if (charset != null) {
            sb.append("[StrCharset=").append(charset).append(']');
        }
        sb.append(returnType.getSimpleName()).append(' ');
        if (critical) {
            sb.append("[Critical allowHeapAccess=").append(criticalAllowHeapAccess).append(']');
        }
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
        private final DowncallMethodParameter[] parameters;
        private final boolean byValue;
        private final boolean critical;
        private final boolean criticalAllowHeapAccess;
        private final long sized;
        private final String charset;

        public Desc(String entrypoint, ClassDesc returnType, DowncallMethodParameter[] parameters, boolean byValue, boolean critical, boolean criticalAllowHeapAccess, long sized, String charset) {
            ConstantDesc[] args = new ConstantDesc[parameters.length + 1 + 1 + 1 + 1];
            args[0] = byValue ? ConstantDescs.TRUE : ConstantDescs.FALSE;
            args[1] = critical ? ConstantDescs.TRUE : ConstantDescs.FALSE;
            args[2] = sized;
            args[3] = entrypoint;
            for (int i = 0; i < parameters.length; i++) {
                args[i + 4] = parameters[i].describeConstable().orElseThrow();
            }
            super(Constants.BSM_DowncallFactory_createDowncallMethodType, entrypoint, returnType, args);
            this.parameters = parameters;
            this.byValue = byValue;
            this.critical = critical;
            this.criticalAllowHeapAccess = criticalAllowHeapAccess;
            this.sized = sized;
            this.charset = charset;
        }

        @Override
        public DowncallMethodType resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new DowncallMethodType(constantName(), constantType().resolveConstantDesc(lookup), parameters, byValue, critical, criticalAllowHeapAccess, sized, charset);
        }
    }
}
