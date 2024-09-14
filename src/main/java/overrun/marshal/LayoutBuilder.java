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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;

/**
 * A layout builder that automatically aligns memory layouts.
 * <h2>Example</h2>
 * <pre>{@code
 * var structLayout = LayoutBuilder.struct()
 *     .cByte("Byte")
 *     .cInt("Int")
 *     .build()
 * }</pre>
 *
 * @param <L> the memory layout to build
 * @param <T> the type of the actual layout builder
 * @author squid233
 * @see overrun.marshal.struct.StructAllocator StructAllocator
 * @since 0.1.0
 */
public abstract class LayoutBuilder<L extends MemoryLayout, T extends LayoutBuilder<L, T>> {
    /**
     * Pending layouts
     */
    protected final List<MemoryLayout> layouts = new ArrayList<>();

    /**
     * the constructor
     */
    protected LayoutBuilder() {
    }

    /**
     * {@return the struct layout builder}
     */
    public static Struct struct() {
        return new Struct();
    }

    /**
     * {@return this}
     */
    public abstract T getThis();

    /**
     * {@return the built memory layout}
     */
    public abstract L build();

    /**
     * Adds a memory layout with the given name.
     *
     * @param layout the layout
     * @param name   the name
     * @return this
     */
    public T add(MemoryLayout layout, String name) {
        layouts.add(layout.withName(name));
        return getThis();
    }

    /**
     * Adds {@code byte}.
     *
     * @param name the name
     * @return this
     */
    public T cByte(String name) {
        return add(JAVA_BYTE, name);
    }

    /**
     * Adds {@code short}.
     *
     * @param name the name
     * @return this
     */
    public T cShort(String name) {
        return add(JAVA_SHORT, name);
    }

    /**
     * Adds {@code int}.
     *
     * @param name the name
     * @return this
     */
    public T cInt(String name) {
        return add(JAVA_INT, name);
    }

    /**
     * Adds {@code long}.
     *
     * @param name the name
     * @return this
     */
    public T cLong(String name) {
        return add(JAVA_LONG, name);
    }

    /**
     * Adds {@code float}.
     *
     * @param name the name
     * @return this
     */
    public T cFloat(String name) {
        return add(JAVA_FLOAT, name);
    }

    /**
     * Adds {@code double}.
     *
     * @param name the name
     * @return this
     */
    public T cDouble(String name) {
        return add(JAVA_DOUBLE, name);
    }

    /**
     * Adds {@code boolean}.
     *
     * @param name the name
     * @return this
     */
    public T cBoolean(String name) {
        return add(JAVA_BOOLEAN, name);
    }

    /**
     * Adds {@code char}.
     *
     * @param name the name
     * @return this
     */
    public T cChar(String name) {
        return add(JAVA_CHAR, name);
    }

    /**
     * Adds {@link MemorySegment}.
     *
     * @param name the name
     * @return this
     */
    public T cAddress(String name) {
        return add(ADDRESS, name);
    }

    /**
     * Adds {@link MemorySegment} with the target layout.
     *
     * @param name         the name
     * @param targetLayout the target layout
     * @return this
     */
    public T cAddress(String name, MemoryLayout targetLayout) {
        return add(ADDRESS.withTargetLayout(targetLayout), name);
    }

    /**
     * Adds an array with the given element layout.
     *
     * @param name          the name
     * @param elementCount  the element count
     * @param elementLayout the element layout
     * @return this
     */
    public T cArray(String name, long elementCount, MemoryLayout elementLayout) {
        return add(MemoryLayout.sequenceLayout(elementCount, elementLayout), name);
    }

    /**
     * Adds a struct.
     *
     * @param name         the name
     * @param structLayout the struct layout
     * @return this
     */
    public T cStruct(String name, StructLayout structLayout) {
        return add(structLayout, name);
    }

    /**
     * The struct layout builder that aligns memory layouts with the C structure alignment.
     */
    public static final class Struct extends LayoutBuilder<StructLayout, Struct> {
        /**
         * the constructor
         */
        public Struct() {
        }

        @Override
        public Struct getThis() {
            return this;
        }

        @Override
        public StructLayout build() {
            long size = 0;
            long align = 0;
            final List<MemoryLayout> finalLayouts = new ArrayList<>(layouts.size());
            for (MemoryLayout layout : layouts) {
                final long alignment = layout.byteAlignment();
                final long padding = size % alignment;
                if (padding != 0) {
                    final PaddingLayout paddingLayout = MemoryLayout.paddingLayout(alignment - padding);
                    finalLayouts.add(paddingLayout);
                    size = Math.addExact(size, paddingLayout.byteSize());
                }
                finalLayouts.add(layout);
                size = Math.addExact(size, layout.byteSize());
                align = Math.max(align, alignment);
            }
            final long padding = size % align;
            if (padding != 0) {
                finalLayouts.add(MemoryLayout.paddingLayout(align - padding));
            }
            return MemoryLayout.structLayout(finalLayouts.toArray(new MemoryLayout[0]));
        }
    }
}
