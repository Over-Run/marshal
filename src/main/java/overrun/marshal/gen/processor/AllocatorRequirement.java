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

package overrun.marshal.gen.processor;

/**
 * Allocator requirement. This tells {@link overrun.marshal.Downcall Downcall} whether a segment allocator is required.
 *
 * @author squid233
 * @since 0.1.0
 */
public enum AllocatorRequirement {
    /**
     * No allocator required
     */
    NONE(0),
    /**
     * No allocator required; memory stack can be implicitly used
     */
    STACK(1),
    /**
     * {@link java.lang.foreign.SegmentAllocator SegmentAllocator} required
     */
    ALLOCATOR(2),
    /**
     * {@link java.lang.foreign.Arena Arena} required
     */
    ARENA(3);

    private final int level;

    AllocatorRequirement(int level) {
        this.level = level;
    }

    /**
     * {@return the stricter allocator requirement between {@code first} and {@code second}}
     *
     * @param first  the first requirement
     * @param second the second requirement
     */
    public static AllocatorRequirement stricter(AllocatorRequirement first, AllocatorRequirement second) {
        return first.isStricter(second) ? first : second;
    }

    /**
     * {@return {@code true} if this requirement is stricter than {@code other}}
     *
     * @param other the other requirement
     */
    public boolean isStricter(AllocatorRequirement other) {
        return this.level > other.level;
    }
}
