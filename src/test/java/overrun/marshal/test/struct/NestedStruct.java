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

import overrun.marshal.LayoutBuilder;
import overrun.marshal.struct.Struct;
import overrun.marshal.struct.StructAllocator;

import java.lang.invoke.MethodHandles;

/**
 * @author squid233
 * @since 0.1.0
 */
public interface NestedStruct extends Struct<NestedStruct> {
    StructAllocator<NestedStruct> OF = new StructAllocator<>(MethodHandles.lookup(), LayoutBuilder.struct()
        .cStruct("vec3", Vector3.OF.layout())
        .cArray("vec3arr", 2, Vector3.OF.layout())
        .build());

    int vec3$x();

    NestedStruct vec3$x(int val);

    int vec3$y();

    NestedStruct vec3$y(int val);

    int vec3$z();

    NestedStruct vec3$z(int val);

    int vec3arr$x(long index);

    NestedStruct vec3arr$x(long index, int val);

    int vec3arr$y(long index);

    NestedStruct vec3arr$y(long index, int val);

    int vec3arr$z(long index);

    NestedStruct vec3arr$z(long index, int val);
}
