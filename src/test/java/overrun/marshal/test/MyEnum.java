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

package overrun.marshal.test;

import overrun.marshal.gen.CEnum;

/**
 * Enum
 *
 * @author squid233
 * @since 0.1.0
 */
public enum MyEnum implements CEnum {
    A(0),
    B(2),
    C(4);
    private final int value;

    MyEnum(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }
}
