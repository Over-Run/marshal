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

import overrun.marshal.CanonicalLayouts;
import overrun.marshal.gen.CanonicalType;
import overrun.marshal.gen.DowncallMethodType;
import overrun.marshal.struct.ByValue;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms the method and parameters into a function descriptor.
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DescriptorTransformer extends TypeTransformer<DescriptorTransformer.Context, FunctionDescriptor> {
    private DescriptorTransformer() {
    }

    private MemoryLayout findCanonicalLayout(String typename) {
        MemoryLayout layout = CanonicalLayouts.get(typename);
        if (layout == null) {
            throw new IllegalArgumentException("Canonical layout not found for type name: " + typename);
        }
        return layout;
    }

    /**
     * The context.
     *
     * @param method                       the method
     * @param descriptorSkipFirstParameter {@code true} if the function descriptor should skip the first parameter
     * @param parameters                   the parameters
     * @param methodType                   the downcall method type
     */
    public record Context(
        Method method,
        boolean descriptorSkipFirstParameter,
        List<Parameter> parameters,
        DowncallMethodType methodType
    ) {
    }

    @Override
    public FunctionDescriptor process(Context context) {
        DowncallMethodType methodType = context.methodType();
        List<MemoryLayout> argLayouts = new ArrayList<>();

        MemoryLayout returnLayout;
        if (methodType.canonicalType() != null) {
            returnLayout = findCanonicalLayout(methodType.canonicalType());
        } else {
            ProcessorType returnType = ProcessorTypes.fromClass(methodType.returnType());
            returnLayout = switch (returnType) {
                case ProcessorType.Allocator allocator -> allocator.downcallLayout();
                case ProcessorType.Array array -> methodType.sized() >= 0 ?
                    ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(methodType.sized(), array.componentType().downcallLayout())) :
                    array.downcallLayout();
                case ProcessorType.BoolConvert boolConvert -> throw new UnsupportedOperationException();//todo
                case ProcessorType.Custom custom -> custom.downcallLayout();
                case ProcessorType.Str str -> str.downcallLayout();
                case ProcessorType.Struct struct -> {
                    if (methodType.byValue()) {
                        yield struct.downcallLayout();
                    }
                    if (methodType.sized() >= 0) {
                        yield ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(methodType.sized(), struct.downcallLayout()));
                    }
                    yield ValueLayout.ADDRESS.withTargetLayout(struct.downcallLayout());
                }
                case ProcessorType.Upcall<?> upcall -> upcall.downcallLayout();
                case ProcessorType.Value value -> {
                    if (value == ProcessorType.Value.ADDRESS) {
                        if (methodType.sized() >= 0) {
                            yield ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(methodType.sized(), ValueLayout.JAVA_BYTE));
                        }
                    }
                    yield value.downcallLayout();
                }
                case ProcessorType.Void _ -> null;
            };
        }

        var parameters = context.parameters();
        for (int i = context.descriptorSkipFirstParameter() ? 1 : 0, size = parameters.size(); i < size; i++) {
            Parameter parameter = parameters.get(i);
            CanonicalType parameterCType = parameter.getDeclaredAnnotation(CanonicalType.class);
            MemoryLayout layout;
            if (parameterCType != null) {
                layout = findCanonicalLayout(parameterCType.value());
            } else {
                ProcessorType type = ProcessorTypes.fromParameter(parameter);
                layout = switch (type) {
                    case ProcessorType.Struct struct -> parameter.getDeclaredAnnotation(ByValue.class) != null ?
                        struct.downcallLayout() :
                        ValueLayout.ADDRESS;
                    default -> type.downcallLayout();
                };
            }
            argLayouts.add(layout);
        }

        return returnLayout == null ?
            FunctionDescriptor.ofVoid(argLayouts.toArray(new MemoryLayout[0])) :
            FunctionDescriptor.of(returnLayout, argLayouts.toArray(new MemoryLayout[0]));
    }

    /**
     * {@return the instance}
     */
    public static DescriptorTransformer getInstance() {
        class Holder {
            static final DescriptorTransformer INSTANCE = new DescriptorTransformer();
        }
        return Holder.INSTANCE;
    }
}
