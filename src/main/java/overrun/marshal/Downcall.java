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
import overrun.marshal.gen.processor.ArgumentProcessor;
import overrun.marshal.gen.processor.ArgumentProcessorContext;
import overrun.marshal.gen.processor.ProcessorTypes;
import overrun.marshal.internal.DowncallOptions;
import overrun.marshal.internal.data.DowncallData;
import overrun.marshal.struct.ByValue;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructAllocator;

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static overrun.marshal.internal.Constants.*;
import static overrun.marshal.internal.StringCharset.getCharset;

/**
 * Downcall library loader.
 * <h2>Loading native library</h2>
 * You can load native libraries with {@link #load(MethodHandles.Lookup, SymbolLookup, DowncallOption...)}.
 * This method generates a hidden class that loads method handle with the given symbol lookup.
 * <p>
 * The {@code load} methods accept a lookup object for defining hidden class with the caller.
 * The lookup object <strong>MUST</strong> have {@linkplain MethodHandles.Lookup#hasFullPrivilegeAccess() full privilege access}
 * which allows {@code Downcall} to define the hidden class.
 * You can obtain that lookup object with {@link MethodHandles#lookup()}.
 * <p>
 * The generated class implements the target class.
 * <h2>Methods</h2>
 * The loader finds method from the target class and its superclasses.
 * <p>
 * The loader skips static methods and methods annotated with {@link Skip @Skip} while generating.
 * <p>
 * {@link Entrypoint @Entrypoint} specifies the entrypoint of the annotated method.
 * An entrypoint is a symbol name
 * that locates to the function when {@linkplain SymbolLookup#find(String) finding} the address.
 * The default entrypoint is the same as the name of the method.
 * <p>
 * The loader will not throw an exception
 * if the method is modified with {@code default} and the native function is not found;
 * instead, it will return {@code null} and automatically invokes the original method declared in the target class.
 * <p>
 * {@link Critical @Critical} indicates that the annotated method is {@linkplain Linker.Option#critical(boolean) critical}.
 * <h3>Parameter Annotations</h3>
 * See {@link Ref @Ref}, {@link Sized @Sized} {@link SizedSeg @SizedSeg} and {@link StrCharset @StrCharset}.
 * <h2>Direct Access</h2>
 * You can get direct access by implement the class with {@link DirectAccess},
 * which allows you to get the function descriptor and the method handle for a given method.
 * <h2>Custom Function Descriptors</h2>
 * You can use a custom function descriptor for each method.
 * <pre>{@code
 * public interface GL {
 *     GL INSTANCE = Downcall.load(lookup(), "libGL.so",
 *         DowncallOption.descriptors(Map.of("glClear", FunctionDescriptor.ofVoid(JAVA_INT))));
 *     void glClear(int mask);
 * }
 * }</pre>
 * <h2>Custom Method Handles</h2>
 * You can get a method handle by declaring a method that returns a method handle.
 * This requires a custom function descriptor.
 * <pre>{@code
 * @Entrypoint("glClear")
 * MethodHandle mh_glClear();
 * default void glClear(int mask) throws Throwable { mh_glClear().invokeExact(mask); }
 * }</pre>
 * <h2>Example</h2>
 * <pre>{@code
 * public interface GL {
 *     GL INSTANCE = Downcall.load(MethodHandles.lookup(), "libGL.so");
 *     int COLOR_BUFFER_BIT = 0x00004000;
 *     void glClear(int mask);
 * }
 * }</pre>
 *
 * @author squid233
 * @see Critical
 * @see DirectAccess
 * @see Entrypoint
 * @see Ref
 * @see Sized
 * @see SizedSeg
 * @see Skip
 * @see StrCharset
 * @since 0.1.0
 */
@SuppressWarnings("preview")
public final class Downcall {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Linker.Option[] NO_OPTION = new Linker.Option[0];
    private static final Linker.Option[] OPTION_CRITICAL_FALSE = {Linker.Option.critical(false)};
    private static final Linker.Option[] OPTION_CRITICAL_TRUE = {Linker.Option.critical(true)};

    private Downcall() {
    }

    /**
     * Loads a class with the given symbol lookup and options.
     *
     * @param caller  the lookup object for the caller
     * @param lookup  the symbol lookup
     * @param options the options
     * @param <T>     the type of the target class
     * @return the loaded implementation instance of the target class
     */
    public static <T> T load(MethodHandles.Lookup caller, SymbolLookup lookup, DowncallOption... options) {
        return loadBytecode(caller, lookup, options);
    }

    /**
     * Loads a class with the given library name and options.
     *
     * @param caller  the lookup object for the caller
     * @param libPath the path of the library
     * @param options the options
     * @param <T>     the type of the target class
     * @return the loaded implementation instance of the target class
     */
    public static <T> T load(MethodHandles.Lookup caller, String libPath, DowncallOption... options) {
        return load(caller, SymbolLookup.libraryLookup(libPath, Arena.ofAuto()), options);
    }

    // bytecode

    private static void invokeSuperMethod(CodeBuilder codeBuilder, List<Parameter> parameters) {
        codeBuilder.aload(codeBuilder.receiverSlot());
        for (int i = 0, size = parameters.size(); i < size; i++) {
            final var parameter = parameters.get(i);
            codeBuilder.loadLocal(
                TypeKind.fromDescriptor(parameter.getType().descriptorString())
                    .asLoadable(),
                codeBuilder.parameterSlot(i));
        }
    }

    // method

    private static String getMethodEntrypoint(Method method) {
        final Entrypoint entrypoint = method.getDeclaredAnnotation(Entrypoint.class);
        return (entrypoint == null || entrypoint.value().isBlank()) ?
            method.getName() :
            entrypoint.value();
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

    // type

    private static ValueLayout getValueLayout(Class<?> carrier) {
        if (carrier == boolean.class) return ValueLayout.JAVA_BOOLEAN;
        if (carrier == char.class) return ValueLayout.JAVA_CHAR;
        if (carrier == byte.class) return ValueLayout.JAVA_BYTE;
        if (carrier == short.class) return ValueLayout.JAVA_SHORT;
        if (carrier == int.class || CEnum.class.isAssignableFrom(carrier)) return ValueLayout.JAVA_INT;
        if (carrier == long.class) return ValueLayout.JAVA_LONG;
        if (carrier == float.class) return ValueLayout.JAVA_FLOAT;
        if (carrier == double.class) return ValueLayout.JAVA_DOUBLE;
        return ValueLayout.ADDRESS;
    }

    private static boolean requireAllocator(Class<?> aClass) {
        return !aClass.isPrimitive() &&
            (aClass == String.class ||
                aClass.isArray() ||
                Upcall.class.isAssignableFrom(aClass));
    }

    // class desc

    private static ClassDesc convertToDowncallCD(AnnotatedElement element, Class<?> aClass) {
        final Convert convert = element.getDeclaredAnnotation(Convert.class);
        if (convert != null && aClass == boolean.class) return convert.value().classDesc();
        if (aClass.isPrimitive()) return aClass.describeConstable().orElseThrow();
        if (CEnum.class.isAssignableFrom(aClass)) return CD_int;
        if (SegmentAllocator.class.isAssignableFrom(aClass)) return CD_SegmentAllocator;
        if (aClass == Object.class) return CD_Object;
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

    // wrapper

    private static Method findWrapper(
        Class<?> aClass,
        Class<? extends Annotation> annotation,
        Predicate<Method> predicate) {
        return Arrays.stream(aClass.getDeclaredMethods())
            .filter(method -> method.getDeclaredAnnotation(annotation) != null)
            .filter(predicate)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Couldn't find wrapper method in " + aClass + "; mark it with @" + annotation.getSimpleName()));
    }

    private static Method findCEnumWrapper(Class<?> aClass) {
        return findWrapper(aClass, CEnum.Wrapper.class, method -> {
            final var types = method.getParameterTypes();
            return types.length == 1 &&
                types[0] == int.class &&
                CEnum.class.isAssignableFrom(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadBytecode(MethodHandles.Lookup caller, SymbolLookup lookup, DowncallOption... options) {
        Class<?> _targetClass = null, targetClass;
        Map<String, FunctionDescriptor> _descriptorMap = null, descriptorMap;
        UnaryOperator<MethodHandle> _transform = null, transform;

        for (DowncallOption option : options) {
            switch (option) {
                case DowncallOptions.TargetClass(var aClass) -> _targetClass = aClass;
                case DowncallOptions.Descriptors(var map) -> _descriptorMap = map;
                case DowncallOptions.Transform(var operator) -> _transform = operator;
                case null, default -> {
                }
            }
        }

        final Class<?> lookupClass = caller.lookupClass();
        targetClass = _targetClass != null ? _targetClass : lookupClass;
        descriptorMap = _descriptorMap != null ? _descriptorMap : Map.of();
        transform = _transform != null ? _transform : UnaryOperator.identity();

        final List<Method> methodList = Arrays.stream(targetClass.getMethods())
            .filter(Predicate.not(Downcall::shouldSkip))
            .toList();
        final Map<Method, String> exceptionStringMap = methodList.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), Downcall::createExceptionString));
        verifyMethods(methodList, exceptionStringMap);

        final ClassFile cf = of();
        final ClassDesc cd_thisClass = ClassDesc.of(lookupClass.getPackageName(), DEFAULT_NAME);
        final Map<Method, DowncallMethodData> methodDataMap = LinkedHashMap.newLinkedHashMap(methodList.size());

        //region method handles
        final AtomicInteger handleCount = new AtomicInteger();
        for (Method method : methodList) {
            final String entrypoint = getMethodEntrypoint(method);
            final String handleName = "$mh" + handleCount.getAndIncrement();
            final var parameters = List.of(method.getParameters());

            final DowncallMethodData methodData = new DowncallMethodData(
                entrypoint,
                handleName,
                exceptionStringMap.get(method),
                parameters,
                method.getDeclaredAnnotation(ByValue.class) == null &&
                    !parameters.isEmpty() &&
                    SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType())
            );
            methodDataMap.put(method, methodData);
        }
        //endregion

        final DowncallData downcallData = generateData(methodDataMap, lookup, descriptorMap, transform);

        final byte[] bytes = cf.build(cd_thisClass, classBuilder -> {
            classBuilder.withFlags(ACC_FINAL | ACC_SUPER);

            // inherit
            final ClassDesc cd_targetClass = targetClass.describeConstable().orElseThrow();
            if (targetClass.isInterface()) {
                classBuilder.withInterfaceSymbols(cd_targetClass);
            } else {
                classBuilder.withSuperclass(cd_targetClass);
            }

            //region method handles
            for (DowncallMethodData data : methodDataMap.values()) {
                if (downcallData.handleMap().get(data.entrypoint()) != null) {
                    classBuilder.withField(data.handleName(),
                        CD_MethodHandle,
                        ACC_PRIVATE | ACC_FINAL | ACC_STATIC);
                }
            }
            //endregion

            //region constructor
            classBuilder.withMethod(INIT_NAME, MTD_void, ACC_PUBLIC,
                methodBuilder ->
                    // super
                    methodBuilder.withCode(codeBuilder -> codeBuilder
                        .aload(codeBuilder.receiverSlot())
                        .invokespecial(targetClass.isInterface() ?
                                CD_Object :
                                cd_targetClass,
                            INIT_NAME,
                            MTD_void)
                        .return_()));
            //endregion

            //region methods
            for (var entry : methodDataMap.entrySet()) {
                Method method = entry.getKey();
                DowncallMethodData methodData = entry.getValue();
                final var returnType = method.getReturnType();
                final String methodName = method.getName();
                final int modifiers = method.getModifiers();
                final ClassDesc cd_returnType = ClassDesc.ofDescriptor(returnType.descriptorString());
                final List<Parameter> parameters = methodData.parameters();
                final MethodTypeDesc mtd_method = MethodTypeDesc.of(cd_returnType,
                    parameters.stream()
                        .map(parameter -> ClassDesc.ofDescriptor(parameter.getType().descriptorString()))
                        .toList());

                final String handleName = methodData.handleName();
                classBuilder.withMethod(methodName,
                    mtd_method,
                    Modifier.isPublic(modifiers) ? ACC_PUBLIC : ACC_PROTECTED,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                        final TypeKind returnTypeKind = TypeKind.from(cd_returnType).asLoadable();

                        if (returnType == MethodHandle.class) {
                            if (method.isDefault() &&
                                downcallData.handleMap().get(methodData.entrypoint()) == null) {
                                // invoke super interface
                                invokeSuperMethod(codeBuilder, parameters);
                                codeBuilder.invokespecial(cd_targetClass,
                                        methodName,
                                        mtd_method,
                                        targetClass.isInterface())
                                    .areturn();
                            } else {
                                codeBuilder.getstatic(cd_thisClass, handleName, CD_MethodHandle)
                                    .areturn();
                            }
                            return;
                        }

                        if (method.isDefault() &&
                            downcallData.handleMap().get(methodData.entrypoint()) == null) {
                            // invoke super interface
                            invokeSuperMethod(codeBuilder, parameters);
                            codeBuilder.invokespecial(cd_targetClass,
                                methodName,
                                mtd_method,
                                targetClass.isInterface()
                            ).return_(returnTypeKind);
                        } else {
                            //region body
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
                                    final List<ClassDesc> parameterCDList = new ArrayList<>(skipFirstParam ? parameterSize - 1 : parameterSize);
                                    blockCodeBuilder.getstatic(cd_thisClass, handleName, CD_MethodHandle);
                                    for (int i = skipFirstParam ? 1 : 0; i < parameterSize; i++) {
                                        final Parameter parameter = parameters.get(i);
                                        final Class<?> type = parameter.getType();
                                        final ClassDesc cd_parameterDowncall = convertToDowncallCD(parameter, type);

                                        if (parameterRefSlot.containsKey(parameter)) {
                                            blockCodeBuilder.aload(parameterRefSlot.get(parameter));
                                        } else {
                                            ArgumentProcessor.getInstance().process(
                                                blockCodeBuilder,
                                                ProcessorTypes.fromClass(type),
                                                new ArgumentProcessorContext(
                                                    parameter,
                                                    blockCodeBuilder.parameterSlot(i),
                                                    allocatorSlot,
                                                    parameter.getDeclaredAnnotation(Convert.class)
                                                )
                                            );
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
                                        blockCodeBuilder.storeLocal(typeKind, resultSlot);
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
                                        blockCodeBuilder.loadLocal(TypeKind.from(cd_returnTypeDowncall), resultSlot);
                                    }
                                    final Convert convert = method.getDeclaredAnnotation(Convert.class);
                                    if (convert != null && returnType == boolean.class) {
                                        blockCodeBuilder.invokestatic(CD_Unmarshal,
                                            "unmarshalAsBoolean",
                                            MethodTypeDesc.of(CD_boolean, convert.value().classDesc()));
                                    } else if (returnType == String.class) {
                                        final boolean hasCharset = getCharset(blockCodeBuilder, method);
                                        blockCodeBuilder.invokestatic(CD_Unmarshal,
                                            "unboundString",
                                            hasCharset ? MTD_String_MemorySegment_Charset : MTD_String_MemorySegment);
                                    } else if (Struct.class.isAssignableFrom(returnType)) {
                                        blockCodeBuilder.ifThenElse(Opcode.IFNONNULL,
                                            blockCodeBuilder1 -> {
                                                final var structAllocatorField = getStructAllocatorField(returnType);
                                                Objects.requireNonNull(structAllocatorField);
                                                blockCodeBuilder1.getstatic(cd_returnType, structAllocatorField.getName(), CD_StructAllocator)
                                                    .aload(resultSlot)
                                                    .invokevirtual(CD_StructAllocator, "of", MTD_Object_MemorySegment)
                                                    .checkcast(cd_returnType);
                                            },
                                            CodeBuilder::aconst_null);
                                    } else if (CEnum.class.isAssignableFrom(returnType)) {
                                        final Method wrapper = findCEnumWrapper(returnType);
                                        blockCodeBuilder.invokestatic(cd_returnType,
                                            wrapper.getName(),
                                            MethodTypeDesc.of(ClassDesc.ofDescriptor(wrapper.getReturnType().descriptorString()), CD_int),
                                            wrapper.getDeclaringClass().isInterface());
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
                                            blockCodeBuilder.storeLocal(returnTypeKind, unmarshalSlot);
                                        }
                                        blockCodeBuilder.aload(stackSlot)
                                            .lload(stackPointerSlot)
                                            .invokevirtual(CD_MemoryStack, "setPointer", MTD_void_long);
                                        if (!returnVoid) {
                                            blockCodeBuilder.loadLocal(returnTypeKind, unmarshalSlot);
                                        }
                                    }

                                    // return
                                    blockCodeBuilder.return_(returnTypeKind);
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
                            //endregion
                        }
                    }));
            }
            //endregion

            //region DirectAccess
            final boolean hasDirectAccess = DirectAccess.class.isAssignableFrom(targetClass);
            if (hasDirectAccess) {
                classBuilder.withMethod("functionDescriptors",
                    MTD_Map,
                    ACC_PUBLIC,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "descriptorMap", MTD_Map)
                        .areturn()));
                classBuilder.withMethod("methodHandles",
                    MTD_Map,
                    ACC_PUBLIC,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "handleMap", MTD_Map)
                        .areturn()));
                classBuilder.withMethod("symbolLookup",
                    MTD_SymbolLookup,
                    ACC_PUBLIC,
                    methodBuilder -> methodBuilder.withCode(codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "symbolLookup", MTD_SymbolLookup)
                        .areturn()));
            }
            //endregion

            //region class initializer
            classBuilder.withMethod(CLASS_INIT_NAME, MTD_void, ACC_STATIC,
                methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                    final int handleMapSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                    codeBuilder.ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "handleMap", MTD_Map)
                        .astore(handleMapSlot);

                    // method handles
                    methodDataMap.values().forEach(methodData -> {
                        final String entrypoint = methodData.entrypoint();
                        if (downcallData.handleMap().get(entrypoint) != null) {
                            codeBuilder
                                .aload(handleMapSlot)
                                .ldc(entrypoint)
                                .invokeinterface(CD_Map, "get", MTD_Object_Object)
                                .checkcast(CD_MethodHandle)
                                .putstatic(cd_thisClass, methodData.handleName(), CD_MethodHandle);
                        }
                    });

                    codeBuilder.return_();
                }));
            //endregion
        });

        try {
            final MethodHandles.Lookup hiddenClass = caller.defineHiddenClassWithClassData(
                bytes,
                downcallData,
                true,
                MethodHandles.Lookup.ClassOption.STRONG
            );
            return (T) hiddenClass.findConstructor(hiddenClass.lookupClass(), MethodType.methodType(void.class))
                .invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldSkip(Method method) {
        final Class<?> returnType = method.getReturnType();
        final boolean b =
            method.getDeclaredAnnotation(Skip.class) != null ||
                Modifier.isStatic(method.getModifiers()) ||
                method.isSynthetic() ||
                Modifier.isFinal(method.getModifiers());
        if (b) {
            return true;
        }

        final String methodName = method.getName();
        final Class<?>[] types = method.getParameterTypes();
        final int length = types.length;

        // check method declared by Object
        if (length == 0) {
            if (returnType == Class.class && "getClass".equals(methodName) ||
                returnType == int.class && "hashCode".equals(methodName) ||
                returnType == Object.class && "clone".equals(methodName) ||
                returnType == String.class && "toString".equals(methodName) ||
                returnType == void.class && "notify".equals(methodName) ||
                returnType == void.class && "notifyAll".equals(methodName) ||
                returnType == void.class && "wait".equals(methodName) ||
                returnType == void.class && "finalize".equals(methodName)  // TODO: no finalize in the future
            ) {
                return true;
            }
        } else if (
            returnType == boolean.class && length == 1 && types[0] == Object.class && "equals".equals(methodName) ||
                returnType == void.class && length == 1 && types[0] == long.class && "wait".equals(methodName) ||
                returnType == void.class && length == 2 && types[0] == long.class && types[1] == long.class && "wait".equals(methodName)
        ) {
            return true;
        }

        // check method declared by DirectAccess
        if (length == 0) {
            if (returnType == Map.class)
                return "functionDescriptors".equals(methodName) || "methodHandles".equals(methodName);
            if (returnType == SymbolLookup.class) return "symbolLookup".equals(methodName);
        }
        if (returnType == MethodHandle.class && length == 1 && types[0] == String.class) {
            return "methodHandle".equals(methodName);
        }
        return false;
    }

    private static String createExceptionString(Method method) {
        return method.getReturnType().getCanonicalName() + " " +
            method.getDeclaringClass().getCanonicalName() + "." + method.getName() +
            Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static void verifyMethods(List<Method> list, Map<Method, String> exceptionStringMap) {
        for (Method method : list) {// check method return type
            final Class<?> returnType = method.getReturnType();
            if (Struct.class.isAssignableFrom(returnType)) {
                boolean foundAllocator = false;
                for (Field field : returnType.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == StructAllocator.class) {
                        foundAllocator = true;
                        break;
                    }
                }
                if (!foundAllocator) {
                    throw new IllegalStateException("The struct " + returnType + " must contain one public static field that is StructAllocator");
                }
            } else if (!isValidReturnType(returnType)) {
                throw new IllegalStateException("Invalid return type: " + exceptionStringMap.get(method));
            }

            // check method parameter
            final Class<?>[] types = method.getParameterTypes();
            final boolean isFirstArena = types.length > 0 && Arena.class.isAssignableFrom(types[0]);
            for (Parameter parameter : method.getParameters()) {
                final Class<?> type = parameter.getType();
                if (Upcall.class.isAssignableFrom(type) && !isFirstArena) {
                    throw new IllegalStateException("The first parameter of method " + method + " is not an arena; however, the parameter " + parameter + " is an upcall");
                } else if (!isValidParamType(type)) {
                    throw new IllegalStateException("Invalid parameter: " + parameter + " in " + method);
                }
            }
        }
    }

    private static boolean isValidParamArrayType(Class<?> aClass) {
        if (!aClass.isArray()) return false;
        final Class<?> type = aClass.getComponentType();
        return type.isPrimitive() ||
            type == MemorySegment.class ||
            type == String.class ||
            Addressable.class.isAssignableFrom(type) ||
            Upcall.class.isAssignableFrom(type) ||
            CEnum.class.isAssignableFrom(type);
    }

    private static boolean isValidReturnArrayType(Class<?> aClass) {
        if (!aClass.isArray()) return false;
        final Class<?> type = aClass.getComponentType();
        return type.isPrimitive() ||
            type == MemorySegment.class ||
            type == String.class;
    }

    private static boolean isValidParamType(Class<?> aClass) {
        return aClass.isPrimitive() ||
            aClass == MemorySegment.class ||
            aClass == String.class ||
            SegmentAllocator.class.isAssignableFrom(aClass) ||
            Addressable.class.isAssignableFrom(aClass) ||
            Upcall.class.isAssignableFrom(aClass) ||
            CEnum.class.isAssignableFrom(aClass) ||
            isValidParamArrayType(aClass);
    }

    private static boolean isValidReturnType(Class<?> aClass) {
        return aClass.isPrimitive() ||
            aClass == MemorySegment.class ||
            aClass == String.class ||
            Struct.class.isAssignableFrom(aClass) ||
            CEnum.class.isAssignableFrom(aClass) ||
            isValidReturnArrayType(aClass) ||
            aClass == MethodHandle.class;
    }

    private static DowncallData generateData(
        Map<Method, DowncallMethodData> methodDataMap,
        SymbolLookup lookup,
        Map<String, FunctionDescriptor> descriptorMap,
        UnaryOperator<MethodHandle> transform) {
        final Map<String, FunctionDescriptor> descriptorMap1 = HashMap.newHashMap(methodDataMap.size());
        final Map<String, MethodHandle> map = HashMap.newHashMap(methodDataMap.size());

        for (var entry : methodDataMap.entrySet()) {
            Method method = entry.getKey();
            DowncallMethodData methodData = entry.getValue();
            final String entrypoint = methodData.entrypoint();
            final Optional<MemorySegment> optional = lookup.find(entrypoint);

            // function descriptor
            final FunctionDescriptor descriptor;
            final FunctionDescriptor get = descriptorMap.get(entrypoint);
            if (get != null) {
                descriptor = get;
            } else {
                final var returnType = method.getReturnType();
                final boolean returnVoid = returnType == void.class;
                final boolean methodByValue = method.getDeclaredAnnotation(ByValue.class) != null;

                // return layout
                final MemoryLayout retLayout;
                if (!returnVoid) {
                    final Convert convert = method.getDeclaredAnnotation(Convert.class);
                    if (convert != null && returnType == boolean.class) {
                        retLayout = convert.value().layout();
                    } else if (returnType.isPrimitive()) {
                        retLayout = getValueLayout(returnType);
                    } else {
                        final SizedSeg sizedSeg = method.getDeclaredAnnotation(SizedSeg.class);
                        final Sized sized = method.getDeclaredAnnotation(Sized.class);
                        final boolean isSizedSeg = sizedSeg != null;
                        final boolean isSized = sized != null;
                        if (Struct.class.isAssignableFrom(returnType)) {
                            final var structAllocatorField = getStructAllocatorField(returnType);
                            Objects.requireNonNull(structAllocatorField);
                            final StructLayout structLayout;
                            try {
                                structLayout = ((StructAllocator<?>) structAllocatorField.get(null)).layout();
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                            if (methodByValue) {
                                retLayout = structLayout;
                            } else {
                                final MemoryLayout targetLayout;
                                if (isSizedSeg) {
                                    targetLayout = MemoryLayout.sequenceLayout(sizedSeg.value(), structLayout);
                                } else if (isSized) {
                                    targetLayout = MemoryLayout.sequenceLayout(sized.value(), structLayout);
                                } else {
                                    targetLayout = structLayout;
                                }
                                retLayout = ValueLayout.ADDRESS.withTargetLayout(targetLayout);
                            }
                        } else {
                            final ValueLayout valueLayout = getValueLayout(returnType);
                            if ((valueLayout instanceof AddressLayout addressLayout) && (isSizedSeg || isSized)) {
                                if (isSizedSeg) {
                                    retLayout = addressLayout.withTargetLayout(MemoryLayout.sequenceLayout(sizedSeg.value(),
                                        ValueLayout.JAVA_BYTE));
                                } else {
                                    retLayout = addressLayout.withTargetLayout(MemoryLayout.sequenceLayout(sized.value(),
                                        returnType.isArray() ? getValueLayout(returnType.getComponentType()) : ValueLayout.JAVA_BYTE));
                                }
                            } else {
                                retLayout = valueLayout;
                            }
                        }
                    }
                } else {
                    retLayout = null;
                }

                // argument layouts
                final var parameters = methodData.parameters();
                final boolean skipFirstParam = methodData.skipFirstParam();
                final int size = skipFirstParam || methodByValue ?
                    parameters.size() - 1 :
                    parameters.size();
                final MemoryLayout[] argLayouts = new MemoryLayout[size];
                for (int i = 0; i < size; i++) {
                    final Parameter parameter = parameters.get(skipFirstParam ? i + 1 : i);
                    final Class<?> type = parameter.getType();
                    final Convert convert = parameter.getDeclaredAnnotation(Convert.class);
                    if (convert != null && type == boolean.class) {
                        argLayouts[i] = convert.value().layout();
                    } else {
                        argLayouts[i] = getValueLayout(type);
                    }
                }

                descriptor = returnVoid ? FunctionDescriptor.ofVoid(argLayouts) : FunctionDescriptor.of(retLayout, argLayouts);
            }

            descriptorMap1.put(entrypoint, descriptor);

            if (optional.isPresent()) {
                // linker options
                final Linker.Option[] options;
                final Critical critical = method.getDeclaredAnnotation(Critical.class);
                if (critical != null) {
                    options = critical.allowHeapAccess() ? OPTION_CRITICAL_TRUE : OPTION_CRITICAL_FALSE;
                } else {
                    options = NO_OPTION;
                }

                if (!map.containsKey(entrypoint)) {
                    map.put(entrypoint, transform.apply(LINKER.downcallHandle(optional.get(), descriptor, options)));
                }
            } else if (method.isDefault()) {
                map.putIfAbsent(entrypoint, null);
            } else {
                throw new UnsatisfiedLinkError("unresolved symbol: " + entrypoint + " (" + descriptor + "): " + methodData.exceptionString());
            }
        }
        return new DowncallData(Collections.unmodifiableMap(descriptorMap1), Collections.unmodifiableMap(map), lookup);
    }

    private static Field getStructAllocatorField(Class<?> aClass) {
        for (Field field : aClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == StructAllocator.class) {
                return field;
            }
        }
        return null;
    }
}
