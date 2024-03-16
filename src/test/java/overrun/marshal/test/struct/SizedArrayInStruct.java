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

package overrun.marshal.test.struct;

import overrun.marshal.Marshal;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructHandle;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/**
 * @author squid233
 * @since 0.1.0
 */
public final class SizedArrayInStruct extends Struct {
    public static final class Handle extends StructHandle {
        private Handle(VarHandle varHandle) {
            super(varHandle);
        }

        public int get(Struct struct, long arrIndex) {
            return (int) varHandle.get(Marshal.marshal(struct), 0L, 0L, arrIndex);
        }

        public void set(Struct struct, long arrIndex, int value) {
            varHandle.set(Marshal.marshal(struct), 0L, 0L, arrIndex, value);
        }
    }

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_INT).withName("arr")
    );
    public static final Handle arr = new Handle(StructHandle.ofSizedArray(LAYOUT, "arr"));

    public SizedArrayInStruct(SegmentAllocator allocator) {
        super(allocator, LAYOUT);
    }
}
