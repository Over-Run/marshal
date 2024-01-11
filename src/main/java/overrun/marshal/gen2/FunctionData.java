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

import overrun.marshal.gen.AccessModifier;

import java.util.List;

/**
 * Holds downcall function
 *
 * @param document       the document
 * @param annotations    the annotations
 * @param accessModifier the access modifier
 * @param returnType     the return type
 * @param name           the name
 * @param parameters     the parameters
 * @param statements     the statement
 * @author squid233
 * @since 0.1.0
 */
public record FunctionData(
    String document,
    List<AnnotationData> annotations,
    AccessModifier accessModifier,
    TypeUse returnType,
    String name,
    List<ParameterData> parameters,
    List<TypeUse> statements
) {
}
