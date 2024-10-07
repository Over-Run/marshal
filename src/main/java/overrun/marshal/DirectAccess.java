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

/**
 * This interface provides access to function descriptors and method handles
 * for each function that is loaded by {@link Downcall}.
 *
 * @author squid233
 * @see Downcall
 * @since 0.1.0
 */
public interface DirectAccess {
    /**
     * {@return the data of this access}
     */
    DirectAccessData directAccessData();
}
