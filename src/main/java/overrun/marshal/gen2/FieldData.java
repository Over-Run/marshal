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

package overrun.marshal.gen2;

import overrun.marshal.AccessModifier;

/**
 * Holds field
 *
 * @param document       the document
 * @param accessModifier the access modifier
 * @param staticField    the static field
 * @param finalField     the final field
 * @param type           the type
 * @param name           the name
 * @param value          the value
 * @author squid233
 * @since 0.1.0
 */
public record FieldData(
    String document,
    AccessModifier accessModifier,
    boolean staticField,
    boolean finalField,
    TypeData type,
    String name,
    TypeUse value
) {
}
