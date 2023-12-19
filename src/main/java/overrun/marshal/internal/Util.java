/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

package overrun.marshal.internal;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Util
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Util {
    private Util() {
        //no instance
    }

    /**
     * capitalize
     *
     * @param str str
     * @return capitalize
     */
    public static String capitalize(String str) {
        final int len = str == null ? 0 : str.length();
        if (len == 0) return str;
        final int codePoint0 = str.codePointAt(0);
        final int titleCase = Character.toTitleCase(codePoint0);
        if (codePoint0 == titleCase) return str;
        if (len > 1) return (char) titleCase + str.substring(1);
        return String.valueOf((char) titleCase);
    }

    /**
     * Invalid type
     *
     * @param typeMirror typeMirror
     * @return invalidType
     */
    public static IllegalStateException invalidType(TypeMirror typeMirror) {
        return new IllegalStateException("Invalid type: " + typeMirror);
    }

    /**
     * Simplify class name
     *
     * @param rawClassName rawClassName
     * @return name
     */
    public static String simplify(String rawClassName) {
        if (isString(rawClassName)) {
            return String.class.getSimpleName();
        }
        if (isMemorySegment(rawClassName)) {
            return MemorySegment.class.getSimpleName();
        }
        return rawClassName;
    }

    /**
     * isDeclared
     *
     * @param typeMirror typeMirror
     * @return isDeclared
     */
    public static boolean isDeclared(TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.DECLARED;
    }

    /**
     * isMemorySegment
     *
     * @param clazzName clazzName
     * @return isMemorySegment
     */
    public static boolean isMemorySegment(String clazzName) {
        return MemorySegment.class.getCanonicalName().equals(clazzName);
    }

    /**
     * isMemorySegment
     *
     * @param typeMirror typeMirror
     * @return isMemorySegment
     */
    public static boolean isMemorySegment(TypeMirror typeMirror) {
        return isDeclared(typeMirror) &&
               isMemorySegment(typeMirror.toString());
    }

    /**
     * isString
     *
     * @param clazzName clazzName
     * @return isString
     */
    public static boolean isString(String clazzName) {
        return String.class.getCanonicalName().equals(clazzName);
    }

    /**
     * isString
     *
     * @param typeMirror typeMirror
     * @return isString
     */
    public static boolean isString(TypeMirror typeMirror) {
        return isDeclared(typeMirror) &&
               isString(typeMirror.toString());
    }

    /**
     * isArray
     *
     * @param typeMirror typeMirror
     * @return isArray
     */
    public static boolean isArray(TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.ARRAY;
    }

    /**
     * isBooleanArray
     *
     * @param typeMirror typeMirror
     * @return isBooleanArray
     */
    public static boolean isBooleanArray(TypeMirror typeMirror) {
        return isArray(typeMirror) &&
               getArrayComponentType(typeMirror).getKind() == TypeKind.BOOLEAN;
    }

    /**
     * isPrimitiveArray
     *
     * @param typeMirror typeMirror
     * @return isPrimitiveArray
     */
    public static boolean isPrimitiveArray(TypeMirror typeMirror) {
        return isArray(typeMirror) &&
               getArrayComponentType(typeMirror).getKind().isPrimitive();
    }

    /**
     * isStringArray
     *
     * @param typeMirror typeMirror
     * @return isStringArray
     */
    public static boolean isStringArray(TypeMirror typeMirror) {
        return isArray(typeMirror) && isString(getArrayComponentType(typeMirror));
    }

    /**
     * getArrayComponentType
     *
     * @param typeMirror typeMirror
     * @return getArrayComponentType
     */
    public static TypeMirror getArrayComponentType(TypeMirror typeMirror) {
        return ((ArrayType) typeMirror).getComponentType();
    }

    /**
     * canConvertToAddress
     *
     * @param typeMirror typeMirror
     * @return canConvertToAddress
     */
    public static boolean canConvertToAddress(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case ARRAY -> isPrimitiveArray(typeMirror) || isBooleanArray(typeMirror) || isStringArray(typeMirror);
            case DECLARED -> isMemorySegment(typeMirror) || isString(typeMirror);
            default -> false;
        };
    }

    /**
     * isValueType
     *
     * @param typeMirror typeMirror
     * @return isValueType
     */
    public static boolean isValueType(TypeMirror typeMirror) {
        if (typeMirror.getKind().isPrimitive()) {
            return true;
        }
        return canConvertToAddress(typeMirror);
    }

    /**
     * toValueLayout
     *
     * @param typeMirror typeMirror
     * @return toValueLayout
     */
    public static ValueLayout toValueLayout(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN -> ValueLayout.JAVA_BOOLEAN;
            case BYTE -> ValueLayout.JAVA_BYTE;
            case SHORT -> ValueLayout.JAVA_SHORT;
            case INT -> ValueLayout.JAVA_INT;
            case LONG -> ValueLayout.JAVA_LONG;
            case CHAR -> ValueLayout.JAVA_CHAR;
            case FLOAT -> ValueLayout.JAVA_FLOAT;
            case DOUBLE -> ValueLayout.JAVA_DOUBLE;
            default -> {
                if (canConvertToAddress(typeMirror)) yield ValueLayout.ADDRESS;
                throw invalidType(typeMirror);
            }
        };
    }

    /**
     * toValueLayoutStr
     *
     * @param typeMirror typeMirror
     * @return toValueLayoutStr
     */
    public static String toValueLayoutStr(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN -> "ValueLayout.JAVA_BOOLEAN";
            case BYTE -> "ValueLayout.JAVA_BYTE";
            case SHORT -> "ValueLayout.JAVA_SHORT";
            case INT -> "ValueLayout.JAVA_INT";
            case LONG -> "ValueLayout.JAVA_LONG";
            case CHAR -> "ValueLayout.JAVA_CHAR";
            case FLOAT -> "ValueLayout.JAVA_FLOAT";
            case DOUBLE -> "ValueLayout.JAVA_DOUBLE";
            default -> {
                if (canConvertToAddress(typeMirror)) yield "ValueLayout.ADDRESS";
                throw invalidType(typeMirror);
            }
        };
    }
}
