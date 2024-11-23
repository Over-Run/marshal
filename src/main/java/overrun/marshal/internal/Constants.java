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

import overrun.marshal.DirectAccessData;

import java.lang.constant.*;
import java.lang.foreign.StructLayout;

import static java.lang.constant.ConstantDescs.*;

/**
 * Constant descriptors
 *
 * @author squid233
 * @since 0.1.0
 */
public final class Constants {
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
     * CD_DirectAccessData
     */
    public static final ClassDesc CD_DirectAccessData = ClassDesc.of("overrun.marshal.DirectAccessData");
    /// CD_DowncallFactory
    public static final ClassDesc CD_DowncallFactory = ClassDesc.of("overrun.marshal.DowncallFactory");
    /// CD_DowncallMethodType
    public static final ClassDesc CD_DowncallMethodType = ClassDesc.of("overrun.marshal.gen.DowncallMethodType");
    /// CD_DowncallMethodParameter
    public static final ClassDesc CD_DowncallMethodParameter = ClassDesc.of("overrun.marshal.gen.DowncallMethodParameter");
    /// CD_ConvertedClassType
    public static final ClassDesc CD_ConvertedClassType = ClassDesc.of("overrun.marshal.gen.ConvertedClassType");
    /**
     * CD_Marshal
     */
    public static final ClassDesc CD_Marshal = ClassDesc.of("overrun.marshal.Marshal");
    /**
     * CD_MemoryLayout$PathElement
     */
    public static final ClassDesc CD_MemoryLayout$PathElement = ClassDesc.of("java.lang.foreign.MemoryLayout$PathElement");
    /**
     * CD_MemorySegment
     */
    public static final ClassDesc CD_MemorySegment = ClassDesc.of("java.lang.foreign.MemorySegment");
    /**
     * CD_MemoryStack
     */
    public static final ClassDesc CD_MemoryStack = ClassDesc.of("io.github.overrun.memstack.MemoryStack");
    /**
     * CD_SegmentAllocator
     */
    public static final ClassDesc CD_SegmentAllocator = ClassDesc.of("java.lang.foreign.SegmentAllocator");
    /**
     * CD_StandardCharsets
     */
    public static final ClassDesc CD_StandardCharsets = ClassDesc.of("java.nio.charset.StandardCharsets");
    /**
     * CD_Struct
     */
    public static final ClassDesc CD_Struct = ClassDesc.of("overrun.marshal.struct.Struct");
    /**
     * CD_StructLayout
     */
    public static final ClassDesc CD_StructLayout = ClassDesc.of("java.lang.foreign.StructLayout");
    /**
     * CD_Unmarshal
     */
    public static final ClassDesc CD_Unmarshal = ClassDesc.of("overrun.marshal.Unmarshal");
    /**
     * CD_Upcall
     */
    public static final ClassDesc CD_Upcall = ClassDesc.of("overrun.marshal.Upcall");

    /**
     * MTD_char_boolean
     */
    public static final MethodTypeDesc MTD_char_boolean = MethodTypeDesc.of(CD_char, CD_boolean);
    /**
     * MTD_byte_boolean
     */
    public static final MethodTypeDesc MTD_byte_boolean = MethodTypeDesc.of(CD_byte, CD_boolean);
    /**
     * MTD_short_boolean
     */
    public static final MethodTypeDesc MTD_short_boolean = MethodTypeDesc.of(CD_short, CD_boolean);
    /**
     * MTD_int_boolean
     */
    public static final MethodTypeDesc MTD_int_boolean = MethodTypeDesc.of(CD_int, CD_boolean);
    /**
     * MTD_long_boolean
     */
    public static final MethodTypeDesc MTD_long_boolean = MethodTypeDesc.of(CD_long, CD_boolean);
    /**
     * MTD_float_boolean
     */
    public static final MethodTypeDesc MTD_float_boolean = MethodTypeDesc.of(CD_float, CD_boolean);
    /**
     * MTD_double_boolean
     */
    public static final MethodTypeDesc MTD_double_boolean = MethodTypeDesc.of(CD_double, CD_boolean);
    /**
     * MTD_Charset_String
     */
    public static final MethodTypeDesc MTD_Charset_String = MethodTypeDesc.of(CD_Charset, CD_String);
    /**
     * MTD_DirectAccessData
     */
    public static final MethodTypeDesc MTD_DirectAccessData = MethodTypeDesc.of(CD_DirectAccessData);
    /**
     * MTD_long
     */
    public static final MethodTypeDesc MTD_long = MethodTypeDesc.of(CD_long);
    /**
     * MTD_long_long_long
     */
    public static final MethodTypeDesc MTD_long_long_long = MethodTypeDesc.of(CD_long, CD_long, CD_long);
    /**
     * MTD_MemoryLayout$PathElement
     */
    public static final MethodTypeDesc MTD_MemoryLayout$PathElement = MethodTypeDesc.of(CD_MemoryLayout$PathElement);
    /**
     * MTD_MemoryLayout$PathElement_String
     */
    public static final MethodTypeDesc MTD_MemoryLayout$PathElement_String = MethodTypeDesc.of(CD_MemoryLayout$PathElement, CD_String);
    /**
     * MTD_MemorySegment
     */
    public static final MethodTypeDesc MTD_MemorySegment = MethodTypeDesc.of(CD_MemorySegment);
    /**
     * MTD_MemorySegment_Arena_Upcall
     */
    public static final MethodTypeDesc MTD_MemorySegment_Arena_Upcall = MethodTypeDesc.of(CD_MemorySegment, CD_Arena, CD_Upcall);
    /**
     * MTD_MemorySegment_Arena_UpcallArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_Arena_UpcallArray = MethodTypeDesc.of(CD_MemorySegment, CD_Arena, CD_Upcall.arrayType());
    /**
     * MTD_MemorySegment_long_long_long
     */
    public static final MethodTypeDesc MTD_MemorySegment_long_long_long = MethodTypeDesc.of(CD_MemorySegment, CD_long, CD_long, CD_long);
    /**
     * MTD_MemorySegment_SegmentAllocator_booleanArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_booleanArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_boolean.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_charArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_charArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_char.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_byteArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_byteArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_byte.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_shortArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_shortArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_short.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_intArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_intArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_int.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_longArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_longArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_long.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_floatArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_floatArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_float.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_doubleArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_doubleArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_double.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_MemorySegmentArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_MemorySegmentArray = MethodTypeDesc.of(CD_MemorySegment, CD_SegmentAllocator, CD_MemorySegment.arrayType());
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
     * MTD_MemorySegment_SegmentAllocator_StringArray
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_StringArray = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String.arrayType());
    /**
     * MTD_MemorySegment_SegmentAllocator_StringArray_Charset
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_StringArray_Charset = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_String.arrayType(),
        CD_Charset);
    /**
     * MTD_MemorySegment_SegmentAllocator_Struct
     */
    public static final MethodTypeDesc MTD_MemorySegment_SegmentAllocator_StructArray = MethodTypeDesc.of(CD_MemorySegment,
        CD_SegmentAllocator,
        CD_Struct.arrayType());
    /**
     * MTD_MemorySegment_Struct
     */
    public static final MethodTypeDesc MTD_MemorySegment_Struct = MethodTypeDesc.of(CD_MemorySegment, CD_Struct);
    /**
     * MTD_MemoryStack
     */
    public static final MethodTypeDesc MTD_MemoryStack = MethodTypeDesc.of(CD_MemoryStack);
    /**
     * MTD_StructLayout
     */
    public static final MethodTypeDesc MTD_StructLayout = MethodTypeDesc.of(CD_StructLayout);
    /**
     * MTD_VarHandle_MemoryLayout$PathElementArray
     */
    public static final MethodTypeDesc MTD_VarHandle_MemoryLayout$PathElementArray = MethodTypeDesc.of(CD_VarHandle, CD_MemoryLayout$PathElement.arrayType());
    /**
     * MTD_void_long_int
     */
    public static final MethodTypeDesc MTD_void_long_int = MethodTypeDesc.of(CD_void, CD_long, CD_int);
    /**
     * MTD_void_MemorySegment_long
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_long = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_long);
    /**
     * MTD_void_MemorySegment_booleanArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_booleanArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_boolean.arrayType());
    /**
     * MTD_void_MemorySegment_charArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_charArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_char.arrayType());
    /**
     * MTD_void_MemorySegment_byteArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_byteArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_byte.arrayType());
    /**
     * MTD_void_MemorySegment_shortArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_shortArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_short.arrayType());
    /**
     * MTD_void_MemorySegment_intArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_intArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_int.arrayType());
    /**
     * MTD_void_MemorySegment_longArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_longArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_long.arrayType());
    /**
     * MTD_void_MemorySegment_floatArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_floatArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_float.arrayType());
    /**
     * MTD_void_MemorySegment_doubleArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_doubleArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_double.arrayType());
    /**
     * MTD_void_MemorySegment_MemorySegmentArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_MemorySegmentArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_MemorySegment.arrayType());
    /**
     * MTD_void_MemorySegment_StringArray
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_StringArray = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_String.arrayType());
    /**
     * MTD_void_MemorySegment_StringArray_Charset
     */
    public static final MethodTypeDesc MTD_void_MemorySegment_StringArray_Charset = MethodTypeDesc.of(CD_void, CD_MemorySegment, CD_String.arrayType(), CD_Charset);

    /// BSM_DowncallFactory_downcallCallSite
    public static final DirectMethodHandleDesc BSM_DowncallFactory_downcallCallSite = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_DowncallFactory,
        "downcallCallSite",
        MethodTypeDesc.of(CD_CallSite,
            CD_MethodHandles_Lookup,
            CD_String,
            CD_MethodType,
            CD_DirectAccessData,
            CD_DowncallMethodType,
            CD_boolean
        ));
    /// BSM_DowncallFactory_downcallHandle
    public static final DirectMethodHandleDesc BSM_DowncallFactory_downcallHandle = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_DowncallFactory,
        "downcallHandle",
        MethodTypeDesc.of(CD_MethodHandle, CD_MethodHandles_Lookup, CD_String, CD_Class, CD_DirectAccessData));
    /// BSM_DowncallFactory_createDowncallMethodParameter
    public static final DirectMethodHandleDesc BSM_DowncallFactory_createDowncallMethodParameter = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_DowncallFactory,
        "createDowncallMethodParameter",
        MethodTypeDesc.of(CD_DowncallMethodParameter,
            CD_MethodHandles_Lookup,
            CD_String,
            CD_Class,
            CD_ConvertedClassType,
            CD_boolean,
            CD_boolean,
            CD_long,
            CD_String,
            CD_String));
    /// BSM_DowncallFactory_createDowncallMethodType
    public static final DirectMethodHandleDesc BSM_DowncallFactory_createDowncallMethodType = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_DowncallFactory,
        "createDowncallMethodType",
        MethodTypeDesc.of(CD_DowncallMethodType,
            CD_MethodHandles_Lookup,
            CD_String,
            CD_Class,
            CD_ConvertedClassType,
            CD_boolean,
            CD_boolean,
            CD_boolean,
            CD_long,
            CD_String,
            CD_String,
            CD_Object.arrayType()));
    /// BSM_DowncallFactory_createConvertedClassType
    public static final DirectMethodHandleDesc BSM_DowncallFactory_createConvertedClassType = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_DowncallFactory,
        "createConvertedClassType",
        MethodTypeDesc.of(CD_ConvertedClassType,
            CD_MethodHandles_Lookup,
            CD_String,
            CD_Class,
            CD_Class,
            CD_Class));

    /**
     * DCD_classData_DowncallData
     */
    public static final DynamicConstantDesc<DirectAccessData> DCD_classData_DirectAccessData = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA, DEFAULT_NAME, CD_DirectAccessData);
    /**
     * DCD_classData_StructLayout
     */
    public static final DynamicConstantDesc<StructLayout> DCD_classData_StructLayout = DynamicConstantDesc.ofNamed(BSM_CLASS_DATA, DEFAULT_NAME, CD_StructLayout);

    private Constants() {
    }
}
