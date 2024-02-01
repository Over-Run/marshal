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

import java.lang.foreign.*;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.*;

/**
 * An off-heap memory stack.
 *
 * <p>This class should be used in a thread-local manner for stack allocations.</p>
 *
 * @author lwjgl3
 * @author squid233
 * @see Configurations#STACK_SIZE
 * @see Configurations#STACK_FRAMES
 * @see Configurations#DEBUG_STACK
 * @since 0.1.0
 */
public sealed class MemoryStack implements Arena {
    private static final boolean CHECKS = Configurations.CHECKS.get();
    private static final boolean DEBUG = Configurations.DEBUG.get();
    private static final boolean DEBUG_STACK = Configurations.DEBUG_STACK.get();
    private static final long DEFAULT_STACK_SIZE = Configurations.STACK_SIZE.get() * 1024;
    private static final int DEFAULT_STACK_FRAMES = Configurations.STACK_FRAMES.get();
    private static final ThreadLocal<MemoryStack> TLS = ThreadLocal.withInitial(MemoryStack::create);

    static {
        if (DEFAULT_STACK_SIZE <= 0) throw new IllegalStateException("Invalid stack size.");
        if (DEFAULT_STACK_FRAMES <= 0) throw new IllegalStateException("Invalid stack frames.");
    }

    private final MemorySegment segment;
    private long pointer;
    private long[] frames;
    int frameIndex;

    /**
     * Creates a new {@code MemoryStack} backed by the specified memory segment.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param segment the backing memory segment
     */
    protected MemoryStack(MemorySegment segment) {
        this.segment = segment;
        this.pointer = segment.byteSize();
        this.frames = new long[DEFAULT_STACK_FRAMES];
    }

    /**
     * Creates a new {@code MemoryStack} with the default size.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @return the memory stack
     */
    public static MemoryStack create() {
        return create(DEFAULT_STACK_SIZE);
    }

    /**
     * Creates a new {@code MemoryStack} with the specified size.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param byteSize the maximum number of bytes that may be allocated on the stack
     * @return the memory stack
     */
    public static MemoryStack create(long byteSize) {
        return create(Arena.ofAuto(), byteSize);
    }

    /**
     * Creates a new {@code MemoryStack} with the specified size and arena.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param arena    the arena for allocating buffer
     * @param byteSize the maximum number of bytes that may be allocated on the stack
     * @return the memory stack
     */
    public static MemoryStack create(Arena arena, long byteSize) {
        return create(arena.allocate(byteSize));
    }

    /**
     * Creates a new {@code MemoryStack} backed by the specified memory segment.
     *
     * <p>In the initial state, there is no active stack frame. The {@link #push} method must be used before any allocations.</p>
     *
     * @param segment the backing memory segment
     * @return the memory stack
     */
    public static MemoryStack create(MemorySegment segment) {
        return DEBUG_STACK ?
            new DebugMemoryStack(segment) :
            new MemoryStack(segment);
    }

    private void frameOverflow() {
        if (DEBUG) {
            Configurations.apiLog("[WARNING] Out of frame stack space (" + frames.length + ") in thread: " + Thread.currentThread());
        }
        frames = Arrays.copyOf(frames, frames.length * 3 / 2);
    }

    /**
     * Stores the current stack pointer and pushes a new frame to the stack.
     *
     * <p>This method should be called when entering a method, before doing any stack allocations. When exiting a method, call the {@link #pop} method to
     * restore the previous stack frame.</p>
     *
     * <p>Pairs of push/pop calls may be nested. Care must be taken to:</p>
     * <ul>
     * <li>match every push with a pop</li>
     * <li>not call pop before push has been called at least once</li>
     * <li>not nest push calls to more than the maximum supported depth</li>
     * </ul>
     *
     * @return this stack
     */
    public MemoryStack push() {
        if (frameIndex == frames.length) {
            frameOverflow();
        }
        frames[frameIndex++] = pointer;
        return this;
    }

    /**
     * Pops the current stack frame and moves the stack pointer to the end of the previous stack frame.
     *
     * @return this stack
     */
    public MemoryStack pop() {
        pointer = frames[--frameIndex];
        return this;
    }

    /**
     * {@return the backing segment}
     */
    public MemorySegment segment() {
        return segment;
    }

    /**
     * {@return the current frame index}
     *
     * <p>This is the current number of nested {@link #push} calls.</p>
     */
    public int frameIndex() {
        return frameIndex;
    }

    /**
     * {@return the memory segment at the current stack pointer}
     */
    public MemorySegment pointerAddress() {
        return segment().asSlice(pointer);
    }

    /**
     * {@return the current stack pointer}
     *
     * <p>The stack grows "downwards", so when the stack is empty {@code pointer} is equal to {@code size}. On every allocation {@code pointer} is reduced by
     * the allocated size (after alignment) and {@code address + pointer} points to the first byte of the last allocation.</p>
     *
     * <p>Effectively, this methods returns how many more bytes may be allocated on the stack.</p>
     */
    public long pointer() {
        return pointer;
    }

    /**
     * Sets the current stack pointer.
     *
     * <p>This method directly manipulates the stack pointer. Using it irresponsibly may break the internal state of the stack. It should only be used in rare
     * cases or in auto-generated code.</p>
     *
     * @param pointer the pointer
     */
    public void setPointer(long pointer) {
        if (CHECKS) {
            checkPointer(pointer);
        }

        this.pointer = pointer;
    }

    private void checkPointer(long pointer) {
        if (pointer < 0 || segment().byteSize() < pointer) {
            throw new IndexOutOfBoundsException("Invalid stack pointer");
        }
    }

    /**
     * Allocates a block of {@code size} bytes of memory on the stack.
     * The content of the newly allocated block of memory is not initialized, remaining with
     * indeterminate values.
     *
     * @param byteSize      the allocation size
     * @param byteAlignment the required alignment
     * @return the memory segment on the stack for the requested allocation
     */
    public MemorySegment malloc(long byteSize, long byteAlignment) {
        if (CHECKS) {
            checkByteSize(byteSize);
            checkAlignment(byteAlignment);
        }

        // Align address to the specified alignment
        long rawLong = segment().address();
        long address = (rawLong + pointer - byteSize) & -byteAlignment;

        pointer = address - rawLong;
        if (CHECKS && pointer < 0) {
            throw new OutOfMemoryError("Out of stack space.");
        }

        return segment().asSlice(pointer, byteSize);
    }

    /**
     * Allocates a block of {@code size} bytes of memory on the stack.
     * The content of the newly allocated block of memory is not initialized, remaining with
     * indeterminate values.
     *
     * @param layout the layout of the memory segment
     * @return the memory segment on the stack for the requested allocation
     */
    public MemorySegment malloc(MemoryLayout layout) {
        return malloc(layout.byteSize(), layout.byteAlignment());
    }

    private MemorySegment malloc(long byteSize, ValueLayout valueLayout) {
        return malloc(byteSize, valueLayout.byteAlignment());
    }

    private static void checkByteSize(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be >= 0.");
        }
    }

    private static void checkAlignment(long alignment) {
        if (alignment <= 0) {
            throw new IllegalArgumentException("Alignment must be > 0.");
        }
        if (Long.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("Alignment must be a power-of-two value.");
        }
    }

    /**
     * Allocates a block of memory on the stack for an array of {@code num} elements,
     * each of them {@code size} bytes long, and initializes all its bits to
     * zero.
     *
     * @param byteSize      the allocation size
     * @param byteAlignment the required element alignment
     * @return the memory segment on the stack for the requested allocation
     */
    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return malloc(byteSize, byteAlignment).fill((byte) 0);
    }

    @Override
    public MemorySegment allocateFrom(OfByte layout, byte value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfChar layout, char value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfShort layout, short value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfInt layout, int value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfFloat layout, float value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfLong layout, long value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(OfDouble layout, double value) {
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(AddressLayout layout, MemorySegment value) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(layout);
        final var segment = malloc(layout.byteSize(), layout);
        segment.set(layout, 0, value);
        return segment;
    }

    @Override
    public MemorySegment allocateFrom(ValueLayout elementLayout, MemorySegment source, ValueLayout sourceElementLayout, long sourceOffset, long elementCount) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(sourceElementLayout);
        Objects.requireNonNull(elementLayout);
        final var segment = malloc(elementLayout.byteSize() * elementCount, elementLayout);
        MemorySegment.copy(source, sourceElementLayout, sourceOffset, segment, elementLayout, 0, elementCount);
        return segment;
    }

    @Override
    public MemorySegment.Scope scope() {
        return segment().scope();
    }

    @Override
    public void close() {
        pop();
    }

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment bytes(byte x) {
        return allocateFrom(JAVA_BYTE, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment bytes(byte x, byte y) {
        final var segment = malloc(2, JAVA_BYTE);
        segment.set(JAVA_BYTE, 0, x);
        segment.set(JAVA_BYTE, 1, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment bytes(byte x, byte y, byte z) {
        final var segment = malloc(3, JAVA_BYTE);
        segment.set(JAVA_BYTE, 0, x);
        segment.set(JAVA_BYTE, 1, y);
        segment.set(JAVA_BYTE, 2, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment bytes(byte x, byte y, byte z, byte w) {
        final var segment = malloc(4, JAVA_BYTE);
        segment.set(JAVA_BYTE, 0, x);
        segment.set(JAVA_BYTE, 1, y);
        segment.set(JAVA_BYTE, 2, z);
        segment.set(JAVA_BYTE, 3, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment bytes(byte... values) {
        return allocateFrom(JAVA_BYTE, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment shorts(short x) {
        return allocateFrom(JAVA_SHORT, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment shorts(short x, short y) {
        final var segment = malloc(4, JAVA_SHORT);
        segment.set(JAVA_SHORT, 0, x);
        segment.set(JAVA_SHORT, 2, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment shorts(short x, short y, short z) {
        final var segment = malloc(6, JAVA_SHORT);
        segment.set(JAVA_SHORT, 0, x);
        segment.set(JAVA_SHORT, 2, y);
        segment.set(JAVA_SHORT, 4, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment shorts(short x, short y, short z, short w) {
        final var segment = malloc(8, JAVA_SHORT);
        segment.set(JAVA_SHORT, 0, x);
        segment.set(JAVA_SHORT, 2, y);
        segment.set(JAVA_SHORT, 4, z);
        segment.set(JAVA_SHORT, 6, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment shorts(short... values) {
        return allocateFrom(JAVA_SHORT, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment ints(int x) {
        return allocateFrom(JAVA_INT, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment ints(int x, int y) {
        final var segment = malloc(8, JAVA_INT);
        segment.set(JAVA_INT, 0, x);
        segment.set(JAVA_INT, 4, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment ints(int x, int y, int z) {
        final var segment = malloc(12, JAVA_INT);
        segment.set(JAVA_INT, 0, x);
        segment.set(JAVA_INT, 4, y);
        segment.set(JAVA_INT, 8, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment ints(int x, int y, int z, int w) {
        final var segment = malloc(16, JAVA_INT);
        segment.set(JAVA_INT, 0, x);
        segment.set(JAVA_INT, 4, y);
        segment.set(JAVA_INT, 8, z);
        segment.set(JAVA_INT, 12, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment ints(int... values) {
        return allocateFrom(JAVA_INT, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment longs(long x) {
        return allocateFrom(JAVA_LONG, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment longs(long x, long y) {
        final var segment = malloc(16, JAVA_LONG);
        segment.set(JAVA_LONG, 0, x);
        segment.set(JAVA_LONG, 8, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment longs(long x, long y, long z) {
        final var segment = malloc(24, JAVA_LONG);
        segment.set(JAVA_LONG, 0, x);
        segment.set(JAVA_LONG, 8, y);
        segment.set(JAVA_LONG, 16, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment longs(long x, long y, long z, long w) {
        final var segment = malloc(32, JAVA_LONG);
        segment.set(JAVA_LONG, 0, x);
        segment.set(JAVA_LONG, 8, y);
        segment.set(JAVA_LONG, 16, z);
        segment.set(JAVA_LONG, 24, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment longs(long... values) {
        return allocateFrom(JAVA_LONG, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment floats(float x) {
        return allocateFrom(JAVA_FLOAT, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment floats(float x, float y) {
        final var segment = malloc(8, JAVA_FLOAT);
        segment.set(JAVA_FLOAT, 0, x);
        segment.set(JAVA_FLOAT, 4, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment floats(float x, float y, float z) {
        final var segment = malloc(12, JAVA_FLOAT);
        segment.set(JAVA_FLOAT, 0, x);
        segment.set(JAVA_FLOAT, 4, y);
        segment.set(JAVA_FLOAT, 8, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment floats(float x, float y, float z, float w) {
        final var segment = malloc(16, JAVA_FLOAT);
        segment.set(JAVA_FLOAT, 0, x);
        segment.set(JAVA_FLOAT, 4, y);
        segment.set(JAVA_FLOAT, 8, z);
        segment.set(JAVA_FLOAT, 12, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment floats(float... values) {
        return allocateFrom(JAVA_FLOAT, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment doubles(double x) {
        return allocateFrom(JAVA_DOUBLE, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment doubles(double x, double y) {
        final var segment = malloc(16, JAVA_DOUBLE);
        segment.set(JAVA_DOUBLE, 0, x);
        segment.set(JAVA_DOUBLE, 8, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment doubles(double x, double y, double z) {
        final var segment = malloc(24, JAVA_DOUBLE);
        segment.set(JAVA_DOUBLE, 0, x);
        segment.set(JAVA_DOUBLE, 8, y);
        segment.set(JAVA_DOUBLE, 16, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment doubles(double x, double y, double z, double w) {
        final var segment = malloc(32, JAVA_DOUBLE);
        segment.set(JAVA_DOUBLE, 0, x);
        segment.set(JAVA_DOUBLE, 8, y);
        segment.set(JAVA_DOUBLE, 16, z);
        segment.set(JAVA_DOUBLE, 24, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment doubles(double... values) {
        return allocateFrom(JAVA_DOUBLE, values);
    }

    // -------------------------------------------------

    /**
     * Single value version of {@link #malloc}.
     *
     * @param x the first value
     * @return the segment
     */
    public MemorySegment segments(MemorySegment x) {
        return allocateFrom(ADDRESS, x);
    }

    /**
     * Two value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @return the segment
     */
    public MemorySegment segments(MemorySegment x, MemorySegment y) {
        final var segment = malloc(ADDRESS.byteSize() * 2, ADDRESS);
        segment.set(ADDRESS, 0, x);
        segment.setAtIndex(ADDRESS, 1, y);
        return segment;
    }

    /**
     * Three value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the segment
     */
    public MemorySegment segments(MemorySegment x, MemorySegment y, MemorySegment z) {
        final var segment = malloc(ADDRESS.byteSize() * 3, ADDRESS);
        segment.set(ADDRESS, 0, x);
        segment.setAtIndex(ADDRESS, 1, y);
        segment.setAtIndex(ADDRESS, 2, z);
        return segment;
    }

    /**
     * Four value version of {@link #malloc}.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @param w the fourth value
     * @return the segment
     */
    public MemorySegment segments(MemorySegment x, MemorySegment y, MemorySegment z, MemorySegment w) {
        final var segment = malloc(ADDRESS.byteSize() * 4, ADDRESS);
        segment.set(ADDRESS, 0, x);
        segment.setAtIndex(ADDRESS, 1, y);
        segment.setAtIndex(ADDRESS, 2, z);
        segment.setAtIndex(ADDRESS, 3, w);
        return segment;
    }

    /**
     * Vararg version of {@link #malloc}.
     *
     * @param values the values
     * @return the segment
     */
    public MemorySegment segments(MemorySegment... values) {
        final var segment = malloc(ADDRESS.byteSize() * values.length, ADDRESS);
        for (int i = 0; i < values.length; i++) {
            segment.setAtIndex(ADDRESS, i, values[i]);
        }
        return segment;
    }

    // -----------------------------------------------------
    // -----------------------------------------------------
    // -----------------------------------------------------

    /**
     * {@return the stack of the current thread}
     */
    public static MemoryStack stackGet() {
        return TLS.get();
    }

    /**
     * Calls {@link #push} on the stack of the current thread.
     *
     * @return the stack of the current thread.
     */
    public static MemoryStack stackPush() {
        return stackGet().push();
    }

    /**
     * Calls {@link #pop} on the stack of the current thread.
     *
     * @return the stack of the current thread.
     */
    public static MemoryStack stackPop() {
        return stackGet().pop();
    }

    /**
     * Stores the method that pushed a frame and checks if it is the same method when the frame is popped.
     *
     * @since 0.1.0
     */
    private static final class DebugMemoryStack extends MemoryStack {
        private Object[] debugFrames;

        private DebugMemoryStack(MemorySegment address) {
            super(address);
            debugFrames = new Object[DEFAULT_STACK_FRAMES];
        }

        @Override
        public MemoryStack push() {
            if (frameIndex == debugFrames.length) {
                frameOverflow();
            }

            debugFrames[frameIndex] = StackWalkUtil.stackWalkGetMethod(MemoryStack.class);

            return super.push();
        }

        private void frameOverflow() {
            debugFrames = Arrays.copyOf(debugFrames, debugFrames.length * 3 / 2);
        }

        @Override
        public MemoryStack pop() {
            Object pushed = debugFrames[frameIndex - 1];
            Object popped = StackWalkUtil.stackWalkCheckPop(MemoryStack.class, pushed);
            if (popped != null) {
                reportAsymmetricPop(pushed, popped);
            }

            debugFrames[frameIndex - 1] = null;
            return super.pop();
        }

        // No need to check pop in try-with-resources
        @Override
        public void close() {
            debugFrames[frameIndex - 1] = null;
            super.pop();
        }

        private static void reportAsymmetricPop(Object pushed, Object popped) {
            Configurations.apiLog(String.format(
                "[overrun.marshal] Asymmetric pop detected:\n\tPUSHED: %s\n\tPOPPED: %s\n\tTHREAD: %s\n",
                pushed,
                popped,
                Thread.currentThread()
            ));
        }
    }
}
