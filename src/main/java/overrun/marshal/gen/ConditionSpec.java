/*
 * MIT License
 *
 * Copyright (c) 2023 Overrun Organization
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

package overrun.marshal.gen;

/**
 * Condition
 *
 * @author squid233
 * @since 0.1.0
 */
public final class ConditionSpec implements Spec {
    private final Spec value;

    private ConditionSpec(Spec value) {
        this.value = value;
    }

    /**
     * Literal condition
     *
     * @param s condition
     * @return spec
     */
    public static ConditionSpec of(String s) {
        return new ConditionSpec(Spec.literal(s));
    }

    /**
     * not null condition
     * @param value value
     * @return spec
     */
    public static ConditionSpec notNull(String value) {
        return of(value + " != null");
    }

    @Override
    public void append(StringBuilder builder, int indent) {
        value.append(builder, indent);
    }
}
