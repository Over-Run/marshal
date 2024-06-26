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
public interface Vector3 extends Struct<Vector3> {
    StructAllocator<Vector3> OF = new StructAllocator<>(MethodHandles.lookup(), LayoutBuilder.struct()
        .cInt("x")
        .cInt("y")
        .cInt("z")
        .build());

    int x();

    int y();

    int z();

    Vector3 x(int val);

    Vector3 y(int val);
}
