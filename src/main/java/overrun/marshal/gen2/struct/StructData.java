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

package overrun.marshal.gen2.struct;

import overrun.marshal.gen.struct.Struct;
import overrun.marshal.gen2.BaseData;
import overrun.marshal.gen2.DeclaredTypeData;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;

/**
 * Holds struct fields and methods
 *
 * @author squid233
 * @since 0.1.0
 */
public final class StructData extends BaseData {
    /**
     * Construct
     *
     * @param processingEnv the processing environment
     */
    public StructData(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    private void processStructType(TypeElement typeElement, String simpleClassName) {
        final var enclosedElements = typeElement.getEnclosedElements();

        final Struct struct = typeElement.getAnnotation(Struct.class);

        this.document = getDocComment(typeElement);
        this.nonFinal = struct.nonFinal();

        final var fields = skipAnnotated(ElementFilter.fieldsIn(enclosedElements)).toList();
    }

    /**
     * Generates the file
     *
     * @param typeElement the type element
     * @throws IOException if an I/O error occurred
     */
    @Override
    public void generate(TypeElement typeElement) throws IOException {
        final Struct struct = typeElement.getAnnotation(Struct.class);
        final DeclaredTypeData generatedClassName = generateClassName(struct.name(), typeElement);

        processStructType(typeElement, generatedClassName.name());

        generate(generatedClassName);
    }
}
