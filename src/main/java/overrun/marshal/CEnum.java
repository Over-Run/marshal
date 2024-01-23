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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A C enum value provider.
 * <h2>Example</h2>
 * <pre>{@code
 * enum MyEnum implements CEnum {
 *     A(0), B(1);
 *     final int value;
 *     MyEnum(int value) { this.value = value; }
 *
 *     @Wrapper
 *     public static MyEnum of(int value) {
 *         return switch (value) {
 *             case 0 -> A;
 *             case 1 -> B;
 *             default -> throw new IllegalArgumentException("Unexpected value: " + value);
 *         }
 *     }
 *
 *     @Override
 *     public int value() { return value; }
 * }
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
public interface CEnum {
    /**
     * {@return the value of the enum}
     */
    int value();

    /**
     * Marks a <strong>static</strong> method as an enum value wrapper.
     * <p>
     * The marked method must only contain one {@code int} parameter.
     *
     * @author squid233
     * @see CEnum
     * @since 0.1.0
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Wrapper {
    }
}
