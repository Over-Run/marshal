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

import overrun.marshal.gen.struct.StructRef;
import overrun.marshal.gen1.Spec;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

/**
 * Holds spec with type data
 *
 * @author squid233
 * @since 0.1.0
 */
public interface TypeUse {
    private static String valueLayoutString(TypeKind typeKind) {
        return switch (typeKind) {
            case BOOLEAN -> "JAVA_BOOLEAN";
            case CHAR -> "JAVA_CHAR";
            case BYTE -> "JAVA_BYTE";
            case SHORT -> "JAVA_SHORT";
            case INT -> "JAVA_INT";
            case LONG -> "JAVA_LONG";
            case FLOAT -> "JAVA_FLOAT";
            case DOUBLE -> "JAVA_DOUBLE";
            case ARRAY, DECLARED -> "ADDRESS";
            default -> null;
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
     * @param typeMirror the type mirror
     * @return the value layout
     */
    static Optional<TypeUse> valueLayout(TypeMirror typeMirror) {
        return valueLayout(valueLayoutString(typeMirror.getKind()));
    }

    /**
     * Gets the value layout
     *
     * @param element the element
     * @return the value layout
     */
    static Optional<TypeUse> valueLayout(Element element) {
        return element.getAnnotation(StructRef.class) != null ?
            valueLayout("ADDRESS") :
            valueLayout(element.asType());
    }

    /**
     * Converts to the downcall type
     *
     * @param typeMirror the type mirror
     * @return the downcall type
     */
    static Optional<TypeUse> toDowncallType(TypeMirror typeMirror) {
        final TypeKind typeKind = typeMirror.getKind();
        if (typeKind.isPrimitive()) {
            return Optional.of(_ -> Spec.literal(typeMirror.toString()));
        }
        return switch (typeKind) {
            case ARRAY, DECLARED -> Optional.of(importData -> Spec.literal(importData.simplifyOrImport(MemorySegment.class)));
            default -> Optional.empty();
        };
    }

    /**
     * Applies the imports
     *
     * @param importData the import data
     * @return the spec
     */
    Spec apply(ImportData importData);
}
