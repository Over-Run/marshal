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

package overrun.marshal;

import overrun.marshal.gen.*;
import overrun.marshal.gen.Type;
import overrun.marshal.struct.ByValue;
import overrun.marshal.struct.Struct;

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

/**
 * Downcall library loader.
 * <h2>Loading native library</h2>
 * You can load native libraries with {@link #load(Class, SymbolLookup)}.
 * This method generates a hidden class that loads method handle with the given symbol lookup.
 * <h2>Methods</h2>
 * The loader finds method from the target class and its superclasses.
 * <p>
 * The loader skips static methods and methods annotated with {@link Skip @Skip} while generating.
 * <p>
 * {@link Entrypoint @Entrypoint} specifies the entrypoint of the annotated method.
 * <p>
 * THe loader will not throw an exception
 * if the method is modified with {@code default} and the native function is not found;
 * instead, it will return {@code null} and automatically invokes the original method declared in the target class.
 * <p>
 * {@link Critical @Critical} indicates that the annotated method is {@linkplain Linker.Option#critical(boolean) critical}.
 * <h3>Parameter Annotations</h3>
 * See {@link Ref @Ref}, {@link Sized @Sized} {@link SizedSeg @SizedSeg} and {@link StrCharset @StrCharset}.
 * <h2>Example</h2>
 * <pre>{@code
 * public interface GL {
 *     GL gl = Downcall.load("libGL.so");
 *     int COLOR_BUFFER_BIT = 0x00004000;
 *     void glClear(int mask);
 * }
 * }</pre>
 *
 * @author squid233
 * @see Critical
 * @see Entrypoint
 * @see Ref
 * @see Sized
 * @see SizedSeg
 * @see Skip
 * @see StrCharset
 * @since 0.1.0
 */
public final class Downcall {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ClassDesc CD_Addressable = ClassDesc.of("overrun.marshal.Addressable");
    private static final ClassDesc CD_AddressLayout = ClassDesc.of("java.lang.foreign.AddressLayout");
    private static final ClassDesc CD_Arena = ClassDesc.of("java.lang.foreign.Arena");
    private static final ClassDesc CD_CEnum = ClassDesc.of("overrun.marshal.CEnum");
    private static final ClassDesc CD_Charset = ClassDesc.of("java.nio.charset.Charset");
    private static final ClassDesc CD_Checks = ClassDesc.of("overrun.marshal.Checks");
    private static final ClassDesc CD_FunctionDescriptor = ClassDesc.of("java.lang.foreign.FunctionDescriptor");
    private static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");
    private static final ClassDesc CD_Linker = ClassDesc.of("java.lang.foreign.Linker");
    private static final ClassDesc CD_Marshal = ClassDesc.of("overrun.marshal.Marshal");
    private static final ClassDesc CD_MemoryLayout = ClassDesc.of("java.lang.foreign.MemoryLayout");
    private static final ClassDesc CD_MemorySegment = ClassDesc.of("java.lang.foreign.MemorySegment");
    private static final ClassDesc CD_Linker_Option = CD_Linker.nested("Option");
    private static final ClassDesc CD_MemoryStack = ClassDesc.of("overrun.marshal.MemoryStack");
    private static final ClassDesc CD_Optional = ClassDesc.of("java.util.Optional");
    private static final ClassDesc CD_SegmentAllocator = ClassDesc.of("java.lang.foreign.SegmentAllocator");
    private static final ClassDesc CD_SequenceLayout = ClassDesc.of("java.lang.foreign.SequenceLayout");
    private static final ClassDesc CD_StandardCharsets = ClassDesc.of("java.nio.charset.StandardCharsets");
    private static final ClassDesc CD_StructLayout = ClassDesc.of("java.lang.foreign.StructLayout");
    private static final ClassDesc CD_SymbolLookup = ClassDesc.of("java.lang.foreign.SymbolLookup");
    private static final ClassDesc CD_Unmarshal = ClassDesc.of("overrun.marshal.Unmarshal");
    private static final ClassDesc CD_Upcall = ClassDesc.of("overrun.marshal.Upcall");
    private static final ClassDesc CD_ValueLayout = ClassDesc.of("java.lang.foreign.ValueLayout");
    private static final ClassDesc CD_ValueLayout_OfBoolean = CD_ValueLayout.nested("OfBoolean");
    private static final ClassDesc CD_ValueLayout_OfChar = CD_ValueLayout.nested("OfChar");
    private static final ClassDesc CD_ValueLayout_OfByte = CD_ValueLayout.nested("OfByte");
    private static final ClassDesc CD_ValueLayout_OfShort = CD_ValueLayout.nested("OfShort");
    private static final ClassDesc CD_ValueLayout_OfInt = CD_ValueLayout.nested("OfInt");
    private static final ClassDesc CD_ValueLayout_OfLong = CD_ValueLayout.nested("OfLong");
    private static final ClassDesc CD_ValueLayout_OfFloat = CD_ValueLayout.nested("OfFloat");
    private static final ClassDesc CD_ValueLayout_OfDouble = CD_ValueLayout.nested("OfDouble");
    private static final ClassDesc CD_LinkerOptionArray = CD_Linker_Option.arrayType();
    private static final ClassDesc CD_MemoryLayoutArray = CD_MemoryLayout.arrayType();
    private static final ClassDesc CD_StringArray = CD_String.arrayType();

    private static final MethodTypeDesc MTD_AddressLayout_MemoryLayout = MethodTypeDesc.of(CD_AddressLayout, CD_MemoryLayout);
    private static final MethodTypeDesc MTD_boolean = MethodTypeDesc.of(CD_boolean);
    private static final MethodTypeDesc MTD_Charset_String = MethodTypeDesc.of(CD_Charset, CD_String);
    private static final MethodTypeDesc MTD_FunctionDescriptor_MemoryLayout_MemoryLayoutArray = MethodTypeDesc.of(CD_FunctionDescriptor, CD_MemoryLayout, CD_MemoryLayoutArray);
    private static final MethodTypeDesc MTD_FunctionDescriptor_MemoryLayoutArray = MethodTypeDesc.of(CD_FunctionDescriptor, CD_MemoryLayoutArray);
    private static final MethodTypeDesc MTD_Linker = MethodTypeDesc.of(CD_Linker);
    private static final MethodTypeDesc MTD_LinkerOption_boolean = MethodTypeDesc.of(CD_Linker_Option, CD_boolean);
    private static final MethodTypeDesc MTD_long = MethodTypeDesc.of(CD_long);
    private static final MethodTypeDesc MTD_MemorySegment_Arena_Upcall = MethodTypeDesc.of(CD_MemorySegment, CD_Arena, CD_Upcall);
    private static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_String = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String);
    private static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_String_Charset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String,
        CD_Charset);
    private static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_StringArray_Charset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_StringArray,
        CD_Charset);
    private static final MethodTypeDesc MTD_MemoryStack = MethodTypeDesc.of(CD_MemoryStack);
    private static final MethodTypeDesc MTD_MethodHandle_MemorySegment_FunctionDescriptor_LinkerOptionArray = MethodTypeDesc.of(CD_MethodHandle,
        CD_MemorySegment,
        CD_FunctionDescriptor,
        CD_LinkerOptionArray);
    private static final MethodTypeDesc MTD_MethodHandle_SymbolLookup = MethodTypeDesc.of(CD_MethodHandle, CD_SymbolLookup);
    private static final MethodTypeDesc MTD_Object_Object = MethodTypeDesc.of(CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    private static final MethodTypeDesc MTD_Optional_String = MethodTypeDesc.of(CD_Optional, CD_String);
    private static final MethodTypeDesc MTD_SequenceLayout_long_MemoryLayout = MethodTypeDesc.of(CD_SequenceLayout, CD_long, CD_MemoryLayout);
    private static final MethodTypeDesc MTD_String_MemorySegment = MethodTypeDesc.of(CD_String, CD_MemorySegment);
    private static final MethodTypeDesc MTD_String_MemorySegment_Charset = MethodTypeDesc.of(CD_String, CD_MemorySegment, CD_Charset);
    private static final MethodTypeDesc MTD_String_Object = MethodTypeDesc.of(CD_String, CD_Object);
    private static final MethodTypeDesc MTD_String_String = MethodTypeDesc.of(CD_String, CD_String);
    private static final MethodTypeDesc MTD_StringArray_MemorySegment = MethodTypeDesc.of(CD_StringArray, CD_MemorySegment);
    private static final MethodTypeDesc MTD_StringArray_MemorySegment_Charset = MethodTypeDesc.of(CD_StringArray, CD_MemorySegment, CD_Charset);
    private static final MethodTypeDesc MTD_void_int_int = MethodTypeDesc.of(CD_void, CD_int, CD_int);
    private static final MethodTypeDesc MTD_void_long = MethodTypeDesc.of(CD_void, CD_long);
    private static final MethodTypeDesc MTD_void_MemorySegment = MethodTypeDesc.of(CD_void, CD_MemorySegment);
    private static final MethodTypeDesc MTD_void_MemorySegment_StringArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_StringArray);
    private static final MethodTypeDesc MTD_void_MemorySegment_StringArray_Charset = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_StringArray, CD_Charset);
    private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc MTD_void_String_Throwable = MethodTypeDesc.of(CD_void, CD_String, CD_Throwable);
    private static final MethodTypeDesc MTD_void_SymbolLookup = MethodTypeDesc.of(CD_void, CD_SymbolLookup);

    private Downcall() {
    }

    /**
     * Loads the given class with the given symbol lookup.
     *
     * @param targetClass   the target class
     * @param lookup        the symbol lookup
     * @param descriptorMap the custom function descriptors for each method handle
     * @param <T>           the type of the target class
     * @return the loaded implementation instance of the target class
     */
    public static <T> T load(Class<T> targetClass, SymbolLookup lookup, Map<String, FunctionDescriptor> descriptorMap) {
        return loadBytecode(targetClass, lookup, descriptorMap);
    }

    /**
     * Loads the given class with the given symbol lookup.
     *
     * @param targetClass the target class
     * @param lookup      the symbol lookup
     * @param <T>         the type of the target class
     * @return the loaded implementation instance of the target class
     */
    public static <T> T load(Class<T> targetClass, SymbolLookup lookup) {
        return load(targetClass, lookup, Map.of());
    }

    /**
     * Loads the caller class with the given symbol lookup.
     *
     * @param lookup        the symbol lookup
     * @param descriptorMap the custom function descriptors for each method handle
     * @param <T>           the type of the caller class
     * @return the loaded implementation instance of the caller class
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(SymbolLookup lookup, Map<String, FunctionDescriptor> descriptorMap) {
        return load((Class<T>) STACK_WALKER.getCallerClass(), lookup, descriptorMap);
    }

    /**
     * Loads the caller class with the given symbol lookup.
     *
     * @param lookup the symbol lookup
     * @param <T>    the type of the caller class
     * @return the loaded implementation instance of the caller class
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(SymbolLookup lookup) {
        return load((Class<T>) STACK_WALKER.getCallerClass(), lookup);
    }

    /**
     * Loads the caller class with the given library name.
     *
     * @param libname the library name
     * @param <T>     the type of the caller class
     * @return the loaded implementation instance of the caller class
     */
    @SuppressWarnings("unchecked")
    public static <T> T load(String libname) {
        return load((Class<T>) STACK_WALKER.getCallerClass(), SymbolLookup.libraryLookup(libname, Arena.ofAuto()));
    }

    private static void convertToValueLayout(CodeBuilder codeBuilder, Class<?> aClass) {
        if (aClass == boolean.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_BOOLEAN", CD_ValueLayout_OfBoolean);
        else if (aClass == char.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_CHAR", CD_ValueLayout_OfChar);
        else if (aClass == byte.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_BYTE", CD_ValueLayout_OfByte);
        else if (aClass == short.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_SHORT", CD_ValueLayout_OfShort);
        else if (aClass == int.class || CEnum.class.isAssignableFrom(aClass))
            codeBuilder.getstatic(CD_ValueLayout, "JAVA_INT", CD_ValueLayout_OfInt);
        else if (aClass == long.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_LONG", CD_ValueLayout_OfLong);
        else if (aClass == float.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_FLOAT", CD_ValueLayout_OfFloat);
        else if (aClass == double.class) codeBuilder.getstatic(CD_ValueLayout, "JAVA_DOUBLE", CD_ValueLayout_OfDouble);
        else codeBuilder.getstatic(CD_ValueLayout, "ADDRESS", CD_AddressLayout);
    }

    private static void invokeSuperMethod(CodeBuilder codeBuilder, List<Parameter> parameters) {
        codeBuilder.aload(codeBuilder.receiverSlot());
        for (int i = 0, size = parameters.size(); i < size; i++) {
            final var parameter = parameters.get(i);
            codeBuilder.loadInstruction(
                TypeKind.fromDescriptor(parameter.getType().descriptorString())
                    .asLoadable(),
                codeBuilder.parameterSlot(i));
        }
    }

    private static boolean hasCharset(StrCharset strCharset) {
        return strCharset != null && !strCharset.value().isBlank();
    }

    private static String getCharset(AnnotatedElement element) {
        final StrCharset strCharset = element.getDeclaredAnnotation(StrCharset.class);
        return hasCharset(strCharset) ? strCharset.value() : null;
    }

    private static void getCharset(CodeBuilder codeBuilder, String charset) {
        final String upperCase = charset.toUpperCase(Locale.ROOT);
        switch (upperCase) {
            case "UTF-8", "ISO-8859-1", "US-ASCII",
                "UTF-16", "UTF-16BE", "UTF-16LE",
                "UTF-32", "UTF-32BE", "UTF-32LE" ->
                codeBuilder.getstatic(CD_StandardCharsets, upperCase.replace('-', '_'), CD_Charset);
            case "UTF_8", "ISO_8859_1", "US_ASCII",
                "UTF_16", "UTF_16BE", "UTF_16LE",
                "UTF_32", "UTF_32BE", "UTF_32LE" -> codeBuilder.getstatic(CD_StandardCharsets, upperCase, CD_Charset);
            default -> codeBuilder.ldc(charset)
                .invokestatic(CD_Charset, "forName", MTD_Charset_String);
        }
    }

    private static boolean getCharset(CodeBuilder codeBuilder, AnnotatedElement element) {
        final String charset = getCharset(element);
        if (charset != null) {
            getCharset(codeBuilder, charset);
            return true;
        }
        return false;
    }

    private static String unmarshalMethod(Class<?> aClass) {
        if (aClass.isArray()) {
            final Class<?> componentType = aClass.getComponentType();
            if (componentType == boolean.class) return "unmarshalAsBooleanArray";
            if (componentType == char.class) return "unmarshalAsCharArray";
            if (componentType == byte.class) return "unmarshalAsByteArray";
            if (componentType == short.class) return "unmarshalAsShortArray";
            if (componentType == int.class) return "unmarshalAsIntArray";
            if (componentType == long.class) return "unmarshalAsLongArray";
            if (componentType == float.class) return "unmarshalAsFloatArray";
            if (componentType == double.class) return "unmarshalAsDoubleArray";
            if (componentType == MemorySegment.class) return "unmarshalAsAddressArray";
            if (componentType == String.class) return "unmarshalAsStringArray";
        }
        return "unmarshal";
    }

    private static String marshalFromBooleanMethod(Type type) {
        return switch (type) {
            case CHAR -> "marshalAsChar";
            case BYTE -> "marshalAsByte";
            case SHORT -> "marshalAsShort";
            case INT -> "marshalAsInt";
            case LONG -> "marshalAsLong";
            case FLOAT -> "marshalAsFloat";
            case DOUBLE -> "marshalAsDouble";
        };
    }

    private static String getMethodEntrypoint(Method method) {
        final Entrypoint entrypoint = method.getDeclaredAnnotation(Entrypoint.class);
        return (entrypoint == null || entrypoint.value().isBlank()) ?
            method.getName() :
            entrypoint.value();
    }

    private static ClassDesc convertToDowncallCD(AnnotatedElement element, Class<?> aClass) {
        final Convert convert = element.getDeclaredAnnotation(Convert.class);
        if (convert != null && aClass == boolean.class) return convert.value().classDesc();
        if (aClass.isPrimitive()) return aClass.describeConstable().orElseThrow();
        if (CEnum.class.isAssignableFrom(aClass)) return CD_int;
        if (SegmentAllocator.class.isAssignableFrom(aClass)) return CD_SegmentAllocator;
        return CD_MemorySegment;
    }

    private static ClassDesc convertToMarshalCD(Class<?> aClass) {
        if (aClass.isPrimitive() ||
            aClass == String.class) return aClass.describeConstable().orElseThrow();
        if (Addressable.class.isAssignableFrom(aClass)) return CD_Addressable;
        if (CEnum.class.isAssignableFrom(aClass)) return CD_CEnum;
        if (Upcall.class.isAssignableFrom(aClass)) return CD_Upcall;
        return CD_MemorySegment;
    }

    private static boolean requireAllocator(Class<?> aClass) {
        return !aClass.isPrimitive() &&
               (aClass == String.class ||
                aClass.isArray() ||
                Upcall.class.isAssignableFrom(aClass));
    }

    private static Method findWrapper(
        Class<?> aClass,
        Class<? extends Annotation> annotation,
        Predicate<Method> predicate) {
        return Arrays.stream(aClass.getDeclaredMethods())
            .filter(method -> method.getDeclaredAnnotation(annotation) != null)
            .filter(predicate)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(STR.
                "Couldn't find wrapper method in \{aClass}; mark it with @\{annotation.getSimpleName()}"));
    }

    private static Method findCEnumWrapper(Class<?> aClass) {
        return findWrapper(aClass, CEnum.Wrapper.class, method -> {
            final var types = method.getParameterTypes();
            return types.length == 1 &&
                   types[0] == int.class &&
                   CEnum.class.isAssignableFrom(method.getReturnType());
        });
    }

    private static Method findUpcallWrapper(Class<?> aClass) {
        return findWrapper(aClass, Upcall.Wrapper.class, method -> {
            final var types = method.getParameterTypes();
            return types.length == 2 &&
                   Arena.class.isAssignableFrom(types[0]) &&
                   types[1] == MemorySegment.class &&
                   Upcall.class.isAssignableFrom(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadBytecode(Class<?> callerClass, SymbolLookup lookup, Map<String, FunctionDescriptor> descriptorMap) {
        final ClassFile cf = of();
        final ClassDesc cd_thisClass = ClassDesc.of(callerClass.getPackageName(), DEFAULT_NAME);
        final byte[] bytes = cf.build(cd_thisClass, classBuilder -> {
            final List<Method> methodList = Arrays.stream(callerClass.getMethods())
                .filter(method ->
                    method.getDeclaredAnnotation(Skip.class) == null &&
                    !Modifier.isStatic(method.getModifiers()))
                .toList();

            // check method parameter
            methodList.forEach(method -> {
                final Class<?>[] types = method.getParameterTypes();
                final boolean isFirstArena = types.length > 0 && Arena.class.isAssignableFrom(types[0]);
                if (Upcall.class.isAssignableFrom(method.getReturnType()) && !isFirstArena) {
                    throw new IllegalStateException(STR."The first parameter of method \{method} is not an arena; however, the return type is an upcall");
                }
                for (Parameter parameter : method.getParameters()) {
                    if (Upcall.class.isAssignableFrom(parameter.getType()) && !isFirstArena) {
                        throw new IllegalStateException(STR."The first parameter of method \{method} is not an arena; however, the parameter \{parameter.toString()} is an upcall");
                    }
                }
            });

            final Map<Method, DowncallMethodData> methodDataMap = LinkedHashMap.newLinkedHashMap(methodList.size());
            final Map<Method, DowncallMethodData> handleDataMap = LinkedHashMap.newLinkedHashMap(methodList.size());
            final List<String> addedHandleList = new ArrayList<>(methodList.size());

            classBuilder.withFlags(ACC_FINAL | ACC_SUPER);

            // interface
            classBuilder.withInterfaceSymbols(callerClass.describeConstable().orElseThrow());

            // linker
            classBuilder.withField("LINKER", CD_Linker, ACC_PRIVATE | ACC_FINAL | ACC_STATIC);

            // method handles
            methodList.forEach(method -> {
                final String methodName = method.getName();
                final String handleName = STR."mh_\{methodName}";
                final var parameters = List.of(method.getParameters());

                final DowncallMethodData methodData = new DowncallMethodData(
                    getMethodEntrypoint(method),
                    handleName,
                    STR."$load$\{method.getName()}",
                    STR."""
                        \{method.getReturnType().getCanonicalName()} \
                        \{method.getDeclaringClass().getCanonicalName()}.\{method.getName()}\
                        \{Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                        .collect(Collectors.joining(", ", "(", ")"))}""",
                    parameters,
                    method.getDeclaredAnnotation(ByValue.class) == null &&
                    !parameters.isEmpty() &&
                    SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType())
                );
                methodDataMap.put(method, methodData);

                if (!addedHandleList.contains(handleName)) {
                    handleDataMap.put(method, methodData);
                    addedHandleList.add(handleName);
                    classBuilder.withField(handleName, CD_MethodHandle,
                        ACC_PRIVATE | ACC_FINAL);
                }
            });

            // constructor
            classBuilder.withMethod(INIT_NAME, MTD_void_SymbolLookup, ACC_PUBLIC,
                methodBuilder ->
                    methodBuilder.withCode(codeBuilder -> {
                        // super
                        codeBuilder.aload(codeBuilder.receiverSlot())
                            .invokespecial(CD_Object, INIT_NAME, MTD_void);

                        // method handles
                        handleDataMap.values().forEach(methodData -> {
                            // initialize field
                            codeBuilder.aload(codeBuilder.receiverSlot())
                                .aload(codeBuilder.parameterSlot(0))
                                .invokestatic(cd_thisClass, methodData.loaderName(), MTD_MethodHandle_SymbolLookup)
                                .putfield(cd_thisClass, methodData.handleName(), CD_MethodHandle);
                        });

                        codeBuilder.return_();
                    }));

            // methods
            methodDataMap.forEach((method, methodData) -> {
                final var returnType = method.getReturnType();

                // check method return type
                if (Struct.class.isAssignableFrom(returnType) && Arrays.stream(returnType.getDeclaredConstructors())
                    .noneMatch(constructor -> {
                        final Class<?>[] types = constructor.getParameterTypes();
                        return types.length == 1 && types[0] == MemorySegment.class;
                    })) {
                    throw new IllegalStateException(STR."The struct \{returnType} must contain a constructor that only accept one memory segment: \{methodData.exceptionString()}");
                }

                final String methodName = method.getName();
                final int modifiers = method.getModifiers();
                final ClassDesc cd_returnType = ClassDesc.ofDescriptor(returnType.descriptorString());
                final TypeKind returnTypeKind = TypeKind.from(cd_returnType).asLoadable();
                final List<Parameter> parameters = methodData.parameters();
                final MethodTypeDesc mtd_method = MethodTypeDesc.of(cd_returnType,
                    parameters.stream()
                        .map(parameter -> ClassDesc.ofDescriptor(parameter.getType().descriptorString()))
                        .toList());

                final String handleName = methodData.handleName();
                final Consumer<CodeBuilder> wrapping = returnType == MethodHandle.class ? null : codeBuilder -> {
                    final boolean hasAllocator =
                        !parameters.isEmpty() &&
                        SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType());
                    final boolean shouldAddStack =
                        !parameters.isEmpty() &&
                        !SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType()) &&
                        parameters.stream().anyMatch(parameter -> requireAllocator(parameter.getType()));
                    final int stackSlot;
                    final int stackPointerSlot;
                    final int allocatorSlot;

                    if (shouldAddStack) {
                        stackSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                        stackPointerSlot = codeBuilder.allocateLocal(TypeKind.LongType);
                        allocatorSlot = stackSlot;
                    } else {
                        stackSlot = -1;
                        stackPointerSlot = -1;
                        allocatorSlot = hasAllocator ? codeBuilder.parameterSlot(0) : -1;
                    }

                    final Map<Parameter, Integer> parameterRefSlot = HashMap.newHashMap(Math.toIntExact(parameters.stream()
                        .filter(parameter -> parameter.getDeclaredAnnotation(Ref.class) != null)
                        .count()));

                    // check size
                    for (int i = 0, size = parameters.size(); i < size; i++) {
                        final Parameter parameter = parameters.get(i);
                        final Sized sized = parameter.getDeclaredAnnotation(Sized.class);
                        final Class<?> type = parameter.getType();
                        if (sized == null || !type.isArray()) {
                            continue;
                        }
                        codeBuilder.ldc(sized.value())
                            .aload(codeBuilder.parameterSlot(i))
                            .arraylength()
                            .invokestatic(CD_Checks,
                                "checkArraySize",
                                MTD_void_int_int);
                    }

                    // initialize stack
                    if (shouldAddStack) {
                        codeBuilder.invokestatic(CD_MemoryStack, "stackGet", MTD_MemoryStack)
                            .astore(stackSlot)
                            .aload(stackSlot)
                            .invokevirtual(CD_MemoryStack, "pointer", MTD_long)
                            .lstore(stackPointerSlot);
                    }

                    codeBuilder.trying(
                        blockCodeBuilder -> {
                            final boolean skipFirstParam = methodData.skipFirstParam();
                            final int parameterSize = parameters.size();
                            final List<ClassDesc> parameterCDList = new ArrayList<>(skipFirstParam ? parameterSize - 1 : parameterSize);

                            final ClassDesc cd_returnTypeDowncall = convertToDowncallCD(method, returnType);
                            final boolean returnVoid = returnType == void.class;

                            // ref
                            for (int i = 0, size = parameters.size(); i < size; i++) {
                                final Parameter parameter = parameters.get(i);
                                if (parameter.getDeclaredAnnotation(Ref.class) == null) {
                                    continue;
                                }
                                final Class<?> type = parameter.getType();
                                if (type.isArray()) {
                                    final Class<?> componentType = type.getComponentType();
                                    final int slot = blockCodeBuilder.allocateLocal(TypeKind.ReferenceType);
                                    blockCodeBuilder.aload(allocatorSlot)
                                        .aload(blockCodeBuilder.parameterSlot(i));

                                    if (componentType == String.class && getCharset(blockCodeBuilder, parameter)) {
                                        blockCodeBuilder.invokestatic(CD_Marshal,
                                            "marshal",
                                            MTD_MemorySegment_SegmentAllocator_StringArray_Charset
                                        ).astore(slot);
                                    } else {
                                        blockCodeBuilder.invokestatic(CD_Marshal,
                                            "marshal",
                                            MethodTypeDesc.of(CD_MemorySegment,
                                                CD_SegmentAllocator,
                                                convertToMarshalCD(componentType).arrayType())
                                        ).astore(slot);
                                    }
                                    parameterRefSlot.put(parameter, slot);
                                }
                            }

                            // invocation
                            blockCodeBuilder.aload(blockCodeBuilder.receiverSlot())
                                .getfield(cd_thisClass, handleName, CD_MethodHandle);
                            for (int i = skipFirstParam ? 1 : 0; i < parameterSize; i++) {
                                final Parameter parameter = parameters.get(i);
                                final Class<?> type = parameter.getType();
                                final ClassDesc cd_parameterDowncall = convertToDowncallCD(parameter, type);

                                final Integer refSlot = parameterRefSlot.get(parameter);
                                if (refSlot != null) {
                                    blockCodeBuilder.aload(refSlot);
                                } else {
                                    final int slot = blockCodeBuilder.parameterSlot(i);
                                    final Convert convert = parameter.getDeclaredAnnotation(Convert.class);
                                    if (convert != null && type == boolean.class) {
                                        final Type convertType = convert.value();
                                        blockCodeBuilder.loadInstruction(
                                            TypeKind.fromDescriptor(type.descriptorString()).asLoadable(),
                                            slot
                                        ).invokestatic(CD_Marshal,
                                            marshalFromBooleanMethod(convertType),
                                            MethodTypeDesc.of(convertType.classDesc(), CD_boolean));
                                    } else if (type.isPrimitive() ||
                                               type == MemorySegment.class ||
                                               SegmentAllocator.class.isAssignableFrom(type)) {
                                        blockCodeBuilder.loadInstruction(
                                            TypeKind.fromDescriptor(type.descriptorString()).asLoadable(),
                                            slot
                                        );
                                    } else if (type == String.class) {
                                        blockCodeBuilder.aload(allocatorSlot)
                                            .aload(slot);
                                        if (getCharset(blockCodeBuilder, parameter)) {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MTD_MemorySegment_SegmentAllocator_String_Charset);
                                        } else {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MTD_MemorySegment_SegmentAllocator_String);
                                        }
                                    } else if (Addressable.class.isAssignableFrom(type) ||
                                               CEnum.class.isAssignableFrom(type)) {
                                        blockCodeBuilder.aload(slot)
                                            .invokestatic(CD_Marshal,
                                                "marshal",
                                                MethodTypeDesc.of(cd_parameterDowncall, convertToMarshalCD(type)));
                                    } else if (Upcall.class.isAssignableFrom(type)) {
                                        blockCodeBuilder.aload(allocatorSlot)
                                            .checkcast(CD_Arena)
                                            .aload(slot)
                                            .invokestatic(CD_Marshal,
                                                "marshal",
                                                MTD_MemorySegment_Arena_Upcall);
                                    } else if (type.isArray()) {
                                        final Class<?> componentType = type.getComponentType();
                                        final boolean isStringArray = componentType == String.class;
                                        final boolean isUpcallArray = Upcall.class.isAssignableFrom(componentType);
                                        blockCodeBuilder.aload(allocatorSlot);
                                        if (isUpcallArray) {
                                            blockCodeBuilder.checkcast(CD_Arena);
                                        }
                                        blockCodeBuilder.aload(slot);
                                        if (isStringArray && getCharset(blockCodeBuilder, parameter)) {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MTD_MemorySegment_SegmentAllocator_StringArray_Charset);
                                        } else {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                    isUpcallArray ? CD_Arena : CD_SegmentAllocator,
                                                    convertToMarshalCD(componentType).arrayType()));
                                        }
                                    }
                                }

                                parameterCDList.add(cd_parameterDowncall);
                            }
                            blockCodeBuilder.invokevirtual(CD_MethodHandle,
                                "invokeExact",
                                MethodTypeDesc.of(cd_returnTypeDowncall, parameterCDList));

                            final int resultSlot;
                            if (returnVoid) {
                                resultSlot = -1;
                            } else {
                                final TypeKind typeKind = TypeKind.from(cd_returnTypeDowncall);
                                resultSlot = blockCodeBuilder.allocateLocal(typeKind);
                                blockCodeBuilder.storeInstruction(typeKind, resultSlot);
                            }

                            // copy ref result
                            for (int i = 0, size = parameters.size(); i < size; i++) {
                                final Parameter parameter = parameters.get(i);
                                final Class<?> type = parameter.getType();
                                final Class<?> componentType = type.getComponentType();
                                if (parameter.getDeclaredAnnotation(Ref.class) != null &&
                                    type.isArray()) {
                                    final boolean isPrimitiveArray = componentType.isPrimitive();
                                    final boolean isStringArray = componentType == String.class;
                                    if (isPrimitiveArray || isStringArray) {
                                        final int refSlot = parameterRefSlot.get(parameter);
                                        final int parameterSlot = blockCodeBuilder.parameterSlot(i);
                                        blockCodeBuilder.aload(refSlot)
                                            .aload(parameterSlot);
                                        if (isPrimitiveArray) {
                                            blockCodeBuilder.invokestatic(CD_Unmarshal,
                                                "copy",
                                                MethodTypeDesc.of(CD_void, CD_MemorySegment, ClassDesc.ofDescriptor(type.descriptorString())));
                                        } else {
                                            if (getCharset(blockCodeBuilder, parameter)) {
                                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                                    "copy",
                                                    MTD_void_MemorySegment_StringArray_Charset);
                                            } else {
                                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                                    "copy",
                                                    MTD_void_MemorySegment_StringArray);
                                            }
                                        }
                                    }
                                }
                            }

                            // wrap return value
                            final int unmarshalSlot;
                            if (returnVoid) {
                                unmarshalSlot = -1;
                            } else {
                                if (shouldAddStack) {
                                    unmarshalSlot = blockCodeBuilder.allocateLocal(returnTypeKind);
                                } else {
                                    unmarshalSlot = -1;
                                }
                                blockCodeBuilder.loadInstruction(TypeKind.from(cd_returnTypeDowncall), resultSlot);
                            }
                            final Convert convert = method.getDeclaredAnnotation(Convert.class);
                            if (convert != null && returnType == boolean.class) {
                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                    "unmarshalAsBoolean",
                                    MethodTypeDesc.of(CD_boolean, convert.value().classDesc()));
                            }
                            if (returnType == String.class) {
                                final boolean hasCharset = getCharset(blockCodeBuilder, method);
                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                    "unmarshalAsString",
                                    hasCharset ? MTD_String_MemorySegment_Charset : MTD_String_MemorySegment);
                            } else if (Struct.class.isAssignableFrom(returnType)) {
                                blockCodeBuilder.ifThenElse(Opcode.IFNONNULL,
                                    blockCodeBuilder1 -> blockCodeBuilder1.new_(cd_returnType)
                                        .dup()
                                        .aload(resultSlot)
                                        .invokespecial(cd_returnType,
                                            INIT_NAME,
                                            MTD_void_MemorySegment),
                                    CodeBuilder::aconst_null);
                            } else if (CEnum.class.isAssignableFrom(returnType)) {
                                final Method wrapper = findCEnumWrapper(returnType);
                                blockCodeBuilder.invokestatic(cd_returnType,
                                    wrapper.getName(),
                                    MethodTypeDesc.of(ClassDesc.ofDescriptor(wrapper.getReturnType().descriptorString()), CD_int),
                                    wrapper.getDeclaringClass().isInterface());
                            } else if (Upcall.class.isAssignableFrom(returnType)) {
                                final Method wrapper = findUpcallWrapper(returnType);
                                blockCodeBuilder.ifThenElse(Opcode.IFNONNULL,
                                    blockCodeBuilder1 -> blockCodeBuilder1.aload(allocatorSlot)
                                        .aload(resultSlot)
                                        .invokestatic(cd_returnType,
                                            wrapper.getName(),
                                            MethodTypeDesc.of(ClassDesc.ofDescriptor(wrapper.getReturnType().descriptorString()),
                                                ClassDesc.ofDescriptor(wrapper.getParameterTypes()[0].descriptorString()),
                                                CD_MemorySegment),
                                            wrapper.getDeclaringClass().isInterface()),
                                    CodeBuilder::aconst_null);
                            } else if (returnType.isArray()) {
                                final Class<?> componentType = returnType.getComponentType();
                                if (componentType == String.class) {
                                    final boolean hasCharset = getCharset(blockCodeBuilder, method);
                                    blockCodeBuilder.invokestatic(CD_Unmarshal,
                                        "unmarshalAsStringArray",
                                        hasCharset ? MTD_StringArray_MemorySegment_Charset : MTD_StringArray_MemorySegment);
                                } else if (componentType.isPrimitive() || componentType == MemorySegment.class) {
                                    blockCodeBuilder.invokestatic(CD_Unmarshal,
                                        unmarshalMethod(returnType),
                                        MethodTypeDesc.of(cd_returnType, CD_MemorySegment));
                                }
                            }

                            // reset stack
                            if (shouldAddStack) {
                                if (!returnVoid) {
                                    blockCodeBuilder.storeInstruction(returnTypeKind, unmarshalSlot);
                                }
                                blockCodeBuilder.aload(stackSlot)
                                    .lload(stackPointerSlot)
                                    .invokevirtual(CD_MemoryStack, "setPointer", MTD_void_long);
                                if (!returnVoid) {
                                    blockCodeBuilder.loadInstruction(returnTypeKind, unmarshalSlot);
                                }
                            }

                            // return
                            blockCodeBuilder.returnInstruction(returnTypeKind);
                        },
                        catchBuilder -> catchBuilder.catching(CD_Throwable, blockCodeBuilder -> {
                            final int slot = blockCodeBuilder.allocateLocal(TypeKind.ReferenceType);
                            // create exception
                            blockCodeBuilder.astore(slot)
                                .new_(CD_IllegalStateException)
                                .dup()
                                .ldc(methodData.exceptionString())
                                .aload(slot)
                                .invokespecial(CD_IllegalStateException, INIT_NAME, MTD_void_String_Throwable);
                            // reset stack
                            if (shouldAddStack) {
                                blockCodeBuilder.aload(stackSlot)
                                    .lload(stackPointerSlot)
                                    .invokevirtual(CD_MemoryStack, "setPointer", MTD_void_long);
                            }
                            // throw
                            blockCodeBuilder.athrow();
                        })
                    );
                };
                classBuilder.withMethod(methodName,
                    mtd_method,
                    Modifier.isPublic(modifiers) ? ACC_PUBLIC : ACC_PROTECTED,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                        if (returnType == MethodHandle.class) {
                            codeBuilder.aload(codeBuilder.receiverSlot())
                                .getfield(cd_thisClass, handleName, CD_MethodHandle);
                            if (method.isDefault()) {
                                codeBuilder.ifThenElse(Opcode.IFNONNULL,
                                    CodeBuilder::areturn,
                                    blockCodeBuilder -> {
                                        // invoke super interface
                                        invokeSuperMethod(blockCodeBuilder, parameters);
                                        final Class<?> declaringClass = method.getDeclaringClass();
                                        blockCodeBuilder.invokespecial(ClassDesc.ofDescriptor(declaringClass.descriptorString()),
                                                methodName,
                                                mtd_method,
                                                declaringClass.isInterface())
                                            .areturn();
                                    });
                            } else {
                                codeBuilder.areturn();
                            }
                            return;
                        }
                        if (method.isDefault()) {
                            codeBuilder.aload(codeBuilder.receiverSlot())
                                .getfield(cd_thisClass, handleName, CD_MethodHandle);
                            codeBuilder.ifThenElse(Opcode.IFNONNULL,
                                wrapping::accept,
                                blockCodeBuilder -> {
                                    // invoke super interface
                                    invokeSuperMethod(blockCodeBuilder, parameters);
                                    final Class<?> declaringClass = method.getDeclaringClass();
                                    blockCodeBuilder.invokespecial(declaringClass.describeConstable().orElseThrow(),
                                        methodName,
                                        mtd_method,
                                        declaringClass.isInterface()
                                    ).returnInstruction(returnTypeKind);
                                }
                            ).nop();
                        } else {
                            wrapping.accept(codeBuilder);
                        }
                    }));
            });

            // handle loader
            handleDataMap.forEach((method, methodData) -> classBuilder.withMethod(
                methodData.loaderName(),
                MTD_MethodHandle_SymbolLookup,
                ACC_PRIVATE | ACC_STATIC,
                methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                    final int optionalSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                    final int descriptorSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                    final String entrypoint = methodData.entrypoint();

                    final var returnType = method.getReturnType();
                    final boolean returnVoid = returnType == void.class;
                    final boolean methodByValue = method.getDeclaredAnnotation(ByValue.class) != null;

                    // function descriptor
                    if (descriptorMap.containsKey(entrypoint)) {
                        codeBuilder.ldc(DynamicConstantDesc.ofNamed(BSM_CLASS_DATA,
                                DEFAULT_NAME,
                                CD_Map))
                            .ldc(entrypoint)
                            .invokeinterface(CD_Map, "get", MTD_Object_Object)
                            .checkcast(CD_FunctionDescriptor);
                    } else {
                        if (!returnVoid) {
                            final Convert convert = method.getDeclaredAnnotation(Convert.class);
                            if (convert != null && returnType == boolean.class) {
                                convertToValueLayout(codeBuilder, convert.value().representation());
                            } else if (returnType.isPrimitive()) {
                                convertToValueLayout(codeBuilder, returnType);
                            } else {
                                final SizedSeg sizedSeg = method.getDeclaredAnnotation(SizedSeg.class);
                                final Sized sized = method.getDeclaredAnnotation(Sized.class);
                                final boolean isSizedSeg = sizedSeg != null;
                                final boolean isSized = sized != null;
                                if (Struct.class.isAssignableFrom(returnType)) {
                                    final String name = Arrays.stream(returnType.getDeclaredFields())
                                        .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == StructLayout.class)
                                        .findFirst()
                                        .orElseThrow(() ->
                                            new IllegalStateException(STR."The struct \{returnType} must contain one public static field that is StructLayout"))
                                        .getName();
                                    if (!methodByValue) {
                                        convertToValueLayout(codeBuilder, returnType);
                                        if (isSizedSeg) {
                                            codeBuilder.constantInstruction(sizedSeg.value());
                                        } else if (isSized) {
                                            codeBuilder.constantInstruction((long) sized.value());
                                        }
                                    }
                                    codeBuilder.getstatic(ClassDesc.ofDescriptor(returnType.descriptorString()),
                                        name,
                                        CD_StructLayout);
                                    if (!methodByValue) {
                                        if (isSizedSeg || isSized) {
                                            codeBuilder.invokestatic(CD_MemoryLayout,
                                                "sequenceLayout",
                                                MTD_SequenceLayout_long_MemoryLayout,
                                                true);
                                        }
                                        codeBuilder.invokeinterface(CD_AddressLayout,
                                            "withTargetLayout",
                                            MTD_AddressLayout_MemoryLayout);
                                    }
                                } else {
                                    convertToValueLayout(codeBuilder, returnType);
                                    if (isSizedSeg || isSized) {
                                        if (isSizedSeg) {
                                            codeBuilder.constantInstruction(sizedSeg.value());
                                            convertToValueLayout(codeBuilder, byte.class);
                                        } else {
                                            codeBuilder.constantInstruction((long) sized.value());
                                            convertToValueLayout(codeBuilder, returnType.isArray() ? returnType.getComponentType() : byte.class);
                                        }
                                        codeBuilder.invokestatic(CD_MemoryLayout,
                                                "sequenceLayout",
                                                MTD_SequenceLayout_long_MemoryLayout,
                                                true)
                                            .invokeinterface(CD_AddressLayout,
                                                "withTargetLayout",
                                                MTD_AddressLayout_MemoryLayout);
                                    }
                                }
                            }
                        }
                        final var parameters = methodData.parameters();
                        final boolean skipFirstParam = methodData.skipFirstParam();
                        final int size = skipFirstParam || methodByValue ?
                            parameters.size() - 1 :
                            parameters.size();
                        codeBuilder.constantInstruction(size)
                            .anewarray(CD_MemoryLayout);
                        for (int i = 0; i < size; i++) {
                            final Parameter parameter = parameters.get(skipFirstParam ? i + 1 : i);
                            final Convert convert = parameter.getDeclaredAnnotation(Convert.class);
                            codeBuilder.dup()
                                .constantInstruction(i);
                            if (convert != null && parameter.getType() == boolean.class) {
                                convertToValueLayout(codeBuilder, convert.value().representation());
                            } else {
                                convertToValueLayout(codeBuilder, parameter.getType());
                            }
                            codeBuilder.aastore();
                        }
                        codeBuilder.invokestatic(CD_FunctionDescriptor,
                            returnVoid ? "ofVoid" : "of",
                            returnVoid ?
                                MTD_FunctionDescriptor_MemoryLayoutArray :
                                MTD_FunctionDescriptor_MemoryLayout_MemoryLayoutArray,
                            true);
                    }
                    codeBuilder.astore(descriptorSlot);

                    codeBuilder.aload(codeBuilder.parameterSlot(0))
                        .ldc(entrypoint)
                        .invokeinterface(CD_SymbolLookup, "find", MTD_Optional_String)
                        .astore(optionalSlot)
                        .aload(optionalSlot)
                        .invokevirtual(CD_Optional, "isPresent", MTD_boolean);
                    codeBuilder.ifThenElse(
                        blockCodeBuilder -> {
                            blockCodeBuilder.getstatic(cd_thisClass, "LINKER", CD_Linker)
                                .aload(optionalSlot)
                                .invokevirtual(CD_Optional, "get", MTD_Object)
                                .checkcast(CD_MemorySegment);

                            codeBuilder.aload(descriptorSlot);

                            // linker option
                            final Critical critical = method.getDeclaredAnnotation(Critical.class);
                            if (critical != null) {
                                blockCodeBuilder.iconst_1()
                                    .anewarray(CD_Linker_Option)
                                    .dup()
                                    .iconst_0()
                                    .constantInstruction(critical.allowHeapAccess() ? 1 : 0)
                                    .invokestatic(CD_Linker_Option,
                                        "critical",
                                        MTD_LinkerOption_boolean,
                                        true)
                                    .aastore();
                            } else {
                                blockCodeBuilder.iconst_0()
                                    .anewarray(CD_Linker_Option);
                            }

                            blockCodeBuilder.invokeinterface(CD_Linker,
                                "downcallHandle",
                                MTD_MethodHandle_MemorySegment_FunctionDescriptor_LinkerOptionArray
                            ).areturn();
                        },
                        blockCodeBuilder -> {
                            if (method.isDefault()) {
                                blockCodeBuilder.aconst_null()
                                    .areturn();
                            } else {
                                blockCodeBuilder.new_(CD_IllegalStateException)
                                    .dup()
                                    .ldc(STR."""
                                        Couldn't find function with name \
                                        \{entrypoint}: \{methodData.exceptionString()} and descriptor:""")
                                    .aload(descriptorSlot)
                                    .invokestatic(CD_String, "valueOf", MTD_String_Object)
                                    .invokevirtual(CD_String, "concat", MTD_String_String)
                                    .invokespecial(CD_IllegalStateException, INIT_NAME, MTD_void_String)
                                    .athrow();
                            }
                        });
                })
            ));

            // class initializer
            classBuilder.withMethod(CLASS_INIT_NAME, MTD_void, ACC_STATIC,
                methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                    // linker
                    codeBuilder.invokestatic(CD_Linker, "nativeLinker", MTD_Linker, true)
                        .putstatic(cd_thisClass, "LINKER", CD_Linker)
                        .return_();
                }));
        });

        try {
            final MethodHandles.Lookup hiddenClass = MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup())
                .defineHiddenClassWithClassData(bytes, Map.copyOf(descriptorMap), true, MethodHandles.Lookup.ClassOption.STRONG);
            return (T) hiddenClass.findConstructor(hiddenClass.lookupClass(), MethodType.methodType(void.class, SymbolLookup.class))
                .invoke(lookup);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
