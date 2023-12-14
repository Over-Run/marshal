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

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;

/**
 * A library loader.
 *
 * @author squid233
 * @see Downcall
 * @since 0.1.0
 */
public interface LibraryLoader {
    /**
     * Creates a library lookup with the given name.
     *
     * @param name the name of the native library
     * @return the symbol lookup of the library
     */
    static SymbolLookup loadLibrary(String name) {
        return SymbolLookup.libraryLookup(name, Arena.global());
    }

    /**
     * Loads a library based on the basename and the operating system.
     *
     * @param basename the basename of the native library
     * @return the symbol lookup of the library
     */
    SymbolLookup load(String basename);

    /**
     * The default library loader.
     *
     * @author squid233
     * @since 0.1.0
     */
    final class Default implements LibraryLoader {
        /**
         * Creates the default library loader.
         */
        public Default() {
        }

        @Override
        public SymbolLookup load(String basename) {
            return SymbolLookup.libraryLookup(basename, Arena.global());
        }
    }
}
