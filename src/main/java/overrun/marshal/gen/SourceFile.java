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

package overrun.marshal.gen;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Consumer;

/**
 * Source file
 *
 * @author squid233
 * @since 0.1.0
 */
public final class SourceFile {
    private final List<ClassSpec> classSpecs = new ArrayList<>(1);
    private final String packageName;
    private final Set<String> imports = new LinkedHashSet<>();

    /**
     * Constructor
     *
     * @param packageName package name
     */
    public SourceFile(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Add imports
     *
     * @param names class names
     */
    public void addImports(String... names) {
        imports.addAll(Arrays.asList(names));
    }

    /**
     * Add import
     *
     * @param name class name
     */
    public void addImport(String name) {
        imports.add(name);
    }

    /**
     * Add a class and perform the action
     *
     * @param className class name
     * @param consumer  action
     */
    public void addClass(String className, Consumer<ClassSpec> consumer) {
        final ClassSpec spec = new ClassSpec(className);
        consumer.accept(spec);
        classSpecs.add(spec);
    }

    /**
     * Write to the writer
     *
     * @param writer the writer
     * @throws IOException If an I/O error occurs
     */
    public void write(Writer writer) throws IOException {
        final StringBuilder sb = new StringBuilder(16384);
        // header
        sb.append("// This file is auto-generated. DO NOT EDIT!\n");
        // package
        if (packageName != null) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        // import
        if (!imports.isEmpty()) {
            imports.forEach(s -> sb.append("import ").append(s).append(";\n"));
            sb.append('\n');
        }
        classSpecs.forEach(classSpec -> classSpec.append(sb, 0));
        writer.write(sb.toString());
    }
}
