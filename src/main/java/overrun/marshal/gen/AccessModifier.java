/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
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
 * The access modifier.
 *
 * @author squid233
 * @see Access
 * @since 0.1.0
 */
public enum AccessModifier {
    /**
     * {@code public} access
     */
    PUBLIC("public"),
    /**
     * {@code protected} access
     */
    PROTECTED("protected"),
    /**
     * package-private access
     */
    PACKAGE_PRIVATE(""),
    /**
     * {@code private} access
     */
    PRIVATE("private");

    private final String toStringValue;

    AccessModifier(String toStringValue) {
        this.toStringValue = toStringValue;
    }

    @Override
    public String toString() {
        return toStringValue;
    }
}
