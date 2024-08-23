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

package overrun.marshal.internal;

import java.lang.constant.ClassDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.constant.ConstantDescs.*;

/**
 * Constant descriptors
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Constants {
    /**
     * CD_Addressable
     */
    public static final ClassDesc CD_Addressable = ClassDesc.of("overrun.marshal.Addressable");
    /**
     * CD_Arena
     */
    public static final ClassDesc CD_Arena = ClassDesc.of("java.lang.foreign.Arena");
    /**
     * CD_Charset
     */
    public static final ClassDesc CD_Charset = ClassDesc.of("java.nio.charset.Charset");
    /**
     * CD_Checks
     */
    public static final ClassDesc CD_Checks = ClassDesc.of("overrun.marshal.Checks");
    /**
     * CD_DowncallData
     */
    public static final ClassDesc CD_DowncallData = ClassDesc.of("overrun.marshal.internal.data.DowncallData");
    /**
     * CD_IllegalStateException
     */
    public static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");
    /**
     * CD_Marshal
     */
    public static final ClassDesc CD_Marshal = ClassDesc.of("overrun.marshal.Marshal");
    /**
     * CD_MemoryLayout_PathElement
     */
    public static final ClassDesc CD_MemoryLayout_PathElement = ClassDesc.of("java.lang.foreign.MemoryLayout$PathElement");
    /**
     * CD_MemorySegment
     */
    public static final ClassDesc CD_MemorySegment = ClassDesc.of("java.lang.foreign.MemorySegment");
    /**
     * CD_MemoryStack
     */
    @Deprecated
    public static final ClassDesc CD_MemoryStack = ClassDesc.of("overrun.marshal.MemoryStack");
    /**
     * CD_SegmentAllocator
     */
    public static final ClassDesc CD_SegmentAllocator = ClassDesc.of("java.lang.foreign.SegmentAllocator");
    /**
     * CD_StandardCharsets
     */
    public static final ClassDesc CD_StandardCharsets = ClassDesc.of("java.nio.charset.StandardCharsets");
    /**
     * CD_StructAllocator
     */
    public static final ClassDesc CD_StructAllocator = ClassDesc.of("overrun.marshal.struct.StructAllocator");
    /**
     * CD_StructLayout
     */
    public static final ClassDesc CD_StructLayout = ClassDesc.of("java.lang.foreign.StructLayout");
    /**
     * CD_SymbolLookup
     */
    public static final ClassDesc CD_SymbolLookup = ClassDesc.of("java.lang.foreign.SymbolLookup");
    /**
     * CD_Unmarshal
     */
    public static final ClassDesc CD_Unmarshal = ClassDesc.of("overrun.marshal.Unmarshal");
    /**
     * CD_Upcall
     */
    public static final ClassDesc CD_Upcall = ClassDesc.of("overrun.marshal.Upcall");
    /**
     * CD_StringArray
     */
    public static final ClassDesc CD_StringArray = CD_String.arrayType();

    /**
     * MTD_Charset_String
     */
    public static final MethodTypeDesc MTD_Charset_String = MethodTypeDesc.of(CD_Charset, CD_String);
    /**
     * MTD_long
     */
    public static final MethodTypeDesc MTD_long = MethodTypeDesc.of(CD_long);
    /**
     * MTD_long_long_long
     */
    public static final MethodTypeDesc MTD_long_long_long = MethodTypeDesc.of(CD_long, CD_long, CD_long);
    /**
     * MTD_Map
     */
    public static final MethodTypeDesc MTD_Map = MethodTypeDesc.of(CD_Map);
    /**
     * MTD_MemoryLayout_PathElement
     */
    public static final MethodTypeDesc MTD_MemoryLayout_PathElement = MethodTypeDesc.of(CD_MemoryLayout_PathElement);
    /**
     * MTD_MemoryLayout_PathElement_String
     */
    public static final MethodTypeDesc MTD_MemoryLayout_PathElement_String = MethodTypeDesc.of(CD_MemoryLayout_PathElement, CD_String);
    /**
     * MTD_MemorySegment
     */
    public static final MethodTypeDesc MTD_MemorySegment = MethodTypeDesc.of(CD_MemorySegment);
    /**
     * MTD_MemorySegment_Arena_Upcall
     */
    public static final MethodTypeDesc MTD_MemorySegment_Arena_Upcall = MethodTypeDesc.of(CD_MemorySegment, CD_Arena, CD_Upcall);
    /**
     * MTD_MemorySegment_long_long_long
     */
    public static final MethodTypeDesc MTD_MemorySegment_long_long_long = MethodTypeDesc.of(CD_MemorySegment, CD_long, CD_long, CD_long);
    /**
     * MTD_MemorySegment_SegmentAllocator_String
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_String = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String);
    /**
     * MTD_MemorySegment_SegmentAllocator_String_Charset
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_String_Charset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String,
        CD_Charset);
    /**
     * MTD_MemorySegment_SegmentAllocator_StringArray_Charset
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_StringArray_Charset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_StringArray,
        CD_Charset);
    /**
     * MTD_MemoryStack
     */
    public static final MethodTypeDesc MTD_MemoryStack = MethodTypeDesc.of(CD_MemoryStack);
    /**
     * MTD_Object_MemorySegment
     */
    public static final MethodTypeDesc MTD_Object_MemorySegment = MethodTypeDesc.of(CD_Object, CD_MemorySegment);
    /**
     * MTD_Object_Object
     */
    public static final MethodTypeDesc MTD_Object_Object = MethodTypeDesc.of(CD_Object, CD_Object);
    /**
     * MTD_String_MemorySegment
     */
    public static final MethodTypeDesc MTD_String_MemorySegment = MethodTypeDesc.of(CD_String, CD_MemorySegment);
    /**
     * MTD_String_MemorySegment_Charset
     */
    public static final MethodTypeDesc MTD_String_MemorySegment_Charset = MethodTypeDesc.of(CD_String, CD_MemorySegment, CD_Charset);
    /**
     * MTD_StringArray_MemorySegment
     */
    public static final MethodTypeDesc MTD_StringArray_MemorySegment = MethodTypeDesc.of(CD_StringArray, CD_MemorySegment);
    /**
     * MTD_StringArray_MemorySegment_Charset
     */
    public static final MethodTypeDesc MTD_StringArray_MemorySegment_Charset = MethodTypeDesc.of(CD_StringArray, CD_MemorySegment, CD_Charset);
    /**
     * MTD_StructLayout
     */
    public static final MethodTypeDesc MTD_StructLayout = MethodTypeDesc.of(CD_StructLayout);
    /**
     * MTD_SymbolLookup
     */
    public static final MethodTypeDesc MTD_SymbolLookup = MethodTypeDesc.of(CD_SymbolLookup);
    /**
     * MTD_VarHandle_MemoryLayout_PathElementArray
     */
    public static final MethodTypeDesc MTD_VarHandle_MemoryLayout_PathElementArray = MethodTypeDesc.of(CD_VarHandle, CD_MemoryLayout_PathElement.arrayType());
    /**
     * MTD_void_int_int
     */
    public static final MethodTypeDesc MTD_void_int_int = MethodTypeDesc.of(CD_void, CD_int, CD_int);
    /**
     * MTD_void_long
     */
    public static final MethodTypeDesc MTD_void_long = MethodTypeDesc.of(CD_void, CD_long);
    /**
     * MTD_void_MemorySegment_long
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_long = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_long);
    /**
     * MTD_void_MemorySegment_StringArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_StringArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_StringArray);
    /**
     * MTD_void_MemorySegment_StringArray_Charset
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_StringArray_Charset = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_StringArray, CD_Charset);
    /**
     * MTD_void_String_Throwable
     */
    public static final MethodTypeDesc MTD_void_String_Throwable = MethodTypeDesc.of(CD_void, CD_String, CD_Throwable);

    /**
     * DCD_classData_DowncallData
     */
    public static final DynamicConstantDesc<?> DCD_classData_DowncallData = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA, DEFAULT_NAME, CD_DowncallData);
    /**
     * DCD_classData_StructLayout
     */
    public static final DynamicConstantDesc<?> DCD_classData_StructLayout = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA, DEFAULT_NAME, CD_StructLayout);

    private Constants() {
    }
}
