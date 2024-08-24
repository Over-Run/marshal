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

import overrun.marshal.gen.processor.AllocatorRequirement;

import java.lang.reflect.Parameter;
import java.util.List;

/**
 * Holds downcall method name
 *
 * @param entrypoint           entrypoint
 * @param signatureString      signatureString
 * @param parameters           parameters
 * @param skipFirstParam       skipFirstParam
 * @param allocatorRequirement allocatorRequirement
 * @author squid233
 * @since 0.1.0
 */
record DowncallMethodData(
    String entrypoint,
    String signatureString,
    List<Parameter> parameters,
    boolean skipFirstParam,
    AllocatorRequirement allocatorRequirement
) {
}
