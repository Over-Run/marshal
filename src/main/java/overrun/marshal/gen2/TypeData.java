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

package overrun.marshal.gen2;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Holds type name
 *
 * @author squid233
 * @since 0.1.0
 */
public sealed interface TypeData permits ArrayTypeData, DeclaredTypeData, PrimitiveTypeData {
    /**
     * Detects type
     *
     * @param env  the processing environment
     * @param type the type
     * @return the type data
     */
    static TypeData detectType(ProcessingEnvironment env, TypeMirror type) {
        final TypeKind typeKind = type.getKind();
        if (typeKind.isPrimitive()) {
            return new PrimitiveTypeData(type.toString());
        }
        if (typeKind == TypeKind.ARRAY &&
            type instanceof ArrayType arrayType) {
            return new ArrayTypeData(detectType(env, arrayType.getComponentType()));
        }
        if (typeKind == TypeKind.DECLARED &&
            env.getTypeUtils().asElement(type) instanceof TypeElement typeElement) {
            final String qualifiedName = typeElement.getQualifiedName().toString();
            final String simpleName = typeElement.getSimpleName().toString();
            return new DeclaredTypeData(qualifiedName.substring(0, qualifiedName.lastIndexOf(simpleName) - 1), simpleName);
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    /**
     * Create type data from class
     *
     * @param aClass the class
     * @return the type data
     */
    static TypeData fromClass(Class<?> aClass) {
        if (aClass.isPrimitive()) {
            return new PrimitiveTypeData(aClass.getCanonicalName());
        }
        if (aClass.isArray()) {
            return new ArrayTypeData(fromClass(aClass.arrayType()));
        }
        return new DeclaredTypeData(aClass.getPackageName(), aClass.getSimpleName());
    }

    @Override
    String toString();
}
