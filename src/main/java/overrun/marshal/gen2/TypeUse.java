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

import overrun.marshal.gen.CEnum;
import overrun.marshal.gen.struct.StructRef;
import overrun.marshal.gen1.Spec;
import overrun.marshal.internal.Util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.function.Function;

/**
 * Holds spec with type data
 *
 * @author squid233
 * @since 0.1.0
 */
@FunctionalInterface
public interface TypeUse {
    private static Optional<TypeUse> valueLayout(TypeKind typeKind) {
        return switch (typeKind) {
            case BOOLEAN -> valueLayout(boolean.class);
            case CHAR -> valueLayout(char.class);
            case BYTE -> valueLayout(byte.class);
            case SHORT -> valueLayout(short.class);
            case INT -> valueLayout(int.class);
            case LONG -> valueLayout(long.class);
            case FLOAT -> valueLayout(float.class);
            case DOUBLE -> valueLayout(double.class);
            case ARRAY, DECLARED -> valueLayout(MemorySegment.class);
            default -> Optional.empty();
        };
    }

    private static Optional<TypeUse> valueLayout(String layout) {
        if (layout == null) {
            return Optional.empty();
        }
        return Optional.of(importData ->
            Spec.accessSpec(importData.simplifyOrImport(ValueLayout.class), layout));
    }

    /**
     * Gets the value layout
     *
     * @param env        the processing environment
     * @param typeMirror the type mirror
     * @return the value layout
     */
    static Optional<TypeUse> valueLayout(ProcessingEnvironment env, TypeMirror typeMirror) {
        if (Util.isAExtendsB(env, typeMirror, SegmentAllocator.class)) {
            return Optional.empty();
        }
        if (Util.isAExtendsB(env, typeMirror, CEnum.class)) {
            return valueLayout(int.class);
        }
        return valueLayout(typeMirror.getKind());
    }

    /**
     * Gets the value layout
     *
     * @param env     the processing environment
     * @param element the element
     * @return the value layout
     */
    static Optional<TypeUse> valueLayout(ProcessingEnvironment env, Element element) {
        if (element.getAnnotation(StructRef.class) != null) {
            return valueLayout(MemorySegment.class);
        }
        return valueLayout(env, element.asType());
    }

    /**
     * Gets the value layout
     *
     * @param carrier the carrier
     * @return the value layout
     */
    static Optional<TypeUse> valueLayout(Class<?> carrier) {
        if (carrier == boolean.class) return valueLayout("JAVA_BOOLEAN");
        if (carrier == char.class) return valueLayout("JAVA_CHAR");
        if (carrier == byte.class) return valueLayout("JAVA_BYTE");
        if (carrier == short.class) return valueLayout("JAVA_SHORT");
        if (carrier == int.class) return valueLayout("JAVA_INT");
        if (carrier == long.class) return valueLayout("JAVA_LONG");
        if (carrier == float.class) return valueLayout("JAVA_FLOAT");
        if (carrier == double.class) return valueLayout("JAVA_DOUBLE");
        if (carrier == MemorySegment.class) return valueLayout("ADDRESS");
        return Optional.empty();
    }

    /**
     * Converts to the downcall type
     *
     * @param env        the processing environment
     * @param typeMirror the type mirror
     * @return the downcall type
     */
    static Optional<TypeUse> toDowncallType(ProcessingEnvironment env, TypeMirror typeMirror) {
        final TypeKind typeKind = typeMirror.getKind();
        if (typeKind.isPrimitive()) {
            return Optional.of(literal(typeMirror.toString()));
        }
        return switch (typeKind) {
            case ARRAY, DECLARED -> {
                if (typeKind == TypeKind.DECLARED) {
                    if (Util.isAExtendsB(env, typeMirror, SegmentAllocator.class)) {
                        yield Optional.of(of(env, typeMirror));
                    }
                    if (Util.isAExtendsB(env, typeMirror, CEnum.class)) {
                        yield Optional.of(literal(int.class));
                    }
                }
                yield Optional.of(of(MemorySegment.class));
            }
            default -> Optional.empty();
        };
    }

    /**
     * Converts to the downcall type
     *
     * @param env     the processing environment
     * @param element the element
     * @return the downcall type
     */
    static Optional<TypeUse> toDowncallType(ProcessingEnvironment env, Element element) {
        return toDowncallType(env, element, Element::asType);
    }

    /**
     * Converts to the downcall type
     *
     * @param env      the processing environment
     * @param element  the element
     * @param function the function
     * @param <T>      the element type
     * @return the downcall type
     */
    static <T extends Element> Optional<TypeUse> toDowncallType(ProcessingEnvironment env, T element, Function<T, TypeMirror> function) {
        final StructRef structRef = element.getAnnotation(StructRef.class);
        if (structRef != null) {
            return Optional.of(of(MemorySegment.class));
        }
        return toDowncallType(env, function.apply(element));
    }

    /**
     * {@return literal type use}
     *
     * @param aClass class
     */
    static TypeUse of(Class<?> aClass) {
        return literal(importData -> importData.simplifyOrImport(aClass));
    }

    /**
     * {@return literal type use}
     *
     * @param env        env
     * @param typeMirror typeMirror
     */
    static TypeUse of(ProcessingEnvironment env, TypeMirror typeMirror) {
        return literal(importData -> importData.simplifyOrImport(env, typeMirror));
    }

    /**
     * {@return literal type use}
     *
     * @param s string
     */
    static TypeUse literal(String s) {
        return _ -> Spec.literal(s);
    }

    /**
     * {@return literal type use}
     *
     * @param aClass class
     */
    static TypeUse literal(Class<?> aClass) {
        return literal(aClass.getCanonicalName());
    }

    /**
     * {@return literal type use}
     *
     * @param function function
     */
    static TypeUse literal(Function<ImportData, String> function) {
        return importData -> Spec.literal(function.apply(importData));
    }

    /**
     * Applies the imports
     *
     * @param importData the import data
     * @return the spec
     */
    Spec apply(ImportData importData);
}
