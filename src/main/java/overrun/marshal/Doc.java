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

package overrun.marshal;

import java.lang.annotation.*;

/**
 * Gives documentation to a method.
 * <h2>Example</h2>
 * <pre>{@code
 * @Doc("""
 *     A const value""")
 * int CONST_VALUE = 42;
 *
 * @Doc("""
 *     The documentation
 *
 *     @param p the parameter
 *     @return 1 if success; 0 otherwise""")
 * int myFunction(int p);
 * }</pre>
 *
 * @author squid233
 * @since 0.1.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Doc {
    /**
     * {@return the javadoc of the method}
     */
    String value();
}
