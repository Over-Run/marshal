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

package overrun.marshal.test.downcall;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import overrun.marshal.Downcall;
import overrun.marshal.DowncallOption;
import overrun.marshal.Unmarshal;
import overrun.marshal.gen.Ref;
import overrun.marshal.gen.Sized;
import overrun.marshal.gen.processor.*;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test simulates the removed {@code CEnum} interface.
 *
 * @author squid233
 * @since 0.1.0
 */
@SuppressWarnings("preview")
public class CEnumSimulateTest {
    private static final ClassDesc CD_MyEnum = ClassDesc.of("overrun.marshal.test.downcall.CEnumSimulateTest$MyEnum");
    private static Functions instance;

    enum MyEnum {
        A, B, C;

        // from int
        static MyEnum byValue(int value) {
            return switch (value) {
                case 0 -> A;
                case 1 -> B;
                case 2 -> C;
                default -> throw new IllegalStateException("Unexpected value: " + value);
            };
        }

        // to int
        int value() {
            return ordinal();
        }

        static MemorySegment marshal(SegmentAllocator allocator, MyEnum[] values) {
            if (values == null) {
                return MemorySegment.NULL;
            }
            MemorySegment segment = allocator.allocate(ValueLayout.JAVA_INT, values.length);
            for (int i = 0; i < values.length; i++) {
                MyEnum anEnum = values[i];
                segment.setAtIndex(ValueLayout.JAVA_INT, i, anEnum != null ? anEnum.value() : 0);
            }
            return segment;
        }

        static MyEnum[] unmarshal(MemorySegment segment) {
            return Unmarshal.unmarshal(ValueLayout.JAVA_INT, segment, MyEnum[]::new, segment1 -> MyEnum.byValue(segment1.get(ValueLayout.JAVA_INT, 0)));
        }

        static void copy(MemorySegment src, MyEnum[] dst) {
            if (Unmarshal.isNullPointer(src) || dst == null) return;
            for (int i = 0; i < dst.length; i++) {
                dst[i] = MyEnum.byValue(src.getAtIndex(ValueLayout.JAVA_INT, i));
            }
        }

        /**
         * processor type definition
         */
        static class Type implements ProcessorType.Custom {
            public static final Type INSTANCE = new Type();

            @Override
            public ClassDesc downcallClassDesc() {
                return ConstantDescs.CD_int;
            }

            @Override
            public MemoryLayout downcallLayout() {
                return ValueLayout.JAVA_INT;
            }

            @Override
            public AllocatorRequirement allocationRequirement() {
                return AllocatorRequirement.NONE;
            }
        }
    }

    /**
     * functions to be tested
     */
    interface Functions {
        int func(MyEnum myEnum);

        MyEnum funcReturn(int value);

        @Sized(3)
        MyEnum[] funcReturnArray();

        int funcArray(MyEnum... values);

        void funcRef(@Ref MyEnum[] values);

        /**
         * implementation (simulates C functions)
         */
        class Impl {
            static final MemorySegment returnArray = Arena.global().allocateFrom(ValueLayout.JAVA_INT, 2, 1, 0);

            static int func(int myEnum) {
                return myEnum * 2;
            }

            static int funcReturn(int value) {
                return value;
            }

            static MemorySegment funcReturnArray() {
                return returnArray;
            }

            static int funcArray(MemorySegment values) {
                MemorySegment segment = values.reinterpret(ValueLayout.JAVA_INT.scale(0, 3));
                return segment.get(ValueLayout.JAVA_INT, 0) + segment.get(ValueLayout.JAVA_INT, 4) + segment.get(ValueLayout.JAVA_INT, 8);
            }

            static void funcRef(MemorySegment values) {
                MemorySegment segment = values.reinterpret(ValueLayout.JAVA_INT.scale(0, 3));
                segment.set(ValueLayout.JAVA_INT, 0, 2);
                segment.set(ValueLayout.JAVA_INT, 4, 1);
                segment.set(ValueLayout.JAVA_INT, 8, 0);
            }
        }
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        Linker linker = Linker.nativeLinker();
        MethodHandles.Lookup handles = MethodHandles.lookup();
        Arena arena = Arena.ofAuto();

        MemorySegment _func = linker.upcallStub(handles.findStatic(Functions.Impl.class, "func", MethodType.methodType(int.class, int.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), arena);
        MemorySegment _funcReturn = linker.upcallStub(handles.findStatic(Functions.Impl.class, "funcReturn", MethodType.methodType(int.class, int.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), arena);
        MemorySegment _funcReturnArray = linker.upcallStub(handles.findStatic(Functions.Impl.class, "funcReturnArray", MethodType.methodType(MemorySegment.class)), FunctionDescriptor.of(ValueLayout.ADDRESS), arena);
        MemorySegment _funcArray = linker.upcallStub(handles.findStatic(Functions.Impl.class, "funcArray", MethodType.methodType(int.class, MemorySegment.class)), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS), arena);
        MemorySegment _funcRef = linker.upcallStub(handles.findStatic(Functions.Impl.class, "funcRef", MethodType.methodType(void.class, MemorySegment.class)), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), arena);

        SymbolLookup lookup = name -> switch (name) {
            case "func" -> Optional.of(_func);
            case "funcReturn" -> Optional.of(_funcReturn);
            case "funcReturnArray" -> Optional.of(_funcReturnArray);
            case "funcArray" -> Optional.of(_funcArray);
            case "funcRef" -> Optional.of(_funcRef);
            default -> Optional.empty();
        };

        // Must register processor types before generating bytecode
        ProcessorTypes.register(MyEnum.class, MyEnum.Type.INSTANCE);
        MarshalProcessor.getInstance().addProcessor(new TypedCodeProcessor<>() {
            @Override
            public boolean process(CodeBuilder builder, ProcessorType type, MarshalProcessor.Context context) {
                return switch (type) {
                    case MyEnum.Type _ -> {
                        builder.aload(context.variableSlot())
                            .invokevirtual(CD_MyEnum,
                                "value",
                                MethodTypeDesc.of(ConstantDescs.CD_int));
                        yield true;
                    }
                    case ProcessorType.Array array -> switch (array.componentType()) {
                        case MyEnum.Type _ -> {
                            builder.aload(context.allocatorSlot())
                                .aload(context.variableSlot())
                                .invokestatic(CD_MyEnum,
                                    "marshal",
                                    MethodTypeDesc.of(MemorySegment.class.describeConstable().orElseThrow(),
                                        SegmentAllocator.class.describeConstable().orElseThrow(),
                                        CD_MyEnum.arrayType()));
                            yield true;
                        }
                        default -> false;
                    };
                    default -> false;
                };
            }
        });
        ReturnValueTransformer.getInstance().addProcessor(new ReturnValueTransformer() {
            @Override
            public MethodHandle process(Context context) {
                try {
                    return switch (context.processorType()) {
                        case MyEnum.Type _ -> MethodHandles.filterReturnValue(context.originalHandle(),
                            MethodHandles.lookup().findStatic(MyEnum.class, "byValue", MethodType.methodType(MyEnum.class, int.class)));
                        case ProcessorType.Array array -> switch (array.componentType()) {
                            case MyEnum.Type _ -> MethodHandles.filterReturnValue(context.originalHandle(),
                                MethodHandles.lookup().findStatic(MyEnum.class, "unmarshal", MethodType.methodType(MyEnum[].class, MemorySegment.class)));
                            default -> null;
                        };
                        default -> null;
                    };
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        RefCopyProcessor.getInstance().addProcessor(new TypedCodeProcessor<>() {
            @Override
            public boolean process(CodeBuilder builder, ProcessorType type, RefCopyProcessor.Context context) {
                if (!(type instanceof ProcessorType.Array array)) {
                    return false;
                }
                if (array.componentType() instanceof MyEnum.Type) {
                    builder.aload(context.srcSegmentSlot())
                        .aload(context.dstArraySlot())
                        .invokestatic(CD_MyEnum,
                            "copy",
                            MethodTypeDesc.of(ConstantDescs.CD_void,
                                MemorySegment.class.describeConstable().orElseThrow(),
                                CD_MyEnum.arrayType()));
                    return true;
                }
                return false;
            }
        });

        instance = Downcall.load(handles, lookup, DowncallOption.targetClass(Functions.class));
    }

    @Test
    void testCEnumValue() {
        assertEquals(0, MyEnum.A.value());
        assertEquals(1, MyEnum.B.value());
        assertEquals(2, MyEnum.C.value());
    }

    @Test
    void testCEnumParam() {
        assertEquals(0, instance.func(MyEnum.A));
        assertEquals(2, instance.func(MyEnum.B));
        assertEquals(4, instance.func(MyEnum.C));
    }

    @Test
    void testCEnumReturn() {
        assertEquals(MyEnum.A, instance.funcReturn(0));
        assertEquals(MyEnum.B, instance.funcReturn(1));
        assertEquals(MyEnum.C, instance.funcReturn(2));
    }

    @Test
    void testCEnumReturnArray() {
        assertArrayEquals(new MyEnum[]{MyEnum.C, MyEnum.B, MyEnum.A}, instance.funcReturnArray());
    }

    @Test
    void testCEnumArray() {
        assertEquals(3, instance.funcArray(MyEnum.A, MyEnum.B, MyEnum.C));
    }

    @Test
    void testCEnumRef() {
        MyEnum[] arr = new MyEnum[3];
        instance.funcRef(arr);
        assertArrayEquals(new MyEnum[]{MyEnum.C, MyEnum.B, MyEnum.A}, arr);
    }
}
