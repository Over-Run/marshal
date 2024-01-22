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

package overrun.marshal.test;

import overrun.marshal.Downcall;
import overrun.marshal.gen.*;

/**
 * Downcall interface
 *
 * @author squid233
 * @since 0.1.0
 */
public interface IDowncall {
    static IDowncall getInstance(boolean testDefaultNull) {
        return Downcall.load(DowncallProvider.lookup(testDefaultNull));
    }

    void test();

    @Entrypoint("test")
    void testWithEntrypoint();

    @Skip
    default void testSkip() {
        System.out.print("testSkip");
    }

    default void testDefault() {
        System.out.print("testDefault in interface");
    }

    void test_int(int i);

    void test_String(String s);

    void test_UTF16String(@StrCharset("UTF-16") String s);

    void test_CEnum(MyEnum myEnum);
}
