/*
 * MIT License
 *
 * Copyright (c) 2023-2024 Overrun Organization
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

import overrun.marshal.gen.Downcall;
import overrun.marshal.gen2.DowncallData;
import overrun.marshal.internal.Processor;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.util.Set;

/**
 * Downcall annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallProcessor extends Processor {
    /**
     * constructor
     */
    public DowncallProcessor() {
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            processClasses(roundEnv);
        } catch (Exception e) {
            printStackTrace(e);
        }
        return false;
    }

    private void processClasses(RoundEnvironment roundEnv) {
        ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(Downcall.class)).forEach(e -> {
            try {
                new DowncallData(processingEnv).generate(e);
            } catch (IOException ex) {
                printStackTrace(ex);
            }
        });
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Downcall.class.getCanonicalName());
    }
}
