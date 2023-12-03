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
 * Marks a class or interface as a native API provider.
 * <h2>Constants</h2>
 * The generated file will include constants of primitive
 * (boolean, byte, short, int, long, float, double) and String types in the marked class or interface.
 * <h2>Methods</h2>
 * The generator tries to generate <strong>static</strong> methods that invoke native functions.
 * <p>
 * The {@link Custom @Custom} annotation overwrite <strong>all</strong> generated method body with the custom code.
 * <p>
 * Some settings can be applied by marking methods with {@link Native @Native}.
 * <h3>Overload Methods</h3>
 * An overload method, marked with {@link Overload @Overload}, automatically invokes another method with the same name.
 * See {@linkplain Overload documentation} for more information.
 * <p>
 * Methods without {@link Overload @Overload} marked
 * converts parameters of {@link String} and array types into {@link java.lang.foreign.MemorySegment MemorySegment}.
 * <h3>Parameter Annotations</h3>
 * See {@link FixedSize @FixedSize} and {@link Ref @Ref}.
 * <h2>Example</h2>
 * <pre>{@code
 * @NativeApi(libname = "libGL.so", name = "GL")
 * interface CGL {
 *     int COLOR_BUFFER_BIT = 0x00004000;
 *     void glClear(int mask);
 * }
 * }</pre>
 *
 * @author squid233
 * @see Custom
 * @see Native
 * @see Overload
 * @see FixedSize
 * @see Ref
 * @since 0.1.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NativeApi {
    /**
     * {@return the name of the native library}
     * <p>
     * Might be a filename with an extension
     * or the basename of {@linkplain #selector() the library selector}.
     */
    String libname();

    /**
     * {@return the name of the generated class}
     */
    String name();

    /**
     * {@return the {@linkplain LibrarySelector library selector}}
     */
    Class<? extends LibrarySelector> selector() default LibrarySelector.class;

    /**
     * {@return {@code true} if the generated class should be {@code final}; {@code false} otherwise}
     */
    boolean makeFinal() default true;
}
