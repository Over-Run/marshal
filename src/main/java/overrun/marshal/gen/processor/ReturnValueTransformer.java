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

package overrun.marshal.gen.processor;

import overrun.marshal.Unmarshal;
import overrun.marshal.Upcall;
import overrun.marshal.struct.Struct;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class ReturnValueTransformer extends HandleTransformer<ReturnValueTransformer.Context> {
    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    private static final MethodHandles.Lookup privateLookup = MethodHandles.lookup();
    private static final MethodHandle
        MH_unmarshalAsStringArray,
        MH_unmarshalAsStringArrayCharset,
        MH_unmarshalAsBooleanArray,
        MH_unmarshalAsCharArray,
        MH_unmarshalAsByteArray,
        MH_unmarshalAsShortArray,
        MH_unmarshalAsIntArray,
        MH_unmarshalAsLongArray,
        MH_unmarshalAsFloatArray,
        MH_unmarshalAsDoubleArray,
        MH_unmarshalAsAddressArray,
        MH_unmarshalCharAsBoolean,
        MH_unmarshalByteAsBoolean,
        MH_unmarshalShortAsBoolean,
        MH_unmarshalIntAsBoolean,
        MH_unmarshalLongAsBoolean,
        MH_unmarshalFloatAsBoolean,
        MH_unmarshalDoubleAsBoolean,
        MH_unboundString,
        MH_unboundStringCharset,
        MH_unmarshalStruct,
        MH_unmarshalUpcall;

    static {
        try {
            MH_unmarshalAsStringArray = lookup.findStatic(Unmarshal.class, "unmarshalAsStringArray", MethodType.methodType(String[].class, MemorySegment.class));
            MH_unmarshalAsStringArrayCharset = lookup.findStatic(Unmarshal.class, "unmarshalAsStringArray", MethodType.methodType(String[].class, MemorySegment.class, Charset.class));
            MH_unmarshalAsBooleanArray = lookup.findStatic(Unmarshal.class, "unmarshalAsBooleanArray", MethodType.methodType(boolean[].class, MemorySegment.class));
            MH_unmarshalAsCharArray = lookup.findStatic(Unmarshal.class, "unmarshalAsCharArray", MethodType.methodType(char[].class, MemorySegment.class));
            MH_unmarshalAsByteArray = lookup.findStatic(Unmarshal.class, "unmarshalAsByteArray", MethodType.methodType(byte[].class, MemorySegment.class));
            MH_unmarshalAsShortArray = lookup.findStatic(Unmarshal.class, "unmarshalAsShortArray", MethodType.methodType(short[].class, MemorySegment.class));
            MH_unmarshalAsIntArray = lookup.findStatic(Unmarshal.class, "unmarshalAsIntArray", MethodType.methodType(int[].class, MemorySegment.class));
            MH_unmarshalAsLongArray = lookup.findStatic(Unmarshal.class, "unmarshalAsLongArray", MethodType.methodType(long[].class, MemorySegment.class));
            MH_unmarshalAsFloatArray = lookup.findStatic(Unmarshal.class, "unmarshalAsFloatArray", MethodType.methodType(float[].class, MemorySegment.class));
            MH_unmarshalAsDoubleArray = lookup.findStatic(Unmarshal.class, "unmarshalAsDoubleArray", MethodType.methodType(double[].class, MemorySegment.class));
            MH_unmarshalAsAddressArray = lookup.findStatic(Unmarshal.class, "unmarshalAsAddressArray", MethodType.methodType(MemorySegment[].class, MemorySegment.class));
            MH_unmarshalCharAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, char.class));
            MH_unmarshalByteAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, byte.class));
            MH_unmarshalShortAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, short.class));
            MH_unmarshalIntAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, int.class));
            MH_unmarshalLongAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, long.class));
            MH_unmarshalFloatAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, float.class));
            MH_unmarshalDoubleAsBoolean = lookup.findStatic(Unmarshal.class, "unmarshalAsBoolean", MethodType.methodType(boolean.class, double.class));
            MH_unboundString = lookup.findStatic(Unmarshal.class, "unboundString", MethodType.methodType(String.class, MemorySegment.class));
            MH_unboundStringCharset = lookup.findStatic(Unmarshal.class, "unboundString", MethodType.methodType(String.class, MemorySegment.class, Charset.class));
            MH_unmarshalStruct = privateLookup.findStatic(ReturnValueTransformer.class, "unmarshalStruct", MethodType.methodType(Struct.class, Class.class, MemorySegment.class));
            MH_unmarshalUpcall = privateLookup.findStatic(ReturnValueTransformer.class, "unmarshalUpcall", MethodType.methodType(Upcall.class, Class.class, MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public record Context(
        Class<?> returnType,
        String charset
    ) {
    }

    @Override
    public MethodHandle process(MethodHandle originalHandle, Context context) {
        return switch (ProcessorTypes.fromClass(context.returnType)) {
            case ProcessorType.Allocator _, ProcessorType.Custom _ -> super.process(originalHandle, context);
            case ProcessorType.Void _ -> originalHandle;
            case ProcessorType.Array array -> switch (array.componentType()) {
                case ProcessorType.Allocator _, ProcessorType.Array _,
                     ProcessorType.Custom _, ProcessorType.Struct _, ProcessorType.Upcall<?> _ ->
                    super.process(originalHandle, context);
                case ProcessorType.BoolConvert _ -> throw new UnsupportedOperationException();//todo
                case ProcessorType.Void _ -> throw new AssertionError("should not reach here");
                case ProcessorType.Str _ -> MethodHandles.filterReturnValue(originalHandle,
                    context.charset != null ?
                        MethodHandles.insertArguments(MH_unmarshalAsStringArrayCharset, 1, Charset.forName(context.charset)) :
                        MH_unmarshalAsStringArray);
                case ProcessorType.Value value -> MethodHandles.filterReturnValue(originalHandle,
                    switch (value) {
                        case BOOLEAN -> MH_unmarshalAsBooleanArray;
                        case CHAR -> MH_unmarshalAsCharArray;
                        case BYTE -> MH_unmarshalAsByteArray;
                        case SHORT -> MH_unmarshalAsShortArray;
                        case INT -> MH_unmarshalAsIntArray;
                        case LONG -> MH_unmarshalAsLongArray;
                        case FLOAT -> MH_unmarshalAsFloatArray;
                        case DOUBLE -> MH_unmarshalAsDoubleArray;
                        case ADDRESS -> MH_unmarshalAsAddressArray;
                    });
            };
            case ProcessorType.Str _ -> MethodHandles.filterReturnValue(originalHandle,
                context.charset != null ?
                    MethodHandles.insertArguments(MH_unboundStringCharset, 1, Charset.forName(context.charset)) :
                    MH_unboundString);
            case ProcessorType.Struct _ -> MethodHandles.filterReturnValue(originalHandle,
                MH_unmarshalStruct.asType(MH_unmarshalStruct.type().changeReturnType(context.returnType)).bindTo(context.returnType));
            case ProcessorType.Upcall<?> _ -> MethodHandles.filterReturnValue(originalHandle,
                MH_unmarshalUpcall.asType(MH_unmarshalUpcall.type().changeReturnType(context.returnType)).bindTo(context.returnType));
            case ProcessorType.Value value -> {
                if (value == ProcessorType.Value.BOOLEAN) {
                    Class<?> originalReturnType = originalHandle.type().returnType();
                    if (originalReturnType == char.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalCharAsBoolean);
                    if (originalReturnType == byte.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalByteAsBoolean);
                    if (originalReturnType == short.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalShortAsBoolean);
                    if (originalReturnType == int.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalIntAsBoolean);
                    if (originalReturnType == long.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalLongAsBoolean);
                    if (originalReturnType == float.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalFloatAsBoolean);
                    if (originalReturnType == double.class)
                        yield MethodHandles.filterReturnValue(originalHandle, MH_unmarshalDoubleAsBoolean);
                    yield originalHandle;
                }
                yield originalHandle;
            }
            case ProcessorType.BoolConvert _ -> throw new UnsupportedOperationException();//todo
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Struct<T>> T unmarshalStruct(Class<?> type, MemorySegment segment) {
        return (T) ((ProcessorType.Struct) ProcessorTypes.fromClass(type)).checkAllocator().of(segment);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Upcall> T unmarshalUpcall(Class<?> type, MemorySegment segment) {
        return ((ProcessorType.Upcall<T>) ProcessorTypes.fromClass(type)).checkFactory().create(segment);
    }

    public static ReturnValueTransformer getInstance() {
        final class Holder {
            private static final ReturnValueTransformer INSTANCE = new ReturnValueTransformer();
        }
        return Holder.INSTANCE;
    }
}
