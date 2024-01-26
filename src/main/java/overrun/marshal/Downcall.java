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

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
 * {@link Critical @Critical} indicates that the annotated method is {@linkplain java.lang.foreign.Linker.Option#critical(boolean) critical}.
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
    private static final ClassDesc CD_Linker_Option = CD_Linker.nested("Option");
    private static final ClassDesc CD_Marshal = ClassDesc.of("overrun.marshal.Marshal");
    private static final ClassDesc CD_MemoryLayout = ClassDesc.of("java.lang.foreign.MemoryLayout");
    private static final ClassDesc CD_MemorySegment = ClassDesc.of("java.lang.foreign.MemorySegment");
    private static final ClassDesc CD_MemoryStack = ClassDesc.of("overrun.marshal.MemoryStack");
    private static final ClassDesc CD_Optional = ClassDesc.of("java.util.Optional");
    private static final ClassDesc CD_SegmentAllocator = ClassDesc.of("java.lang.foreign.SegmentAllocator");
    private static final ClassDesc CD_SequenceLayout = ClassDesc.of("java.lang.foreign.SequenceLayout");
    private static final ClassDesc CD_StandardCharsets = ClassDesc.of("java.nio.charset.StandardCharsets");
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
    private static final MethodTypeDesc MTD_marshalStringCharset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String,
        CD_Charset);
    private static final MethodTypeDesc MTD_marshalString = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String);
    private static final MethodTypeDesc MTD_marshalStringCharsetArray = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String.arrayType(),
        CD_Charset);

    private Downcall() {
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
                .invokestatic(CD_Charset, "forName", MethodTypeDesc.of(CD_Charset, CD_String));
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

    private static void tryAddLayout(CodeBuilder codeBuilder, Class<?> aClass) {
        if (aClass.isArray()) {
            final Class<?> componentType = aClass.getComponentType();
            if (componentType == String.class) {
                convertToValueLayout(codeBuilder, MemorySegment.class);
            } else if (componentType.isPrimitive()) {
                convertToValueLayout(codeBuilder, componentType);
            }
        }
    }

    private static String getMethodEntrypoint(Method method) {
        final Entrypoint entrypoint = method.getDeclaredAnnotation(Entrypoint.class);
        return (entrypoint == null || entrypoint.value().isBlank()) ?
            method.getName() :
            entrypoint.value();
    }

    private static ClassDesc convertToValueLayoutCD(Class<?> aClass) {
        if (aClass == boolean.class) return CD_ValueLayout_OfBoolean;
        if (aClass == char.class) return CD_ValueLayout_OfChar;
        if (aClass == byte.class) return CD_ValueLayout_OfByte;
        if (aClass == short.class) return CD_ValueLayout_OfShort;
        if (aClass == int.class || CEnum.class.isAssignableFrom(aClass)) return CD_ValueLayout_OfInt;
        if (aClass == long.class) return CD_ValueLayout_OfLong;
        if (aClass == float.class) return CD_ValueLayout_OfFloat;
        if (aClass == double.class) return CD_ValueLayout_OfDouble;
        return CD_AddressLayout;
    }

    private static ClassDesc convertToDowncallCD(Class<?> aClass) {
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

    private static boolean shouldStoreResult(Class<?> aClass, List<Parameter> parameters) {
        return Upcall.class.isAssignableFrom(aClass) ||
               parameters.stream().anyMatch(parameter -> parameter.getDeclaredAnnotation(Ref.class) != null);
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
                   types[0] == Arena.class &&
                   types[1] == MemorySegment.class &&
                   Upcall.class.isAssignableFrom(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadBytecode(Class<?> callerClass, SymbolLookup lookup) {
        final ClassFile cf = ClassFile.of();
        final ClassDesc cd_thisClass = ClassDesc.of(callerClass.getPackageName(), STR."_\{callerClass.getSimpleName()}");
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
                    STR."load$\{method.getName()}",
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
            classBuilder.withMethod(INIT_NAME, MethodTypeDesc.of(CD_void, CD_SymbolLookup), ACC_PUBLIC,
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
                                .invokestatic(cd_thisClass, methodData.loaderName(), MethodTypeDesc.of(CD_MethodHandle, CD_SymbolLookup))
                                .putfield(cd_thisClass, methodData.handleName(), CD_MethodHandle);
                        });

                        codeBuilder.return_();
                    }));

            // methods
            methodDataMap.forEach((method, methodData) -> {
                final String methodName = method.getName();
                final int modifiers = method.getModifiers();
                final var returnType = method.getReturnType();
                final ClassDesc cd_returnType = ClassDesc.ofDescriptor(returnType.descriptorString());
                final TypeKind returnTypeKind = TypeKind.from(cd_returnType).asLoadable();
                final List<Parameter> parameters = methodData.parameters();
                final MethodTypeDesc mtd_method = MethodTypeDesc.of(cd_returnType,
                    parameters.stream()
                        .map(parameter -> ClassDesc.ofDescriptor(parameter.getType().descriptorString()))
                        .toList());

                final String handleName = methodData.handleName();
                final Consumer<CodeBuilder> wrapping = codeBuilder -> {
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
                                MethodTypeDesc.of(CD_void, CD_int, CD_int));
                    }

                    // initialize stack
                    if (shouldAddStack) {
                        codeBuilder.invokestatic(CD_MemoryStack, "stackGet", MethodTypeDesc.of(CD_MemoryStack))
                            .astore(stackSlot)
                            .aload(stackSlot)
                            .invokevirtual(CD_MemoryStack, "pointer", MethodTypeDesc.of(CD_long))
                            .lstore(stackPointerSlot);
                    }

                    codeBuilder.trying(
                        blockCodeBuilder -> {
                            final boolean skipFirstParam = methodData.skipFirstParam();
                            final int parameterSize = parameters.size();
                            final List<ClassDesc> parameterCDList = new ArrayList<>(skipFirstParam ? parameterSize - 1 : parameterSize);

                            final ClassDesc cd_returnTypeDowncall = convertToDowncallCD(returnType);
                            final boolean returnVoid = returnType == void.class;
                            final boolean shouldStoreResult =
                                !returnVoid &&
                                shouldStoreResult(returnType, parameters);
                            final int resultSlot;

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
                                            MTD_marshalStringCharsetArray
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
                            if (!shouldStoreResult) {
                                tryAddLayout(blockCodeBuilder, returnType);
                            }
                            blockCodeBuilder.aload(blockCodeBuilder.receiverSlot())
                                .getfield(cd_thisClass, handleName, CD_MethodHandle);
                            for (int i = skipFirstParam ? 1 : 0; i < parameterSize; i++) {
                                final Parameter parameter = parameters.get(i);
                                final Class<?> type = parameter.getType();

                                final Integer refSlot = parameterRefSlot.get(parameter);
                                if (refSlot != null) {
                                    blockCodeBuilder.aload(refSlot);
                                } else {
                                    final int slot = blockCodeBuilder.parameterSlot(i);
                                    if (type.isPrimitive() ||
                                        type == MemorySegment.class) {
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
                                                MTD_marshalStringCharset);
                                        } else {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MTD_marshalString);
                                        }
                                    } else if (Addressable.class.isAssignableFrom(type) ||
                                               CEnum.class.isAssignableFrom(type)) {
                                        blockCodeBuilder.aload(slot)
                                            .invokestatic(CD_Marshal,
                                                "marshal",
                                                MethodTypeDesc.of(convertToDowncallCD(type), convertToMarshalCD(type)));
                                    } else if (Upcall.class.isAssignableFrom(type)) {
                                        blockCodeBuilder.aload(allocatorSlot)
                                            .checkcast(CD_Arena)
                                            .aload(slot)
                                            .invokestatic(CD_Marshal,
                                                "marshal",
                                                MethodTypeDesc.of(CD_MemorySegment, CD_Arena, CD_Upcall));
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
                                                MTD_marshalStringCharsetArray);
                                        } else {
                                            blockCodeBuilder.invokestatic(CD_Marshal,
                                                "marshal",
                                                MethodTypeDesc.of(CD_MemorySegment,
                                                    isUpcallArray ? CD_Arena : CD_SegmentAllocator,
                                                    convertToMarshalCD(componentType).arrayType()));
                                        }
                                    }
                                }

                                parameterCDList.add(convertToDowncallCD(type));
                            }
                            blockCodeBuilder.invokevirtual(CD_MethodHandle,
                                "invokeExact",
                                MethodTypeDesc.of(cd_returnTypeDowncall, parameterCDList));

                            if (shouldStoreResult) {
                                final TypeKind typeKind = TypeKind.from(cd_returnTypeDowncall);
                                resultSlot = blockCodeBuilder.allocateLocal(typeKind);
                                blockCodeBuilder.storeInstruction(typeKind, resultSlot);

                                tryAddLayout(blockCodeBuilder, returnType);
                                blockCodeBuilder.loadInstruction(typeKind, resultSlot);
                            } else {
                                resultSlot = -1;
                            }

                            // wrap return value
                            if (CEnum.class.isAssignableFrom(returnType)) {
                                final Method wrapper = findCEnumWrapper(returnType);
                                blockCodeBuilder.invokestatic(cd_returnType,
                                    wrapper.getName(),
                                    MethodTypeDesc.of(ClassDesc.ofDescriptor(wrapper.getReturnType().descriptorString()), CD_int),
                                    wrapper.getDeclaringClass().isInterface());
                            } else if (Upcall.class.isAssignableFrom(returnType)) {
                                final Method wrapper = findUpcallWrapper(returnType);
                                blockCodeBuilder.aload(resultSlot)
                                    .ifThenElse(Opcode.IFNONNULL,
                                        blockCodeBuilder1 -> blockCodeBuilder1.aload(allocatorSlot)
                                            .aload(resultSlot)
                                            .invokestatic(cd_returnType,
                                                wrapper.getName(),
                                                MethodTypeDesc.of(ClassDesc.ofDescriptor(wrapper.getReturnType().descriptorString()),
                                                    CD_Arena,
                                                    CD_MemorySegment),
                                                wrapper.getDeclaringClass().isInterface()),
                                        CodeBuilder::aconst_null);
                            } else if (returnType == String.class) {
                                final boolean hasCharset = getCharset(blockCodeBuilder, method);
                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                    "unmarshalAsString",
                                    MethodTypeDesc.of(CD_String,
                                        hasCharset ?
                                            List.of(CD_MemorySegment, CD_Charset) :
                                            List.of(CD_MemorySegment)));
                            } else if (returnType.isArray()) {
                                final Class<?> componentType = returnType.getComponentType();
                                if (componentType == String.class) {
                                    final boolean hasCharset = getCharset(blockCodeBuilder, method);
                                    blockCodeBuilder.invokestatic(CD_Unmarshal,
                                        "unmarshalAsStringArray",
                                        MethodTypeDesc.of(CD_String.arrayType(),
                                            hasCharset ?
                                                List.of(CD_AddressLayout, CD_MemorySegment, CD_Charset) :
                                                List.of(CD_AddressLayout, CD_MemorySegment)));
                                } else if (componentType.isPrimitive()) {
                                    blockCodeBuilder.invokestatic(CD_Unmarshal,
                                        "unmarshal",
                                        MethodTypeDesc.of(cd_returnType,
                                            convertToValueLayoutCD(componentType),
                                            CD_MemorySegment));
                                }
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
                                                    MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_String.arrayType(), CD_Charset));
                                            } else {
                                                blockCodeBuilder.invokestatic(CD_Unmarshal,
                                                    "copy",
                                                    MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_String.arrayType()));
                                            }
                                        }
                                    }
                                }
                            }

                            // reset stack
                            if (shouldAddStack) {
                                blockCodeBuilder.aload(stackSlot)
                                    .lload(stackPointerSlot)
                                    .invokevirtual(CD_MemoryStack, "setPointer", MethodTypeDesc.of(CD_void, CD_long));
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
                                .invokespecial(CD_IllegalStateException, INIT_NAME, MethodTypeDesc.of(CD_void, CD_String, CD_Throwable));
                            // reset stack
                            if (shouldAddStack) {
                                blockCodeBuilder.aload(stackSlot)
                                    .lload(stackPointerSlot)
                                    .invokevirtual(CD_MemoryStack, "setPointer", MethodTypeDesc.of(CD_void, CD_long));
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
                        if (method.isDefault()) {
                            codeBuilder.aload(codeBuilder.receiverSlot())
                                .getfield(cd_thisClass, handleName, CD_MethodHandle);
                            codeBuilder.ifThenElse(Opcode.IFNONNULL,
                                wrapping::accept,
                                blockCodeBuilder -> {
                                    // invoke super interface
                                    blockCodeBuilder.aload(blockCodeBuilder.receiverSlot());
                                    for (int i = 0, size = parameters.size(); i < size; i++) {
                                        final var parameter = parameters.get(i);
                                        blockCodeBuilder.loadInstruction(
                                            TypeKind.fromDescriptor(parameter.getType().descriptorString())
                                                .asLoadable(),
                                            blockCodeBuilder.parameterSlot(i));
                                    }
                                    final Class<?> declaringClass = method.getDeclaringClass();
                                    blockCodeBuilder.invokespecial(declaringClass.describeConstable().orElseThrow(),
                                        method.getName(),
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
                MethodTypeDesc.of(CD_MethodHandle, CD_SymbolLookup),
                ACC_PRIVATE | ACC_STATIC,
                methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                    final int optionalSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                    codeBuilder.aload(codeBuilder.parameterSlot(0))
                        .ldc(methodData.entrypoint())
                        .invokeinterface(CD_SymbolLookup, "find", MethodTypeDesc.of(CD_Optional, CD_String))
                        .astore(optionalSlot)
                        .aload(optionalSlot)
                        .invokevirtual(CD_Optional, "isPresent", MethodTypeDesc.of(CD_boolean));
                    codeBuilder.ifThenElse(
                        blockCodeBuilder -> {
                            blockCodeBuilder.getstatic(cd_thisClass, "LINKER", CD_Linker)
                                .aload(optionalSlot)
                                .invokevirtual(CD_Optional, "get", MethodTypeDesc.of(CD_Object))
                                .checkcast(CD_MemorySegment);

                            // layout
                            final var returnType = method.getReturnType();
                            final boolean returnVoid = returnType == void.class;
                            if (!returnVoid) {
                                convertToValueLayout(blockCodeBuilder, returnType);
                                if (!returnType.isPrimitive()) {
                                    final SizedSeg sizedSeg = method.getDeclaredAnnotation(SizedSeg.class);
                                    final Sized sized = method.getDeclaredAnnotation(Sized.class);
                                    final boolean isSizedSeg = sizedSeg != null;
                                    final boolean isSized = sized != null && returnType.isArray();
                                    if (isSizedSeg || isSized) {
                                        if (isSizedSeg) {
                                            blockCodeBuilder.constantInstruction(sizedSeg.value());
                                            convertToValueLayout(blockCodeBuilder, byte.class);
                                        } else {
                                            blockCodeBuilder.constantInstruction((long) sized.value());
                                            convertToValueLayout(blockCodeBuilder, returnType.getComponentType());
                                        }
                                        blockCodeBuilder.invokestatic(CD_MemoryLayout,
                                                "sequenceLayout",
                                                MethodTypeDesc.of(CD_SequenceLayout, CD_long, CD_MemoryLayout),
                                                true)
                                            .invokeinterface(CD_AddressLayout,
                                                "withTargetLayout",
                                                MethodTypeDesc.of(CD_AddressLayout, CD_MemoryLayout));
                                    }
                                }
                            }
                            final var parameters = methodData.parameters();
                            final boolean skipFirstParam = methodData.skipFirstParam();
                            final int size = skipFirstParam ? parameters.size() - 1 : parameters.size();
                            blockCodeBuilder.constantInstruction(size)
                                .anewarray(CD_MemoryLayout);
                            for (int i = 0; i < size; i++) {
                                blockCodeBuilder.dup()
                                    .constantInstruction(i);
                                convertToValueLayout(blockCodeBuilder, parameters.get(skipFirstParam ? i + 1 : i).getType());
                                blockCodeBuilder.aastore();
                            }
                            blockCodeBuilder.invokestatic(CD_FunctionDescriptor,
                                returnVoid ? "ofVoid" : "of",
                                returnVoid ?
                                    MethodTypeDesc.of(CD_FunctionDescriptor, CD_MemoryLayout.arrayType()) :
                                    MethodTypeDesc.of(CD_FunctionDescriptor, CD_MemoryLayout, CD_MemoryLayout.arrayType()),
                                true);

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
                                        MethodTypeDesc.of(CD_Linker_Option, CD_boolean),
                                        true)
                                    .aastore();
                            } else {
                                blockCodeBuilder.iconst_0()
                                    .anewarray(CD_Linker_Option);
                            }

                            blockCodeBuilder.invokeinterface(CD_Linker,
                                "downcallHandle",
                                MethodTypeDesc.of(CD_MethodHandle,
                                    CD_MemorySegment,
                                    CD_FunctionDescriptor,
                                    CD_Linker_Option.arrayType())
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
                                        Failed to load function with name \
                                        \{methodData.entrypoint()}: \{methodData.exceptionString()}""")
                                    .invokespecial(CD_IllegalStateException, INIT_NAME, MethodTypeDesc.of(CD_void, CD_String))
                                    .athrow();
                            }
                        });
                })
            ));

            // class initializer
            classBuilder.withMethod(CLASS_INIT_NAME, MTD_void, ACC_STATIC,
                methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                    // linker
                    codeBuilder.invokestatic(CD_Linker, "nativeLinker", MethodTypeDesc.of(CD_Linker), true)
                        .putstatic(cd_thisClass, "LINKER", CD_Linker)
                        .return_();
                }));
        });

        try {
            final MethodHandles.Lookup hiddenClass = MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup())
                .defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.STRONG);
            return (T) hiddenClass.findConstructor(hiddenClass.lookupClass(), MethodType.methodType(void.class, SymbolLookup.class))
                .invoke(lookup);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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
        return loadBytecode(targetClass, lookup);
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
}
