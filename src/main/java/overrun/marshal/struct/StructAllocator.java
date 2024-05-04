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

package overrun.marshal.struct;

import overrun.marshal.LayoutBuilder;

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.constant.ConstantDescs.*;
import static overrun.marshal.internal.Constants.*;

/**
 * C structure allocator.
 * <h2>Struct implementation</h2>
 * The methods in {@link Struct} are always implemented at runtime,
 * even if the caller does not extend {@link Struct}.
 * <h2>Defining structure layout</h2>
 * Use {@link StructLayout}, or {@link LayoutBuilder} for automatic alignment.
 * <h2>Accessors</h2>
 * <h3>Values</h3>
 * <i>Values</i> are these kinds of objects:
 * {@code byte}, {@code short},
 * {@code int}, {@code long},
 * {@code float}, {@code double},
 * {@code boolean}, {@code char}
 * and {@link MemorySegment}.
 * <p>
 * Values can be accessed with a normal getter and setter:
 * <pre>{@code
 * interface Point {
 *     ...(LayoutBuilder.struct().cInt("x"));
 *     int x();
 *     Point x(int val); // an optional setter. without a setter, a member is immutable
 * }
 * }</pre>
 * The method name of the getter and setter <strong>MUST</strong> be same as the name defined in the struct layout.
 * <p>
 * You cannot use a Java reserved-word as a name.
 * <h3>Sized-array</h3>
 * Sized-arrays are defined with {@link MemoryLayout#sequenceLayout(long, MemoryLayout) MemoryLayout::sequenceLayout}
 * or {@link LayoutBuilder#cArray(String, long, MemoryLayout) LayoutBuilder::cArray}.
 * The element layout must be a value layout or sequence layout.
 * <p>
 * Nested arrays are supported.
 * <pre>{@code
 * interface S
 *     (LayoutBuilder.struct()
 *                   .cArray("arr", 2L, JAVA_INT)
 *                   .cArray("nestArr", 2L, sequenceLayout(2L, JAVA_INT)))
 * int arr(long index);
 * S arr(long index, int val);
 * int nestArr(long index0, long index1);
 * S nestArr(long index0, long index1, int val);
 * }</pre>
 * <h3>Nested structures</h3>
 * Append '$' before the name of the member to access a nested structure.
 * <pre>{@code
 * struct P { int x; int y; }
 * interface S {
 *     (LayoutBuilder.struct()
 *                   .cStruct("point", P.layout)
 *                   .cArray("pointArr", 2L, P.layout))
 *     int point$x();
 *     S point$x(int val);
 *     int point$y();
 *     int pointArr$x(long index);
 *     S pointArr$x(long index, int val);
 *     int pointArr$y(long index);
 * }
 * }</pre>
 * <h2>Slices</h2>
 * Use {@code slice} to access a struct array.
 * <pre>{@code
 * interface S {
 *     S slice(long index, long count);
 *     S slice(long index);
 * }
 * }</pre>
 * <h2>Example</h2>
 * The constructor requires a lookup object with <strong>full privilege access</strong>.
 * <p>
 * Use {@link MethodHandles#lookup()} to obtain it.
 * <pre>{@code
 * interface Point {
 *     StructAllocator<Point> OF = new StructAllocator(
 *         MethodHandles.lookup(),
 *         LayoutBuilder.struct()
 *                      .cInt("x")
 *                      .cInt("y")
 *                      .build()
 *     );
 *
 *     int x();
 *     Point x(int val);
 *
 *     int y();
 *     Point y(int val);
 * }
 * }</pre>
 *
 * @param <T> the target type
 * @author squid233
 * @see Struct
 * @see LayoutBuilder
 * @since 0.1.0
 */
@SuppressWarnings("preview")
public final class StructAllocator<T> {
    private final MethodHandle constructor;
    private final StructLayout layout;

    /**
     * the constructor
     *
     * @param lookup the lookup object with <strong>full privilege access</strong>
     * @param layout the struct layout
     */
    public StructAllocator(MethodHandles.Lookup lookup, StructLayout layout) {
        this.layout = layout;
        final byte[] bytes = buildBytecode(lookup, layout);
        try {
            final MethodHandles.Lookup hiddenClass = lookup.defineHiddenClassWithClassData(bytes, layout, true);
            this.constructor = hiddenClass.findConstructor(hiddenClass.lookupClass(), MethodType.methodType(void.class, MemorySegment.class, long.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] buildBytecode(MethodHandles.Lookup lookup, StructLayout layout) {
        final Class<?> caller = lookup.lookupClass();
        final ClassDesc cd_caller = caller.describeConstable().orElseThrow();
        final ClassDesc cd_thisClass = ClassDesc.of(caller.getPackageName(), DEFAULT_NAME);
        return ClassFile.of().build(cd_thisClass, classBuilder -> {
            classBuilder.withFlags(AccessFlag.FINAL, AccessFlag.SUPER);
            if (caller.isInterface()) {
                classBuilder.withInterfaceSymbols(cd_caller);
            } else {
                classBuilder.withSuperclass(cd_caller);
            }

            // fields
            classBuilder.withField("_segment", CD_MemorySegment, Modifier.PRIVATE | Modifier.FINAL);
            classBuilder.withField("_count", CD_long, Modifier.PRIVATE | Modifier.FINAL);

            // constructor
            classBuilder.withMethodBody(INIT_NAME, MTD_void_MemorySegment_long, Modifier.PUBLIC, codeBuilder -> {
                // super
                codeBuilder.aload(codeBuilder.receiverSlot())
                    .invokespecial(caller.isInterface() ? CD_Object : cd_caller, INIT_NAME, MTD_void);
                // _segment
                codeBuilder.aload(codeBuilder.receiverSlot())
                    .aload(codeBuilder.parameterSlot(0))
                    .putfield(cd_thisClass, "_segment", CD_MemorySegment);
                // _count
                codeBuilder.aload(codeBuilder.receiverSlot())
                    .lload(codeBuilder.parameterSlot(1))
                    .putfield(cd_thisClass, "_count", CD_long);
                codeBuilder.return_();
            });

            // Struct implementation
            classBuilder.withMethodBody("layout", MTD_StructLayout, Modifier.PUBLIC, codeBuilder -> codeBuilder
                .ldc(DCD_classData_StructLayout)
                .areturn());
            classBuilder.withMethodBody("segment", MTD_MemorySegment, Modifier.PUBLIC, codeBuilder -> codeBuilder
                .aload(codeBuilder.receiverSlot())
                .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                .areturn());
            classBuilder.withMethodBody("elementCount", MTD_long, Modifier.PUBLIC, codeBuilder -> codeBuilder
                .aload(codeBuilder.receiverSlot())
                .getfield(cd_thisClass, "_count", CD_long)
                .lreturn());

            // slice methods
            Class<?> sliceLongLongClass = Struct.class;
            try {
                sliceLongLongClass = caller.getDeclaredMethod("slice", long.class, long.class).getReturnType();
            } catch (NoSuchMethodException _) {
            }
            Class<?> sliceLongClass = Struct.class;
            try {
                sliceLongClass = caller.getDeclaredMethod("slice", long.class).getReturnType();
            } catch (NoSuchMethodException _) {
            }
            classBuilder.withMethodBody("slice", MethodTypeDesc.of(sliceLongLongClass.describeConstable().orElseThrow(), CD_long, CD_long), Modifier.PUBLIC, codeBuilder -> codeBuilder
                .new_(cd_thisClass)
                .dup()
                .aload(codeBuilder.receiverSlot())
                .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                .ldc(DCD_classData_StructLayout)
                .lconst_0()
                .lload(codeBuilder.parameterSlot(0))
                .invokeinterface(CD_StructLayout, "scale", MTD_long_long_long)
                .ldc(DCD_classData_StructLayout)
                .lconst_0()
                .lload(codeBuilder.parameterSlot(1))
                .invokeinterface(CD_StructLayout, "scale", MTD_long_long_long)
                .ldc(DCD_classData_StructLayout)
                .invokeinterface(CD_StructLayout, "byteAlignment", MTD_long)
                .invokeinterface(CD_MemorySegment, "asSlice", MTD_MemorySegment_long_long_long)
                .lload(codeBuilder.parameterSlot(1))
                .invokespecial(cd_thisClass, INIT_NAME, MTD_void_MemorySegment_long)
                .areturn());
            classBuilder.withMethodBody("slice", MethodTypeDesc.of(sliceLongClass.describeConstable().orElseThrow(), CD_long), Modifier.PUBLIC, codeBuilder -> codeBuilder
                .new_(cd_thisClass)
                .dup()
                .aload(codeBuilder.receiverSlot())
                .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                .ldc(DCD_classData_StructLayout)
                .lconst_0()
                .lload(codeBuilder.parameterSlot(0))
                .invokeinterface(CD_StructLayout, "scale", MTD_long_long_long)
                .ldc(DCD_classData_StructLayout)
                .invokeinterface(CD_StructLayout, "byteSize", MTD_long)
                .ldc(DCD_classData_StructLayout)
                .invokeinterface(CD_StructLayout, "byteAlignment", MTD_long)
                .invokeinterface(CD_MemorySegment, "asSlice", MTD_MemorySegment_long_long_long)
                .lconst_1()
                .invokespecial(cd_thisClass, INIT_NAME, MTD_void_MemorySegment_long)
                .areturn());

            // members
            for (MemoryLayout memberLayout : layout.memberLayouts()) {
                switch (memberLayout) {
                    case GroupLayout _, SequenceLayout _ -> {
                        final String name = memberLayout.name().orElseThrow();
                        final var results = findValueLayout(memberLayout);

                        for (FindResult result : results) {
                            final String methodName = STR."\{name}\{result.appendName()}";
                            final int indexCount = result.indexCount();
                            final ClassDesc returnDesc = ClassDesc.ofDescriptor(result.layout().carrier().descriptorString());
                            final TypeKind returnKind = TypeKind.from(returnDesc);

                            // var handle
                            final String vhName = STR."_VH_\{methodName}";
                            classBuilder.withField(vhName, CD_VarHandle, Modifier.PRIVATE | Modifier.FINAL | Modifier.STATIC);

                            // getter
                            final var getterParams = makeClassDescriptors(list -> {
                                for (int i = 0; i < indexCount; i++) {
                                    list.add(CD_long);
                                }
                            });
                            classBuilder.withMethodBody(methodName, MethodTypeDesc.of(returnDesc, getterParams), Modifier.PUBLIC, codeBuilder -> {
                                codeBuilder.getstatic(cd_thisClass, vhName, CD_VarHandle)
                                    .aload(codeBuilder.receiverSlot())
                                    .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                                    .lconst_0();
                                for (int i = 0; i < indexCount; i++) {
                                    codeBuilder.lload(codeBuilder.parameterSlot(i));
                                }
                                codeBuilder.invokevirtual(CD_VarHandle, "get", MethodTypeDesc.of(returnDesc, makeClassDescriptors(list -> {
                                        list.add(CD_MemorySegment);
                                        list.add(CD_long);
                                        list.addAll(getterParams);
                                    })))
                                    .returnInstruction(returnKind);
                            });

                            // setter
                            final List<ClassDesc> setterParams = makeClassDescriptors(list -> {
                                for (int i = 0; i < indexCount; i++) {
                                    list.add(CD_long);
                                }
                                list.add(returnDesc);
                            });
                            classBuilder.withMethodBody(methodName, MethodTypeDesc.of(cd_caller, setterParams), Modifier.PUBLIC, codeBuilder -> {
                                codeBuilder.getstatic(cd_thisClass, vhName, CD_VarHandle)
                                    .aload(codeBuilder.receiverSlot())
                                    .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                                    .lconst_0();
                                for (int i = 0; i < indexCount; i++) {
                                    codeBuilder.lload(codeBuilder.parameterSlot(i));
                                }
                                codeBuilder.loadInstruction(returnKind, codeBuilder.parameterSlot(indexCount))
                                    .invokevirtual(CD_VarHandle, "set", MethodTypeDesc.of(CD_void, makeClassDescriptors(list -> {
                                        list.add(CD_MemorySegment);
                                        list.add(CD_long);
                                        list.addAll(setterParams);
                                    })))
                                    .aload(codeBuilder.receiverSlot())
                                    .areturn();
                            });
                        }
                    }
                    case ValueLayout valueLayout -> {
                        final String name = valueLayout.name().orElseThrow();
                        final ClassDesc returnDesc = ClassDesc.ofDescriptor(valueLayout.carrier().descriptorString());
                        final TypeKind returnKind = TypeKind.from(returnDesc);

                        // var handle
                        final String vhName = STR."_VH_\{name}";
                        classBuilder.withField(vhName, CD_VarHandle, Modifier.PRIVATE | Modifier.FINAL | Modifier.STATIC);

                        // getter
                        classBuilder.withMethodBody(name, MethodTypeDesc.of(returnDesc), Modifier.PUBLIC, codeBuilder -> codeBuilder
                            .getstatic(cd_thisClass, vhName, CD_VarHandle)
                            .aload(codeBuilder.receiverSlot())
                            .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                            .lconst_0()
                            .invokevirtual(CD_VarHandle, "get", MethodTypeDesc.of(returnDesc, CD_MemorySegment, CD_long))
                            .returnInstruction(returnKind));

                        // setter
                        classBuilder.withMethodBody(name, MethodTypeDesc.of(cd_caller, returnDesc), Modifier.PUBLIC, codeBuilder -> {
                            codeBuilder.getstatic(cd_thisClass, vhName, CD_VarHandle)
                                .aload(codeBuilder.receiverSlot())
                                .getfield(cd_thisClass, "_segment", CD_MemorySegment)
                                .lconst_0()
                                .loadInstruction(returnKind, codeBuilder.parameterSlot(0))
                                .invokevirtual(CD_VarHandle, "set", MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_long, returnDesc));
                            codeBuilder.aload(codeBuilder.receiverSlot())
                                .areturn();
                        });
                    }
                    case PaddingLayout _ -> {
                    }
                }
            }

            // class initializer
            classBuilder.withMethodBody(CLASS_INIT_NAME, MTD_void, Modifier.STATIC, codeBuilder -> {
                for (MemoryLayout memberLayout : layout.memberLayouts()) {
                    switch (memberLayout) {
                        case GroupLayout _, SequenceLayout _ -> {
                            final String name = memberLayout.name().orElseThrow();
                            final var results = findValueLayout(memberLayout);

                            for (FindResult result : results) {
                                // var handle
                                codeBuilder.ldc(DCD_classData_StructLayout)
                                    .ldc(result.elems().size() + 1)
                                    .anewarray(CD_MemoryLayout_PathElement)
                                    .dup()
                                    .iconst_0()
                                    .ldc(name)
                                    .invokestatic(CD_MemoryLayout_PathElement, "groupElement", MTD_MemoryLayout_PathElement_String, true)
                                    .aastore();
                                int i = 0;
                                for (Elem elem : result.elems()) {
                                    codeBuilder.dup()
                                        .ldc(i + 1);
                                    switch (elem) {
                                        case GroupElem groupElem -> codeBuilder
                                            .ldc(groupElem.name)
                                            .invokestatic(CD_MemoryLayout_PathElement, "groupElement", MTD_MemoryLayout_PathElement_String, true);
                                        case SeqElem _ -> codeBuilder
                                            .invokestatic(CD_MemoryLayout_PathElement, "sequenceElement", MTD_MemoryLayout_PathElement, true);
                                    }
                                    codeBuilder.aastore();
                                    i++;
                                }
                                codeBuilder.invokeinterface(CD_StructLayout, "varHandle", MTD_VarHandle_MemoryLayout_PathElementArray)
                                    .putstatic(cd_thisClass, STR."_VH_\{name}\{result.appendName()}", CD_VarHandle);
                            }
                        }
                        case ValueLayout valueLayout -> {
                            final String name = valueLayout.name().orElseThrow();

                            // var handle
                            codeBuilder.ldc(DCD_classData_StructLayout)
                                .iconst_1()
                                .anewarray(CD_MemoryLayout_PathElement)
                                .dup()
                                .iconst_0()
                                .ldc(name)
                                .invokestatic(CD_MemoryLayout_PathElement, "groupElement", MTD_MemoryLayout_PathElement_String, true)
                                .aastore()
                                .invokeinterface(CD_StructLayout, "varHandle", MTD_VarHandle_MemoryLayout_PathElementArray)
                                .putstatic(cd_thisClass, STR."_VH_\{name}", CD_VarHandle);
                        }
                        case PaddingLayout _ -> {
                        }
                    }
                }
                codeBuilder.return_();
            });
        });
    }

    private sealed interface Elem {
    }

    private static final class SeqElem implements Elem {
        private static final SeqElem INSTANCE = new SeqElem();
    }

    private record GroupElem(String name) implements Elem {
    }

    private record FindResult(List<Elem> elems, ValueLayout layout) {
        private int indexCount() {
            int c = 0;
            for (Elem elem : elems) {
                if (elem instanceof SeqElem) c++;
            }
            return c;
        }

        public String appendName() {
            return elems.stream()
                .filter(elem -> elem instanceof GroupElem)
                .map(elem -> STR."$\{((GroupElem) elem).name}")
                .collect(Collectors.joining());
        }
    }

    private static List<FindResult> findValueLayout(MemoryLayout layout) {
        return findValueLayout(new ArrayList<>(), layout);
    }

    private static List<FindResult> findValueLayout(List<Elem> elems, MemoryLayout layout) {
        List<FindResult> results = new ArrayList<>();
        switch (layout) {
            case GroupLayout groupLayout -> {
                for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                    results.addAll(findValueLayout(append(elems, new GroupElem(memberLayout.name().orElseThrow())), memberLayout));
                }
            }
            case PaddingLayout _ -> {
            }
            case SequenceLayout sequenceLayout ->
                results.addAll(findValueLayout(append(elems, SeqElem.INSTANCE), sequenceLayout.elementLayout()));
            case ValueLayout valueLayout -> results.add(new FindResult(List.copyOf(elems), valueLayout));
        }
        return Collections.unmodifiableList(results);
    }

    private static List<Elem> append(List<Elem> list, Elem e) {
        var l = new ArrayList<>(list);
        l.add(e);
        return l;
    }

    private static List<ClassDesc> makeClassDescriptors(Consumer<List<ClassDesc>> consumer) {
        final List<ClassDesc> list = new ArrayList<>();
        consumer.accept(list);
        return list;
    }

    /**
     * Creates a struct with the given segment and count.
     *
     * @param segment the segment
     * @param count   the count
     * @return the instance of the struct
     */
    @SuppressWarnings("unchecked")
    public T of(MemorySegment segment, long count) {
        Objects.requireNonNull(segment);
        try {
            return (T) constructor.invoke(segment, count);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a struct with the given segment.
     *
     * @param segment the segment
     * @return the instance of the struct
     */
    public T of(MemorySegment segment) {
        return of(segment, Struct.estimateCount(segment, layout));
    }

    /**
     * Allocates a struct with the given allocator and count.
     *
     * @param allocator the allocator
     * @param count     the count
     * @return the instance of the struct
     */
    public T of(SegmentAllocator allocator, long count) {
        return of(allocator.allocate(layout, count), count);
    }

    /**
     * Allocates a struct with the given allocator.
     *
     * @param allocator the allocator
     * @return the instance of the struct
     */
    public T of(SegmentAllocator allocator) {
        return of(allocator.allocate(layout), 1L);
    }

    /**
     * {@return the instance of this struct modelling {@code NULL} address}.
     */
    public T nullptr() {
        return of(MemorySegment.NULL, 0L);
    }

    /**
     * {@return the layout of this struct}
     */
    public StructLayout layout() {
        return layout;
    }
}
