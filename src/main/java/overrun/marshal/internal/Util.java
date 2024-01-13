/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
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

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.lang.annotation.Annotation;
import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.function.Predicate;

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
     * try insert prefix
     *
     * @param prefix    the prefix
     * @param name      the name
     * @param predicate the predicate
     * @return the string
     */
    public static String tryInsertPrefix(String prefix, String name, Predicate<String> predicate) {
        if (predicate.test(name)) {
            return tryInsertPrefix(prefix, prefix + name, predicate);
        }
        return name;
    }

    /**
     * try insert underline
     *
     * @param name      the name
     * @param predicate the predicate
     * @return the string
     */
    public static String tryInsertUnderline(String name, Predicate<String> predicate) {
        return tryInsertPrefix("_", name, predicate);
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
    @Deprecated(since = "0.1.0")
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
    @Deprecated(since = "0.1.0")
    public static boolean isMemorySegment(String clazzName) {
        return MemorySegment.class.getCanonicalName().equals(clazzName);
    }

    /**
     * isMemorySegment
     *
     * @param typeMirror typeMirror
     * @return isMemorySegment
     */
    @Deprecated(since = "0.1.0")
    public static boolean isMemorySegment(TypeMirror typeMirror) {
        return isSameClass(typeMirror, MemorySegment.class);
    }

    /**
     * isString
     *
     * @param clazzName clazzName
     * @return isString
     */
    @Deprecated(since = "0.1.0")
    public static boolean isString(String clazzName) {
        return String.class.getCanonicalName().equals(clazzName);
    }

    /**
     * isString
     *
     * @param typeMirror typeMirror
     * @return isString
     */
    @Deprecated(since = "0.1.0")
    public static boolean isString(TypeMirror typeMirror) {
        return isSameClass(typeMirror, String.class);
    }

    /**
     * isArray
     *
     * @param typeMirror typeMirror
     * @return isArray
     */
    @Deprecated(since = "0.1.0")
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
     * insertUnderline
     *
     * @param builtinName builtinName
     * @param nameString  nameString
     * @return insertUnderline
     */
    @Deprecated(since = "0.1.0")
    public static String insertUnderline(String builtinName, String nameString) {
        return builtinName.equals(nameString) ? "_" + builtinName : builtinName;
    }

    /**
     * Finds annotated method
     *
     * @param typeElement the type element
     * @param aClass      the class
     * @param predicate   the filter
     * @return the method
     */
    public static Optional<ExecutableElement> findAnnotatedMethod(
        TypeElement typeElement,
        Class<? extends Annotation> aClass,
        Predicate<ExecutableElement> predicate
    ) {
        return ElementFilter.methodsIn(typeElement.getEnclosedElements())
            .stream()
            .filter(executableElement -> executableElement.getAnnotation(aClass) != null)
            .filter(predicate)
            .findFirst();
    }

    /**
     * Gets the type element from class name
     *
     * @param env    the processing environment
     * @param aClass the class
     * @return the type element
     */
    public static TypeElement getTypeElementFromClass(ProcessingEnvironment env, String aClass) {
        return env.getElementUtils().getTypeElement(aClass);
    }

    /**
     * Gets the type element from class
     *
     * @param env    the processing environment
     * @param aClass the class
     * @return the type element
     */
    public static TypeElement getTypeElementFromClass(ProcessingEnvironment env, Class<?> aClass) {
        return getTypeElementFromClass(env, aClass.getCanonicalName());
    }

    /**
     * {@return is A extends B}
     *
     * @param env the processing environment
     * @param t1  A
     * @param t2  B
     */
    public static boolean isAExtendsB(ProcessingEnvironment env, TypeMirror t1, TypeMirror t2) {
        return isDeclared(t1) &&
               isDeclared(t2) &&
               env.getTypeUtils().isAssignable(t1, t2);
    }

    /**
     * {@return is A extends B}
     *
     * @param env the processing environment
     * @param t1  A
     * @param t2  B
     */
    public static boolean isAExtendsB(ProcessingEnvironment env, TypeMirror t1, Class<?> t2) {
        return isAExtendsB(env, t1, getTypeElementFromClass(env, t2).asType());
    }

    /**
     * {@return is same class}
     *
     * @param typeMirror the type mirror
     * @param aClass     the class
     */
    public static boolean isSameClass(TypeMirror typeMirror, Class<?> aClass) {
        return ((typeMirror.getKind().isPrimitive() && aClass.isPrimitive()) ||
                (typeMirror.getKind() == TypeKind.ARRAY && aClass.isArray()) ||
                isDeclared(typeMirror)) &&
               aClass.getCanonicalName().equals(typeMirror.toString());
    }
}
