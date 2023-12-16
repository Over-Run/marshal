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

package overrun.marshal.internal;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
public abstract class Processor extends AbstractProcessor {
    /**
     * constructor
     */
    protected Processor() {
    }

    /**
     * Print error
     *
     * @param msg message
     */
    protected void printError(Object msg) {
        processingEnv.getMessager().printError(String.valueOf(msg));
    }

    /**
     * Print stack trace
     *
     * @param t throwable
     */
    protected void printStackTrace(Throwable t) {
        try (PrintWriter writer = new PrintWriter(Writer.nullWriter()) {
            private final StringBuffer sb = new StringBuffer(2048);

            @Override
            public void println(String x) {
                sb.append(x);
                sb.append('\n');
            }

            @Override
            public void println(Object x) {
                sb.append(x);
                sb.append('\n');
            }

            @Override
            public void close() {
                printError(sb);
            }
        }) {
            t.printStackTrace(writer);
        }
    }

    /**
     * Get document
     *
     * @param element element
     * @return document
     */
    protected String getDocument(Element element) {
        return processingEnv.getElementUtils().getDocComment(element);
    }

    /**
     * Get const expression
     *
     * @param value value
     * @return const expression
     */
    protected String getConstExp(Object value) {
        return processingEnv.getElementUtils().getConstantExpression(value);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_22;
    }
}
