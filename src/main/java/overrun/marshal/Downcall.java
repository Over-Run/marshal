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
import overrun.marshal.gen.processor.*;
import overrun.marshal.internal.DowncallOptions;
import overrun.marshal.internal.StringCharset;
import overrun.marshal.internal.data.DowncallData;
import overrun.marshal.struct.ByValue;
import overrun.marshal.struct.Struct;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static overrun.marshal.internal.Constants.*;

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
 * <h2>Processor Types</h2>
 * Processor types are used to process builtin and custom types.
 * You can {@linkplain ProcessorTypes#register(Class, ProcessorType) register} a type
 * and {@linkplain BaseProcessor#addProcessor(Processor) add} your own processor.
 * <p>
 * For custom types, you must add processors to {@link MarshalProcessor} and {@link UnmarshalProcessor}.
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

    // type

    @Deprecated
    private static ValueLayout getValueLayout(Class<?> carrier) {
        if (carrier == boolean.class) return ValueLayout.JAVA_BOOLEAN;
        if (carrier == char.class) return ValueLayout.JAVA_CHAR;
        if (carrier == byte.class) return ValueLayout.JAVA_BYTE;
        if (carrier == short.class) return ValueLayout.JAVA_SHORT;
        if (carrier == int.class) return ValueLayout.JAVA_INT;
        if (carrier == long.class) return ValueLayout.JAVA_LONG;
        if (carrier == float.class) return ValueLayout.JAVA_FLOAT;
        if (carrier == double.class) return ValueLayout.JAVA_DOUBLE;
        return ValueLayout.ADDRESS;
    }

    private static Map.Entry<byte[], DowncallData> buildBytecode(MethodHandles.Lookup caller, SymbolLookup lookup, DowncallOption... options) {
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
        final Map<Method, String> signatureStringMap = methodList.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), Downcall::createSignatureString));
        verifyMethods(methodList, signatureStringMap);

        final ClassFile cf = of();
        final ClassDesc cd_thisClass = ClassDesc.of(lookupClass.getPackageName(), DEFAULT_NAME);
        final Map<Method, DowncallMethodData> methodDataMap = HashMap.newHashMap(methodList.size());
        Set<String> methodEntrypointSet = new HashSet<>();

        //region method handles
        for (Method method : methodList) {
            final String entrypoint = getMethodEntrypoint(method);
            final var parameters = List.of(method.getParameters());

            boolean byValue = method.getDeclaredAnnotation(ByValue.class) != null;
            AllocatorRequirement allocatorRequirement = parameters.stream()
                .reduce(byValue ? AllocatorRequirement.ALLOCATOR : AllocatorRequirement.NONE,
                    (requirement, parameter) -> AllocatorRequirement.stricter(
                        requirement,
                        ProcessorTypes.fromClass(parameter.getType()).allocationRequirement()
                    ), AllocatorRequirement::stricter);
            final DowncallMethodData methodData = new DowncallMethodData(
                entrypoint,
                signatureStringMap.get(method),
                parameters,
                !byValue &&
                    !parameters.isEmpty() &&
                    allocatorRequirement != AllocatorRequirement.NONE &&
                    SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType()),
                allocatorRequirement
            );
            methodDataMap.put(method, methodData);
            methodEntrypointSet.add(entrypoint);
        }
        //endregion

        final DowncallData downcallData = generateData(methodDataMap, lookup, descriptorMap, transform);

        return Map.entry(cf.build(cd_thisClass, classBuilder -> {
            classBuilder.withFlags(ACC_FINAL | ACC_SUPER);

            // inherit
            final ClassDesc cd_targetClass = targetClass.describeConstable().orElseThrow();
            if (targetClass.isInterface()) {
                classBuilder.withInterfaceSymbols(cd_targetClass);
            } else {
                classBuilder.withSuperclass(cd_targetClass);
            }

            //region method handles
            for (String entrypoint : methodEntrypointSet) {
                if (downcallData.handleMap().get(entrypoint) != null) {
                    classBuilder.withField(entrypoint,
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

                classBuilder.withMethodBody(methodName,
                    mtd_method,
                    Modifier.isPublic(modifiers) ? ACC_PUBLIC : ACC_PROTECTED,
                    codeBuilder -> {
                        final String entrypoint = methodData.entrypoint();
                        final TypeKind returnTypeKind = TypeKind.from(cd_returnType);

                        // returns MethodHandle
                        if (returnType == MethodHandle.class) {
                            if (method.isDefault() &&
                                downcallData.handleMap().get(entrypoint) == null) {
                                // invoke super interface
                                invokeSuperMethod(codeBuilder, parameters);
                                codeBuilder.invokespecial(cd_targetClass,
                                        methodName,
                                        mtd_method,
                                        targetClass.isInterface())
                                    .areturn();
                            } else {
                                codeBuilder.getstatic(cd_thisClass, entrypoint, CD_MethodHandle)
                                    .areturn();
                            }
                            return;
                        }

                        if (method.isDefault() &&
                            downcallData.handleMap().get(entrypoint) == null) {
                            // invoke super interface
                            invokeSuperMethod(codeBuilder, parameters);
                            codeBuilder.invokespecial(cd_targetClass,
                                methodName,
                                mtd_method,
                                targetClass.isInterface()
                            ).return_(returnTypeKind);
                            return;
                        }

                        //region body

                        AllocatorRequirement allocatorRequirement = methodData.allocatorRequirement();
                        boolean isFirstAllocator = !parameters.isEmpty() &&
                            SegmentAllocator.class.isAssignableFrom(parameters.getFirst().getType());
                        boolean hasMemoryStack = switch (allocatorRequirement) {
                            case NONE, ALLOCATOR, ARENA -> false;
                            case STACK -> !isFirstAllocator;
                        };

                        CheckProcessor.getInstance().process(new CheckProcessor.Context(codeBuilder, parameters));

                        int allocatorSlot;
                        if (isFirstAllocator) {
                            allocatorSlot = codeBuilder.parameterSlot(0);
                        } else if (hasMemoryStack) {
                            allocatorSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                            codeBuilder.invokestatic(CD_MemoryStack,
                                "pushLocal",
                                MTD_MemoryStack,
                                true
                            ).astore(allocatorSlot);
                        } else {
                            allocatorSlot = -1;
                        }

                        codeBuilder.trying(
                            blockCodeBuilder -> {
                                // before invoke
                                Map<Parameter, Integer> refSlotMap = new HashMap<>();
                                BeforeInvokeProcessor.getInstance().process(
                                    new BeforeInvokeProcessor.Context(blockCodeBuilder,
                                        parameters,
                                        refSlotMap,
                                        allocatorSlot)
                                );

                                // invoke
                                blockCodeBuilder.getstatic(cd_thisClass, entrypoint, CD_MethodHandle);
                                List<ClassDesc> downcallClassDescList = new ArrayList<>();
                                for (int i = methodData.skipFirstParam() ? 1 : 0, size = parameters.size(); i < size; i++) {
                                    Parameter parameter = parameters.get(i);
                                    ProcessorType processorType = ProcessorTypes.fromParameter(parameter);
                                    if (refSlotMap.containsKey(parameter)) {
                                        blockCodeBuilder.aload(refSlotMap.get(parameter));
                                    } else {
                                        MarshalProcessor marshalProcessor = MarshalProcessor.getInstance();
                                        MarshalProcessor.Context context = new MarshalProcessor.Context(blockCodeBuilder,
                                            processorType,
                                            StringCharset.getCharset(parameter),
                                            blockCodeBuilder.parameterSlot(i),
                                            allocatorSlot);
                                        marshalProcessor.checkProcessed(marshalProcessor.process(context), context);
                                    }
                                    downcallClassDescList.add(processorType.downcallClassDesc());
                                }
                                ProcessorType returnProcessorType = ProcessorTypes.fromMethod(method);
                                ClassDesc cd_returnDowncall = returnProcessorType.downcallClassDesc();
                                TypeKind returnDowncallTypeKind = TypeKind.from(cd_returnDowncall);
                                blockCodeBuilder.invokevirtual(CD_MethodHandle,
                                    "invokeExact",
                                    MethodTypeDesc.of(cd_returnDowncall, downcallClassDescList));
                                boolean returnVoid = returnType == void.class;
                                int resultSlot = returnVoid ? -1 : blockCodeBuilder.allocateLocal(returnDowncallTypeKind);
                                if (!returnVoid) {
                                    blockCodeBuilder.storeLocal(returnDowncallTypeKind, resultSlot);
                                }

                                // after invoke
                                AfterInvokeProcessor.getInstance().process(new AfterInvokeProcessor.Context(blockCodeBuilder, parameters, refSlotMap));

                                // before return
                                BeforeReturnProcessor.getInstance().process(new BeforeReturnProcessor.Context(blockCodeBuilder, hasMemoryStack));

                                // return
                                UnmarshalProcessor unmarshalProcessor = UnmarshalProcessor.getInstance();
                                UnmarshalProcessor.Context returnContext = new UnmarshalProcessor.Context(blockCodeBuilder,
                                    returnProcessorType,
                                    returnType,
                                    StringCharset.getCharset(method),
                                    resultSlot);
                                unmarshalProcessor.checkProcessed(unmarshalProcessor.process(returnContext), returnContext);
                                blockCodeBuilder.return_(returnTypeKind);
                            },
                            catchBuilder -> catchBuilder.catching(CD_Throwable, blockCodeBuilder -> {
                                BeforeReturnProcessor.getInstance().process(new BeforeReturnProcessor.Context(blockCodeBuilder, hasMemoryStack));
                                // rethrow the exception
                                int slot = blockCodeBuilder.allocateLocal(TypeKind.ReferenceType);
                                blockCodeBuilder.astore(slot)
                                    .new_(CD_IllegalStateException)
                                    .dup()
                                    .ldc(methodData.signatureString())
                                    .aload(slot)
                                    .invokespecial(CD_IllegalStateException, INIT_NAME, MTD_void_String_Throwable)
                                    .athrow();
                            }));

                        //endregion
                    });
            }
            //endregion

            //region DirectAccess
            final boolean hasDirectAccess = DirectAccess.class.isAssignableFrom(targetClass);
            if (hasDirectAccess) {
                classBuilder.withMethodBody("functionDescriptors",
                    MTD_Map,
                    ACC_PUBLIC,
                    codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "descriptorMap", MTD_Map)
                        .areturn());
                classBuilder.withMethodBody("methodHandles",
                    MTD_Map,
                    ACC_PUBLIC,
                    codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "handleMap", MTD_Map)
                        .areturn());
                classBuilder.withMethodBody("symbolLookup",
                    MTD_SymbolLookup,
                    ACC_PUBLIC,
                    codeBuilder -> codeBuilder
                        .ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "symbolLookup", MTD_SymbolLookup)
                        .areturn());
            }
            //endregion

            //region class initializer
            classBuilder.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC,
                codeBuilder -> {
                    final int handleMapSlot = codeBuilder.allocateLocal(TypeKind.ReferenceType);
                    codeBuilder.ldc(DCD_classData_DowncallData)
                        .invokevirtual(CD_DowncallData, "handleMap", MTD_Map)
                        .astore(handleMapSlot);

                    // method handles
                    for (String entrypoint : methodEntrypointSet) {
                        if (downcallData.handleMap().get(entrypoint) != null) {
                            codeBuilder
                                .aload(handleMapSlot)
                                .ldc(entrypoint)
                                .invokeinterface(CD_Map, "get", MTD_Object_Object)
                                .checkcast(CD_MethodHandle)
                                .putstatic(cd_thisClass, entrypoint, CD_MethodHandle);
                        }
                    }

                    codeBuilder.return_();
                });
            //endregion
        }), downcallData);
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadBytecode(MethodHandles.Lookup caller, SymbolLookup lookup, DowncallOption... options) {
        var built = buildBytecode(caller, lookup, options);

        try {
            final MethodHandles.Lookup hiddenClass = caller.defineHiddenClassWithClassData(
                built.getKey(),
                built.getValue(),
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
        if (method.getDeclaredAnnotation(Skip.class) != null ||
            Modifier.isStatic(method.getModifiers()) ||
            method.isSynthetic() ||
            Modifier.isFinal(method.getModifiers())) {
            return true;
        }

        final String methodName = method.getName();
        final Class<?>[] types = method.getParameterTypes();
        final int length = types.length;

        // check method declared by Object or DirectAccess
        return switch (length) {
            case 0 -> switch (methodName) {
                // Object
                case "getClass" -> returnType == Class.class;
                case "hashCode" -> returnType == int.class;
                case "clone" -> returnType == Object.class;
                case "toString" -> returnType == String.class;
                case "notify", "notifyAll", "wait" -> returnType == void.class;
                case "finalize" -> returnType == void.class; // TODO: no finalize in the future
                // DirectAccess
                case "functionDescriptors", "methodHandles" -> returnType == Map.class;
                case "symbolLookup" -> returnType == SymbolLookup.class;
                default -> false;
            };
            case 1 -> switch (methodName) {
                // Object
                case "equals" -> returnType == boolean.class && types[0] == Object.class;
                case "wait" -> returnType == void.class && types[0] == long.class;
                // DirectAccess
                case "methodHandle" -> returnType == MethodHandle.class && types[0] == String.class;
                default -> false;
            };
            case 2 -> // Object
                returnType == void.class && types[0] == long.class && types[1] == long.class && "wait".equals(methodName);
            default -> false;
        };
    }

    private static String createSignatureString(Method method) {
        return method.getReturnType().getCanonicalName() + " " +
            method.getDeclaringClass().getCanonicalName() + "." + method.getName() +
            Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static void verifyMethods(List<Method> list, Map<Method, String> signatureStringMap) {
        for (Method method : list) {
            String signature = signatureStringMap.get(method);

            // check method return type
            final Class<?> returnType = method.getReturnType();
            if (!isValidReturnType(returnType)) {
                throw new IllegalStateException("Invalid return type: " + signature);
            }

            // check method parameter
            final Class<?>[] types = method.getParameterTypes();
            final boolean isFirstAllocator = types.length > 0 && SegmentAllocator.class.isAssignableFrom(types[0]);
            final boolean isFirstArena = types.length > 0 && Arena.class.isAssignableFrom(types[0]);
            for (Parameter parameter : method.getParameters()) {
                final Class<?> type = parameter.getType();
                if (!isValidParamType(type)) {
                    throw new IllegalStateException("Invalid parameter: " + parameter + " in " + method);
                }
            }

            // check allocator requirement
            boolean byValue = method.getDeclaredAnnotation(ByValue.class) != null;
            AllocatorRequirement allocatorRequirement = byValue ?
                AllocatorRequirement.ALLOCATOR :
                AllocatorRequirement.NONE;
            Object strictObject = byValue ? "annotation @ByValue" : null;
            for (Parameter parameter : method.getParameters()) {
                final Class<?> type = parameter.getType();
                ProcessorType processorType = ProcessorTypes.fromClass(type);
                AllocatorRequirement previous = allocatorRequirement;
                allocatorRequirement = AllocatorRequirement.stricter(allocatorRequirement, processorType.allocationRequirement());
                if (allocatorRequirement != previous) {
                    strictObject = parameter;
                }
            }
            switch (allocatorRequirement) {
                case NONE, STACK -> {
                }
                case ALLOCATOR -> {
                    if (!isFirstAllocator) {
                        throw new IllegalStateException("A segment allocator is required by " + strictObject + ": " + signature);
                    }
                }
                case ARENA -> {
                    if (!isFirstArena) {
                        throw new IllegalStateException("An arena is required by " + strictObject + ": " + signature);
                    }
                }
                default -> throw new IllegalStateException("Invalid allocator requirement: " + allocatorRequirement);
            }

            // check Sized annotation
            boolean sized = method.getDeclaredAnnotation(Sized.class) != null;
            boolean sizedSeg = method.getDeclaredAnnotation(SizedSeg.class) != null;
            if (sized && sizedSeg) {
                throw new IllegalStateException("Cannot be annotated with both @Sized and @SizedSeg: " + signature);
            }
            if (sized) {
                if (!returnType.isArray()) {
                    throw new IllegalStateException("Return type annotated with @Sized must be an array: " + signature);
                }
            } else if (sizedSeg) {
                if (returnType != MemorySegment.class && !Struct.class.isAssignableFrom(returnType)) {
                    throw new IllegalStateException("Return type annotated with @SizedSeg must be MemorySegment or Struct: " + signature);
                }
            }
        }
    }

    private static boolean isValidParamType(Class<?> aClass) {
        return ProcessorTypes.isRegistered(aClass);
    }

    private static boolean isValidReturnType(Class<?> aClass) {
        return aClass == MethodHandle.class || ProcessorTypes.isRegisteredExactly(aClass);
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
                            final StructLayout structLayout = ((ProcessorType.Struct) ProcessorTypes.fromClass(returnType)).checkAllocator().layout();
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
                final boolean skipFirstParam = methodData.skipFirstParam() || methodByValue;
                final int size = skipFirstParam ?
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

            final Optional<MemorySegment> optional = lookup.find(entrypoint);
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
                throw new NoSuchElementException("Symbol not found: " + entrypoint + " (" + descriptor + "): " + methodData.signatureString());
            }
        }
        return new DowncallData(Collections.unmodifiableMap(descriptorMap1),
            Collections.unmodifiableMap(map),
            lookup);
    }
}
