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

import javax.lang.model.element.ExecutableElement;
import java.util.List;
import java.util.Optional;

/**
 * Holds method handle
 *
 * @param executableElement the executable element
 * @param accessModifier    the access modifier
 * @param name              the name
 * @param returnType        the return type
 * @param parameterTypes    the parameter types
 * @param optional          optional
 * @author squid233
 * @since 0.1.0
 */
public record MethodHandleData(
    ExecutableElement executableElement,
    AccessModifier accessModifier,
    String name,
    Optional<TypeUse> returnType,
    List<TypeUse> parameterTypes,
    boolean optional
) {
}
