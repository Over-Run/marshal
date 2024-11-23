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
import overrun.marshal.struct.ByValue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Supplier;
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
 * The generated class implements the target class, which is specified with
 * {@link DowncallOption#targetClass(Class) DowncallOption::targetClass}. If no target class is specified, the caller
 * class will be used.
 * <h2>Methods</h2>
 * The loader finds method from the target class and its superclasses.
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
 * <h3>Skipping</h3>
 * The loader skips the following type of method:
 * <ul>
 *     <li>{@link Skip @Skip} annotated method</li>
 *     <li>static, final, {@linkplain Method#isSynthetic() synthetic} or {@linkplain Method#isBridge() bridge} method</li>
 *     <li>methods in {@link Object} and {@link DirectAccess}</li>
 *     <li>methods in classes specified by {@link DowncallOption#skipClass(Class) DowncallOption::skipClass}</li>
 * </ul>
 * <h3>Annotations</h3>
 * See {@link Convert @Convert}, {@link Ref @Ref}, {@link Sized @Sized} and {@link StrCharset @StrCharset}.
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
 * You can {@linkplain ProcessorTypes#register(Class, ProcessorType) register} a type and your own processor
 * as builtin processors may require an additional processor.
 * <p>
 * For custom types, you must register them before
 * {@linkplain #load(MethodHandles.Lookup, SymbolLookup, DowncallOption...) loading},
 * and you should {@linkplain Processor#addProcessor(Processor) add} processors to subclasses of
 * {@link TypedCodeProcessor}, {@link TypeTransformer} and {@link HandleTransformer} (especially
 * {@link MarshalProcessor} and {@link ReturnValueTransformer}).
 * <p>
 * Builtin types are described in {@link ProcessorType}.
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
 * @see Convert
 * @see Critical
 * @see DirectAccess
 * @see Entrypoint
 * @see Processor
 * @see ProcessorType
 * @see Ref
 * @see Sized
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
     * Loads a class with the given library name and options using
     * {@link SymbolLookup#libraryLookup(String, Arena) SymbolLookup::libraryLookup}.
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

    private record SkipMethodSignature(String methodName, Class<?>[] parameterTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SkipMethodSignature that)) return false;
            return Objects.equals(methodName, that.methodName) && Objects.deepEquals(parameterTypes, that.parameterTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, Arrays.hashCode(parameterTypes));
        }
    }

    private static Map.Entry<byte[], DirectAccessData> buildBytecode(MethodHandles.Lookup caller, SymbolLookup lookup, DowncallOption... options) {
        Class<?> _targetClass = null, targetClass;
        Map<String, FunctionDescriptor> _descriptorMap = null, descriptorMap;
        UnaryOperator<MethodHandle> _transform = null, transform;
        Set<Class<?>> skipClasses = new HashSet<>();
        skipClasses.add(Object.class);
        skipClasses.add(DirectAccess.class);

        for (DowncallOption option : options) {
            switch (option) {
                case DowncallOptions.TargetClass(var aClass) -> _targetClass = aClass;
                case DowncallOptions.Descriptors(var map) -> _descriptorMap = map;
                case DowncallOptions.Transform(var operator) -> _transform = operator;
                case DowncallOptions.SkipClass(var clazz) -> skipClasses.add(clazz);
                case null -> throw new IllegalArgumentException("No null option in Downcall::load");
            }
        }

        final Class<?> lookupClass = caller.lookupClass();
        targetClass = _targetClass != null ? _targetClass : lookupClass;
        descriptorMap = _descriptorMap != null ? _descriptorMap : Map.of();
        transform = _transform != null ? _transform : UnaryOperator.identity();

        var skipSignatures = skipClasses.stream()
            .<SkipMethodSignature>mapMulti((aClass, consumer) -> {
                for (Method method : aClass.getDeclaredMethods()) {
                    consumer.accept(new SkipMethodSignature(method.getName(), method.getParameterTypes()));
                }
            })
            .toList();

        final List<Method> methodList = Arrays.stream(targetClass.getMethods())
            .filter(method -> !shouldSkip(skipSignatures, method))
            .toList();
        verifyMethods(methodList);

        //region method handles
        final Map<Method, DowncallMethodType> methodTypeMap = HashMap.newHashMap(methodList.size());
        for (Method method : methodList) {
            methodTypeMap.put(method, DowncallMethodType.of(method));
        }
        //endregion

        final var downcallData = generateData(methodTypeMap, lookup, descriptorMap, transform);

        final ClassFile cf = of();
        final ClassDesc cd_thisClass = ClassDesc.of(lookupClass.getPackageName(), DEFAULT_NAME);
        return Map.entry(cf.build(cd_thisClass, classBuilder -> {
            classBuilder.withFlags(ACC_FINAL | ACC_SUPER);

            // inherit
            final ClassDesc cd_targetClass = ClassDesc.ofDescriptor(targetClass.descriptorString());
            if (targetClass.isInterface()) {
                classBuilder.withInterfaceSymbols(cd_targetClass);
            } else {
                classBuilder.withSuperclass(cd_targetClass);
            }

            //region constructor
            classBuilder.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                // super
                codeBuilder -> codeBuilder
                    .aload(codeBuilder.receiverSlot())
                    .invokespecial(targetClass.isInterface() ? CD_Object : cd_targetClass,
                        INIT_NAME,
                        MTD_void)
                    .return_());
            //endregion

            //region methods
            for (var entry : methodTypeMap.entrySet()) {
                Method method = entry.getKey();
                DowncallMethodType downcallMethodType = entry.getValue();

                final String entrypoint = downcallMethodType.entrypoint();
                if (method.isDefault() &&
                    downcallData.methodHandle(entrypoint) == null) {
                    continue;
                }

                final String methodName = method.getName();
                final var returnType = method.getReturnType();
                final ClassDesc cd_returnType = ClassDesc.ofDescriptor(returnType.descriptorString());
                var parameters = downcallMethodType.parameters();
                final MethodTypeDesc mtd_method = MethodTypeDesc.of(cd_returnType,
                    Arrays.stream(method.getParameters())
                        .map(parameter -> ClassDesc.ofDescriptor(parameter.getType().descriptorString()))
                        .toList());

                classBuilder.withMethodBody(methodName,
                    mtd_method,
                    ACC_PUBLIC,
                    codeBuilder -> {
                        if (returnType == MethodHandle.class) {
                            // returns MethodHandle
                            codeBuilder.loadConstant(DynamicConstantDesc.ofNamed(BSM_DowncallFactory_downcallHandle,
                                entrypoint,
                                CD_MethodHandle,
                                DCD_classData_DirectAccessData)
                            ).areturn();
                            return;
                        }

                        //region body

                        final TypeKind returnTypeKind = TypeKind.from(cd_returnType);
                        AllocatorRequirement allocatorRequirement = downcallMethodType.allocatorRequirement();
                        boolean isFirstAllocator = !parameters.isEmpty() &&
                            SegmentAllocator.class.isAssignableFrom(parameters.getFirst().type().javaClass());
                        boolean hasMemoryStack = switch (allocatorRequirement) {
                            case NONE, ALLOCATOR, ARENA -> false;
                            case STACK -> !isFirstAllocator;
                        };

                        CheckProcessor.getInstance().process(codeBuilder, new CheckProcessor.Context(downcallMethodType));

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

                        // before invoke
                        int[] refSlotList = new int[parameters.size()];
                        Arrays.fill(refSlotList, -1);
                        BeforeInvokeProcessor.getInstance().process(codeBuilder,
                            new BeforeInvokeProcessor.Context(
                                downcallMethodType,
                                refSlotList,
                                allocatorSlot)
                        );

                        // invoke
                        List<ClassDesc> downcallClassDescList = new ArrayList<>();
                        for (int i = downcallMethodType.invocationStartParameter(), size = parameters.size(); i < size; i++) {
                            DowncallMethodParameter parameter = parameters.get(i);
                            ConvertedClassType type = parameter.type();
                            ProcessorType processorType = ProcessorTypes.fromClass(type.javaClass());
                            if (refSlotList[i] != -1) {
                                codeBuilder.aload(refSlotList[i]);
                            } else {
                                MarshalProcessor.getInstance().process(codeBuilder, processorType, new MarshalProcessor.Context(
                                    allocatorSlot,
                                    codeBuilder.parameterSlot(i),
                                    parameter.charset(),
                                    type.downcallClass()
                                ));
                            }
                            downcallClassDescList.add(ProcessorTypes.fromClass(type.downcallClass()).downcallClassDesc());
                        }
                        codeBuilder.invokedynamic(DynamicCallSiteDesc.of(BSM_DowncallFactory_downcallCallSite,
                            entrypoint,
                            MethodTypeDesc.of(cd_returnType, downcallClassDescList),
                            DCD_classData_DirectAccessData,
                            downcallMethodType.describeConstable().orElseThrow(),
                            hasMemoryStack ? TRUE : FALSE
                        ));
                        boolean returnVoid = returnType == void.class;
                        int resultSlot = returnVoid ? -1 : codeBuilder.allocateLocal(returnTypeKind);
                        if (!returnVoid) {
                            codeBuilder.storeLocal(returnTypeKind, resultSlot);
                        }

                        // after invoke
                        AfterInvokeProcessor.getInstance().process(codeBuilder,
                            new AfterInvokeProcessor.Context(downcallMethodType, refSlotList));

                        // return
                        if (!returnVoid) {
                            codeBuilder.loadLocal(returnTypeKind, resultSlot);
                        }
                        codeBuilder.return_(returnTypeKind);

                        //endregion
                    });
            }
            //endregion

            //region DirectAccess
            final boolean hasDirectAccess = DirectAccess.class.isAssignableFrom(targetClass);
            if (hasDirectAccess) {
                classBuilder.withMethodBody("directAccessData",
                    MTD_DirectAccessData,
                    ACC_PUBLIC,
                    codeBuilder -> codeBuilder
                        .loadConstant(DCD_classData_DirectAccessData)
                        .areturn());
            }
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

    private static boolean shouldSkip(List<SkipMethodSignature> skipMethodSignatures, Method method) {
        // 1. modifier
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) ||
            Modifier.isFinal(modifiers) ||
            method.isSynthetic() ||
            method.isBridge()) return true;
        // 2. annotation Skip
        if (method.getDeclaredAnnotation(Skip.class) != null) return true;
        // 3. skip class
        return skipMethodSignatures.contains(new SkipMethodSignature(method.getName(), method.getParameterTypes()));
    }

    private static String createSignatureString(Method method) {
        return method.getReturnType().getCanonicalName() + " " +
            method.getDeclaringClass().getCanonicalName() + "." + method.getName() +
            Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static void verifyMethods(List<Method> list) {
        for (Method method : list) {
            // check method annotation
            Sized sized = method.getDeclaredAnnotation(Sized.class);
            if (sized != null && sized.value() < 0) {
                throw new IllegalStateException("Invalid value of @Sized annotation: " + sized.value() + " in method " + createSignatureString(method));
            }

            // check method return type
            final Class<?> returnType = method.getReturnType();
            if (!isValidReturnType(returnType)) {
                throw new IllegalStateException("Invalid return type: " + createSignatureString(method));
            }

            // check method parameter
            final Class<?>[] types = method.getParameterTypes();
            for (Parameter parameter : method.getParameters()) {
                if (!isValidParameter(parameter)) {
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
                ProcessorType processorType = ProcessorTypes.fromClass(ConvertedClassType.parameterType(parameter).javaClass());
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
                    if (types.length == 0 || !SegmentAllocator.class.isAssignableFrom(types[0])) {
                        throw new IllegalStateException("A segment allocator is required by " + strictObject + ": " + createSignatureString(method));
                    }
                }
                case ARENA -> {
                    if (types.length == 0 || !Arena.class.isAssignableFrom(types[0])) {
                        throw new IllegalStateException("An arena is required by " + strictObject + ": " + createSignatureString(method));
                    }
                }
                default -> throw new IllegalStateException("Invalid allocator requirement: " + allocatorRequirement);
            }
        }
    }

    private static boolean isValidParameter(Parameter parameter) {
        if (ProcessorTypes.isRegistered(parameter.getType())) {
            Sized sized = parameter.getDeclaredAnnotation(Sized.class);
            return sized == null || sized.value() >= 0;
        }
        return false;
    }

    private static boolean isValidReturnType(Class<?> aClass) {
        return aClass == MethodHandle.class || ProcessorTypes.isRegisteredExactly(aClass);
    }

    private static DirectAccessData generateData(
        Map<Method, DowncallMethodType> methodTypeMap,
        SymbolLookup lookup,
        Map<String, FunctionDescriptor> descriptorMap,
        UnaryOperator<MethodHandle> transform) {
        final Map<String, FunctionDescriptor> descriptorMap1 = HashMap.newHashMap(methodTypeMap.size());
        final Map<String, Supplier<MethodHandle>> map = HashMap.newHashMap(methodTypeMap.size());

        for (var entry : methodTypeMap.entrySet()) {
            Method method = entry.getKey();
            DowncallMethodType downcallMethodType = entry.getValue();
            final String entrypoint = downcallMethodType.entrypoint();

            // function descriptor
            final FunctionDescriptor get = descriptorMap.get(entrypoint);
            final FunctionDescriptor descriptor;
            if (method.getReturnType() == MethodHandle.class) {
                descriptor = get;
            } else {
                descriptor = get != null ?
                    get :
                    DescriptorTransformer.getInstance().process(downcallMethodType);
            }
            descriptorMap1.put(entrypoint, descriptor);

            if (descriptor != null) {
                Supplier<MethodHandle> handle = () -> {
                    final Optional<MemorySegment> optional = lookup.find(entrypoint);
                    if (optional.isPresent()) {
                        // linker options
                        Linker.Option[] options;
                        if (downcallMethodType.critical()) {
                            options = downcallMethodType.criticalAllowHeapAccess() ? OPTION_CRITICAL_TRUE : OPTION_CRITICAL_FALSE;
                        } else {
                            options = NO_OPTION;
                        }

                        return transform.apply(LINKER.downcallHandle(optional.get(), descriptor, options));
                    } else {
                        MethodHandle apply = transform.apply(null);
                        if (apply != null || method.isDefault()) {
                            return apply;
                        }
                        throw new NoSuchElementException("Symbol not found: " + entrypoint + " (" + descriptor + "): " + downcallMethodType);
                    }
                };
                if (!map.containsKey(entrypoint) || map.get(entrypoint) == null) {
                    map.putIfAbsent(entrypoint, handle);
                }
            }
        }
        return new DirectAccessData(Collections.unmodifiableMap(descriptorMap1),
            s -> map.get(s).get(),
            lookup);
    }
}
