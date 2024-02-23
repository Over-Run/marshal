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

import org.jetbrains.annotations.Nullable;
import overrun.marshal.Marshal;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The struct handle that provides getter and setter of an element in a struct.
 *
 * @author squid233
 * @since 0.1.0
 */
public class StructHandle implements StructHandleView {
    /**
     * The var handle where to access the struct.
     */
    protected final VarHandle varHandle;

    /**
     * Creates a struct handle with the given var handle.
     *
     * @param varHandle the var handle
     */
    protected StructHandle(VarHandle varHandle) {
        this.varHandle = varHandle;
    }

    /**
     * Creates a var handle where to access a value of the given struct layout.
     *
     * @param layout the struct layout
     * @param name   the name of the value
     * @return the var handle
     */
    public static VarHandle ofValue(StructLayout layout, String name) {
        return layout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(name));
    }

    /**
     * Creates a var handle where to access a value in a sized array of the given struct layout.
     *
     * @param layout the struct layout
     * @param name   the name of the sized array
     * @return the var handle
     */
    public static VarHandle ofSizedArray(StructLayout layout, String name) {
        return layout.arrayElementVarHandle(
            MemoryLayout.PathElement.groupElement(name),
            MemoryLayout.PathElement.sequenceElement()
        );
    }

    /**
     * Creates a boolean struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Bool ofBoolean(StructLayout layout, String name) {
        return new Bool(ofValue(layout, name));
    }

    /**
     * Creates a char struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Char ofChar(StructLayout layout, String name) {
        return new Char(ofValue(layout, name));
    }

    /**
     * Creates a byte struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Byte ofByte(StructLayout layout, String name) {
        return new Byte(ofValue(layout, name));
    }

    /**
     * Creates a short struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Short ofShort(StructLayout layout, String name) {
        return new Short(ofValue(layout, name));
    }

    /**
     * Creates an int struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Int ofInt(StructLayout layout, String name) {
        return new Int(ofValue(layout, name));
    }

    /**
     * Creates a long struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Long ofLong(StructLayout layout, String name) {
        return new Long(ofValue(layout, name));
    }

    /**
     * Creates a float struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Float ofFloat(StructLayout layout, String name) {
        return new Float(ofValue(layout, name));
    }

    /**
     * Creates a double struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Double ofDouble(StructLayout layout, String name) {
        return new Double(ofValue(layout, name));
    }

    /**
     * Creates an address struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Address ofAddress(StructLayout layout, String name) {
        return new Address(ofValue(layout, name));
    }

    /**
     * Creates a string struct handle.
     *
     * @param layout  the struct layout
     * @param name    the name
     * @param charset the charset
     * @return the struct handle
     */
    public static Str ofString(StructLayout layout, String name, Charset charset) {
        return new Str(ofValue(layout, name), charset);
    }

    /**
     * Creates a string struct handle.
     *
     * @param layout the struct layout
     * @param name   the name
     * @return the struct handle
     */
    public static Str ofString(StructLayout layout, String name) {
        return ofString(layout, name, StandardCharsets.UTF_8);
    }

    /**
     * Creates an array struct handle.
     *
     * @param layout        the struct layout
     * @param name          the name
     * @param setterFactory the setter factory
     * @param getterFactory the getter factory
     * @param <T>           the type of the array
     * @return the struct handle
     */
    public static <T> Array<T> ofArray(StructLayout layout, String name, BiFunction<SegmentAllocator, T, MemorySegment> setterFactory, Function<MemorySegment, T> getterFactory) {
        return new Array<>(ofValue(layout, name), setterFactory, getterFactory);
    }

    /**
     * Creates an addressable struct handle.
     *
     * @param layout  the struct layout
     * @param name    the name
     * @param factory the factory
     * @param <T>     the type of the addressable
     * @return the struct handle
     */
    public static <T extends overrun.marshal.Addressable> Addressable<T> ofAddressable(StructLayout layout, String name, Function<MemorySegment, T> factory) {
        return new Addressable<>(ofValue(layout, name), factory);
    }

    /**
     * Creates an upcall struct handle.
     *
     * @param layout  the struct layout
     * @param name    the name
     * @param factory the factory
     * @param <T>     the type of the upcall
     * @return the struct handle
     */
    public static <T extends overrun.marshal.Upcall> Upcall<T> ofUpcall(StructLayout layout, String name, Function<MemorySegment, T> factory) {
        return new Upcall<>(ofValue(layout, name), factory);
    }

    /**
     * boolean generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Bool extends StructHandle implements StructHandleView.Bool {
        private Bool(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, boolean value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, boolean value) {
            set(struct, 0L, value);
        }

        @Override
        public boolean get(Struct struct, long index) {
            return (boolean) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public boolean get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * char generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Char extends StructHandle implements StructHandleView.Char {
        private Char(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, char value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, char value) {
            set(struct, 0L, value);
        }

        @Override
        public char get(Struct struct, long index) {
            return (char) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public char get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * byte generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Byte extends StructHandle implements StructHandleView.Byte {
        private Byte(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, byte value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, byte value) {
            set(struct, 0L, value);
        }

        @Override
        public byte get(Struct struct, long index) {
            return (byte) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public byte get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * short generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Short extends StructHandle implements StructHandleView.Short {
        private Short(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, short value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, short value) {
            set(struct, 0L, value);
        }

        @Override
        public short get(Struct struct, long index) {
            return (short) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public short get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * int generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Int extends StructHandle implements StructHandleView.Int {
        private Int(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, int value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, int value) {
            set(struct, 0L, value);
        }

        @Override
        public int get(Struct struct, long index) {
            return (int) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public int get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * long generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Long extends StructHandle implements StructHandleView.Long {
        private Long(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, long value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, long value) {
            set(struct, 0L, value);
        }

        @Override
        public long get(Struct struct, long index) {
            return (long) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public long get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * float generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Float extends StructHandle implements StructHandleView.Float {
        private Float(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, float value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, float value) {
            set(struct, 0L, value);
        }

        @Override
        public float get(Struct struct, long index) {
            return (float) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public float get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * double generic type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Double extends StructHandle implements StructHandleView.Double {
        private Double(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, double value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, double value) {
            set(struct, 0L, value);
        }

        @Override
        public double get(Struct struct, long index) {
            return (double) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public double get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * memory segment type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Address extends StructHandle implements StructHandleView.Address {
        private Address(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public void set(Struct struct, long index, MemorySegment value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value);
        }

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public void set(Struct struct, MemorySegment value) {
            set(struct, 0L, value);
        }

        @Override
        public MemorySegment get(Struct struct, long index) {
            return (MemorySegment) varHandle.get(Marshal.marshal(struct), 0L, index);
        }

        @Override
        public MemorySegment get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * string type
     *
     * @author squid233
     * @since 0.1.0
     */
    public static final class Str extends StructHandle implements StructHandleView.Str {
        private final Charset charset;

        private Str(VarHandle varHandle, Charset charset) {
            super(varHandle);
            this.charset = charset;
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct    the struct
         * @param index     the index
         * @param allocator the allocator
         * @param value     the value
         */
        public void set(Struct struct, long index, SegmentAllocator allocator, String value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, allocator.allocateFrom(value, charset));
        }

        /**
         * Sets the value.
         *
         * @param struct    the struct
         * @param allocator the allocator
         * @param value     the value
         */
        public void set(Struct struct, SegmentAllocator allocator, String value) {
            set(struct, 0L, allocator, value);
        }

        @Override
        public String get(Struct struct, long index, long byteSize) {
            return ((MemorySegment) varHandle.get(Marshal.marshal(struct), 0L, index)).reinterpret(byteSize).getString(0L, charset);
        }

        @Override
        public String get(Struct struct, long byteSize) {
            return get(struct, 0L, byteSize);
        }
    }

    /**
     * array type
     *
     * @param <T> the type of the array
     * @author squid233
     * @since 0.1.0
     */
    public static final class Array<T> extends StructHandle implements StructHandleView.Array<T> {
        private final BiFunction<SegmentAllocator, T, MemorySegment> setterFactory;
        private final Function<MemorySegment, T> getterFactory;

        private Array(VarHandle varHandle, BiFunction<SegmentAllocator, T, MemorySegment> setterFactory, Function<MemorySegment, T> getterFactory) {
            super(varHandle);
            this.setterFactory = setterFactory;
            this.getterFactory = getterFactory;
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct    the struct
         * @param index     the index
         * @param allocator the allocator
         * @param value     the value
         */
        public void set(Struct struct, long index, SegmentAllocator allocator, T value) {
            if (setterFactory == null) throw new UnsupportedOperationException();
            varHandle.set(Marshal.marshal(struct), 0L, index, setterFactory.apply(allocator, value));
        }

        /**
         * Sets the value.
         *
         * @param struct    the struct
         * @param allocator the allocator
         * @param value     the value
         */
        public void set(Struct struct, SegmentAllocator allocator, T value) {
            set(struct, 0L, allocator, value);
        }

        @Override
        public T get(Struct struct, long index, long byteSize) {
            if (getterFactory == null) throw new UnsupportedOperationException();
            return getterFactory.apply(((MemorySegment) varHandle.get(Marshal.marshal(struct), 0L, index)).reinterpret(byteSize));
        }

        @Override
        public T get(Struct struct, long byteSize) {
            return get(struct, 0L, byteSize);
        }
    }

    /**
     * generic type
     *
     * @param <T> the type of the element
     * @author squid233
     * @since 0.1.0
     */
    public static abstract class Type<T> extends StructHandle implements StructHandleView.Type<T> {
        /**
         * Creates a struct handle with the given var handle.
         *
         * @param varHandle the var handle
         */
        protected Type(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct the struct
         * @param index  the index
         * @param value  the value
         */
        public abstract void set(Struct struct, long index, T value);

        /**
         * Sets the value.
         *
         * @param struct the struct
         * @param value  the value
         */
        public abstract void set(Struct struct, T value);
    }

    /**
     * generic type with userdata
     *
     * @param <T> the type of the element
     * @param <G> the type of the getter userdata
     * @param <S> the type of the setter userdata
     * @author squid233
     * @since 0.1.0
     */
    public static abstract class TypeExtGS<T, G, S> extends StructHandle implements StructHandleView.TypeExt<T, G> {
        /**
         * Creates a struct handle with the given var handle.
         *
         * @param varHandle the var handle
         */
        protected TypeExtGS(VarHandle varHandle) {
            super(varHandle);
        }

        /**
         * Sets the value at the given index.
         *
         * @param struct   the struct
         * @param index    the index
         * @param userdata the userdata
         * @param value    the value
         */
        public abstract void set(Struct struct, long index, S userdata, T value);

        /**
         * Sets the value.
         *
         * @param struct   the struct
         * @param userdata the userdata
         * @param value    the value
         */
        public abstract void set(Struct struct, S userdata, T value);
    }

    /**
     * generic type with userdata
     *
     * @param <T> the type of the element
     * @param <U> the type of the userdata
     * @author squid233
     * @since 0.1.0
     */
    public static abstract class TypeExt<T, U> extends TypeExtGS<T, U, U> {
        /**
         * Creates a struct handle with the given var handle.
         *
         * @param varHandle the var handle
         */
        protected TypeExt(VarHandle varHandle) {
            super(varHandle);
        }
    }

    /**
     * addressable type
     *
     * @param <T> the addressable type
     * @author squid233
     * @since 0.1.0
     */
    public static final class Addressable<T extends overrun.marshal.Addressable> extends Type<T> {
        private final Function<MemorySegment, T> factory;

        private Addressable(VarHandle varHandle, Function<MemorySegment, T> factory) {
            super(varHandle);
            this.factory = factory;
        }

        @Override
        public void set(Struct struct, long index, @Nullable T value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value != null ? value.segment() : MemorySegment.NULL);
        }

        @Override
        public void set(Struct struct, T value) {
            set(struct, 0L, value);
        }

        @Override
        public T get(Struct struct, long index) {
            if (factory == null) throw new UnsupportedOperationException();
            return factory.apply((MemorySegment) varHandle.get(Marshal.marshal(struct), 0L, index));
        }

        @Override
        public T get(Struct struct) {
            return get(struct, 0L);
        }
    }

    /**
     * upcall type
     *
     * @param <T> the upcall type
     * @author squid233
     * @since 0.1.0
     */
    public static final class Upcall<T extends overrun.marshal.Upcall> extends TypeExt<T, Arena> {
        private final Function<MemorySegment, T> factory;

        private Upcall(VarHandle varHandle, Function<MemorySegment, T> factory) {
            super(varHandle);
            this.factory = factory;
        }

        @Override
        public void set(Struct struct, long index, Arena userdata, T value) {
            varHandle.set(Marshal.marshal(struct), 0L, index, value.stub(userdata));
        }

        @Override
        public void set(Struct struct, Arena userdata, T value) {
            set(struct, 0L, userdata, value);
        }

        @Override
        public T get(Struct struct, long index, Arena userdata) {
            if (factory == null) throw new UnsupportedOperationException();
            return factory.apply((MemorySegment) varHandle.get(Marshal.marshal(struct), 0L, index));
        }

        @Override
        public T get(Struct struct, Arena userdata) {
            return get(struct, 0L, userdata);
        }
    }
}
