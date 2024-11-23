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

import overrun.marshal.gen.processor.AllocatorRequirement;
import overrun.marshal.gen.processor.ProcessorTypes;
import overrun.marshal.internal.Constants;
import overrun.marshal.struct.ByValue;

import java.lang.constant.*;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/// The method type of downcall method.
///
/// @author squid233
/// @since 0.1.0
public final class DowncallMethodType implements Constable {
    private final String entrypoint;
    private final ConvertedClassType returnType;
    private final DowncallMethodParameter[] parameters;
    private final boolean byValue;
    private final boolean critical;
    private final boolean criticalAllowHeapAccess;
    private final long sized;
    private final String charset;
    private final String canonicalType;
    private final AllocatorRequirement allocatorRequirement;

    /// constructor
    ///
    /// @param entrypoint              entrypoint
    /// @param returnType              returnType
    /// @param parameters              parameters
    /// @param byValue                 byValue
    /// @param critical                critical
    /// @param criticalAllowHeapAccess criticalAllowHeapAccess
    /// @param sized                   sized
    /// @param charset                 charset
    /// @param canonicalType           canonicalType
    public DowncallMethodType(
        String entrypoint,
        ConvertedClassType returnType,
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
        this.allocatorRequirement = Arrays.stream(parameters)
            .reduce(byValue ? AllocatorRequirement.ALLOCATOR : AllocatorRequirement.NONE,
                (r, p) -> AllocatorRequirement.stricter(r, ProcessorTypes.fromClass(p.type().javaClass()).allocationRequirement()),
                AllocatorRequirement::stricter);
    }

    /// Creates a downcall method type with the given method.
    ///
    /// @param method the method
    /// @return the method type
    public static DowncallMethodType of(Method method) {
        CanonicalType anCanonicalType = method.getDeclaredAnnotation(CanonicalType.class);
        Critical anCritical = method.getDeclaredAnnotation(Critical.class);
        Sized anSized = method.getDeclaredAnnotation(Sized.class);
        StrCharset anStrCharset = method.getDeclaredAnnotation(StrCharset.class);

        return new DowncallMethodType(getMethodEntrypoint(method),
            ConvertedClassType.returnType(method),
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
        return Optional.of(new Desc(entrypoint, returnType.describeConstable().orElseThrow(), parameters, byValue, critical, criticalAllowHeapAccess, sized, charset, canonicalType));
    }

    /// {@return the entrypoint}
    public String entrypoint() {
        return entrypoint;
    }

    /// {@return the return type}
    public ConvertedClassType returnType() {
        return returnType;
    }

    /// {@return the parameters}
    public List<DowncallMethodParameter> parameters() {
        return Arrays.asList(parameters);
    }

    /// {@return `true` if return-by-value}
    public boolean byValue() {
        return byValue;
    }

    /// {@return `true` if [_critical_][java.lang.foreign.Linker.Option#critical(boolean)]}
    public boolean critical() {
        return critical;
    }

    /// {@return value of [Critical]}
    public boolean criticalAllowHeapAccess() {
        return criticalAllowHeapAccess;
    }

    ///  {@return value of [Sized]}
    public long sized() {
        return sized;
    }

    /// {@return value of [StrCharset]}
    public String charset() {
        return charset;
    }

    /// {@return value of [CanonicalType]}
    public String canonicalType() {
        return canonicalType;
    }

    /// {@return the allocation requirement}
    public AllocatorRequirement allocatorRequirement() {
        return allocatorRequirement;
    }

    /// {@return the first parameter of function descriptor}
    public int descriptorStartParameter() {
        return parameters.length == 0 ? 0 : skipAllocator();
    }

    /// {@return the first parameter of `invokedynamic` in [Downcall][overrun.marshal.Downcall]}
    public int invocationStartParameter() {
        return parameters.length == 0 || byValue ? 0 : skipAllocator();
    }

    private int skipAllocator() {
        if (allocatorRequirement != AllocatorRequirement.NONE &&
            SegmentAllocator.class.isAssignableFrom(parameters[0].type().javaClass())) {
            return 1;
        }
        return 0;
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
        sb.append(returnType).append(' ');
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

    /// The constant desc.
    public static final class Desc extends DynamicConstantDesc<DowncallMethodType> {
        private final ConvertedClassType.Desc returnType;
        private final DowncallMethodParameter[] parameters;
        private final boolean byValue;
        private final boolean critical;
        private final boolean criticalAllowHeapAccess;
        private final long sized;
        private final String charset;
        private final String canonicalType;

        /// constructor
        ///
        /// @param entrypoint              entrypoint
        /// @param returnType              returnType
        /// @param parameters              parameters
        /// @param byValue                 byValue
        /// @param critical                critical
        /// @param criticalAllowHeapAccess criticalAllowHeapAccess
        /// @param sized                   sized
        /// @param charset                 charset
        /// @param canonicalType           canonicalType
        public Desc(
            String entrypoint,
            ConvertedClassType.Desc returnType,
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
