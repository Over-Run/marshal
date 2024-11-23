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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * @author squid233
 * @since 0.1.0
 */
public record ConvertedClassType(
    Class<?> javaClass,
    Class<?> downcallClass
) implements Constable {
    public static ConvertedClassType returnType(Method method) {
        return of(method, method.getReturnType());
    }

    public static ConvertedClassType parameterType(Parameter parameter) {
        return of(parameter, parameter.getType());
    }

    private static ConvertedClassType of(AnnotatedElement element, Class<?> type) {
        Convert convert = element.getAnnotation(Convert.class);
        if (type == boolean.class && convert != null) {
            return new ConvertedClassType(type, convert.value().javaClass());
        }
        return new ConvertedClassType(type, type);
    }

    @Override
    public Optional<Desc> describeConstable() {
        return Optional.of(new Desc(ClassDesc.ofDescriptor(javaClass.descriptorString()), ClassDesc.ofDescriptor(downcallClass.descriptorString())));
    }

    @Override
    public String toString() {
        if (javaClass == downcallClass) {
            return javaClass.getSimpleName();
        }
        return downcallClass.getSimpleName() + "<from " + javaClass.getSimpleName() + ">";
    }

    public static final class Desc extends DynamicConstantDesc<ConvertedClassType> {
        private final ClassDesc javaClass;
        private final ClassDesc downcallClass;

        public Desc(ClassDesc javaClass, ClassDesc downcallClass) {
            super(Constants.BSM_DowncallFactory_createExtendedClassType, ConstantDescs.DEFAULT_NAME, Constants.CD_ConvertedClassType, javaClass, downcallClass);
            this.javaClass = javaClass;
            this.downcallClass = downcallClass;
        }

        @Override
        public ConvertedClassType resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            return new ConvertedClassType(javaClass.resolveConstantDesc(lookup), downcallClass.resolveConstantDesc(lookup));
        }
    }
}
