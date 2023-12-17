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

import overrun.marshal.StrCharset;
import overrun.marshal.Upcall;
import overrun.marshal.gen.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;

/**
 * Annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
public abstract class Processor extends AbstractProcessor {
    /**
     * upcallTypeMirror
     */
    protected TypeMirror upcallTypeMirror;

    /**
     * constructor
     */
    protected Processor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        upcallTypeMirror = processingEnv.getElementUtils().getTypeElement(Upcall.class.getCanonicalName()).asType();
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

    /**
     * isUpcall
     *
     * @param typeMirror typeMirror
     * @return isUpcall
     */
    protected boolean isUpcall(TypeMirror typeMirror) {
        return Util.isDeclared(typeMirror) &&
               processingEnv.getTypeUtils().isAssignable(typeMirror, upcallTypeMirror);
    }

    /**
     * Add an annotation
     *
     * @param annotation annotation
     */
    protected <T extends Annotation, R> void addAnnotationValue(Annotatable annotatable, T annotation, Class<T> tClass, Function<T, R> function) {
        if (annotation != null) {
            annotatable.addAnnotation(new AnnotationSpec(tClass)
                .addArgument("value", getConstExp(function.apply(annotation))));
        }
    }

    /**
     * getCustomCharset
     *
     * @param e element
     * @return getCustomCharset
     */
    protected static String getCustomCharset(Element e) {
        final StrCharset strCharset = e.getAnnotation(StrCharset.class);
        return strCharset != null ? strCharset.value() : "UTF-8";
    }

    /**
     * Create charset
     *
     * @param file file
     * @param name name
     * @return charset
     */
    protected Spec createCharset(SourceFile file, String name) {
        final String upperCase = name.toUpperCase(Locale.ROOT);
        return switch (upperCase) {
            case "UTF-8", "ISO-8859-1", "US-ASCII",
                "UTF-16", "UTF-16BE", "UTF-16LE",
                "UTF-32", "UTF-32BE", "UTF-32LE" -> {
                file.addImport(StandardCharsets.class);
                yield Spec.accessSpec(StandardCharsets.class, upperCase.replace('-', '_'));
            }
            default -> {
                file.addImport(Charset.class);
                yield new InvokeSpec(Charset.class, "forName").addArgument(getConstExp(name));
            }
        };
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_22;
    }
}
