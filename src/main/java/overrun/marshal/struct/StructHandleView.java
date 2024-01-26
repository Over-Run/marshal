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

package overrun.marshal.struct;

import java.lang.foreign.MemorySegment;

/**
 * A view of a {@linkplain StructHandle struct handle}, which only provides getters.
 *
 * @author squid233
 * @since 0.1.0
 */
public interface StructHandleView {
    /**
     * boolean generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Bool extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        boolean get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        boolean get();
    }

    /**
     * char generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Char extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        char get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        char get();
    }

    /**
     * byte generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Byte extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        byte get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        byte get();
    }

    /**
     * short generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Short extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        short get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        short get();
    }

    /**
     * int generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Int extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        int get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        int get();
    }

    /**
     * long generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Long extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        long get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        long get();
    }

    /**
     * float generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Float extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        float get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        float get();
    }

    /**
     * double generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Double extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        double get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        double get();
    }

    /**
     * memory segment type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Address extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        MemorySegment get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        MemorySegment get();
    }

    /**
     * string type
     *
     * @author squid233
     * @since 0.1.0
     */
    interface Str extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param byteSize  the byte size of the string
         * @param index the index
         * @return the value
         */
        String get(long byteSize, long index);

        /**
         * Gets the value.
         *
         * @param byteSize the byte size of the string
         * @return the value
         */
        String get(long byteSize);
    }

    /**
     * array type
     *
     * @param <T> the type of the array
     * @author squid233
     * @since 0.1.0
     */
    interface Array<T> extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param byteSize  the byte size of the array
         * @param index the index
         * @return the value
         */
        T get(long byteSize, long index);

        /**
         * Gets the value.
         *
         * @param byteSize the byte size of the array
         * @return the value
         */
        T get(long byteSize);
    }

    /**
     * generic type
     *
     * @param <T> the type of the element
     * @author squid233
     * @since 0.1.0
     */
    interface Type<T> extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param index the index
         * @return the value
         */
        T get(long index);

        /**
         * Gets the value.
         *
         * @return the value
         */
        T get();
    }

    /**
     * generic type with userdata
     *
     * @param <T> the type of the element
     * @param <U> the type of the userdata
     * @author squid233
     * @since 0.1.0
     */
    interface TypeExt<T, U> extends StructHandleView {
        /**
         * Gets the value at the given index.
         *
         * @param userdata the userdata
         * @param index    the index
         * @return the value
         */
        T get(U userdata, long index);

        /**
         * Gets the value.
         *
         * @param userdata the userdata
         * @return the value
         */
        T get(U userdata);
    }
}
