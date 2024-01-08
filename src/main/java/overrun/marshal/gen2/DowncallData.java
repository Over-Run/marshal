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

package overrun.marshal.gen2;

import overrun.marshal.AccessModifier;
import overrun.marshal.Downcall;
import overrun.marshal.Loader;
import overrun.marshal.gen1.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.util.*;

import static overrun.marshal.internal.Util.*;

/**
 * Holds downcall fields and functions
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallData {
    private final ProcessingEnvironment processingEnv;
    private final ImportData importData = new ImportData(new ArrayList<>());
    private final List<FieldData> fields = new ArrayList<>();
    private String document = null;
    private boolean nonFinal = false;

    /**
     * Construct
     *
     * @param processingEnv the processing environment
     */
    public DowncallData(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    private void processDowncallType(TypeElement typeElement) {
        final var enclosedElements = typeElement.getEnclosedElements();

        // annotations
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);

        document = getDocComment(processingEnv, typeElement);
        nonFinal = downcall.nonFinal();

        // process fields
        ElementFilter.fieldsIn(enclosedElements).forEach(element -> {
            final Object constantValue = element.getConstantValue();
            if (constantValue == null) {
                return;
            }
            fields.add(new FieldData(
                getDocComment(processingEnv, element),
                AccessModifier.PUBLIC,
                true,
                true,
                TypeData.detectType(processingEnv, element.asType()),
                element.getSimpleName().toString(),
                _ -> Spec.literal(getConstExp(processingEnv, constantValue))
            ));
        });

        final var methods = ElementFilter.methodsIn(enclosedElements);
        if (!methods.isEmpty()) {
            // collect method handles

            // add linker and lookup
            fields.add(new FieldData(
                null,
                AccessModifier.PRIVATE,
                true,
                true,
                TypeData.fromClass(Linker.class),
                "_LINKER", // TODO: 2024/1/8 squid233: conflict
                importData -> new InvokeSpec(importData.simplifyOrImport(Linker.class), "nativeLinker")
            ));

            addLookup(typeElement);

            // add method handles
        }
    }

    /**
     * Generates the file
     *
     * @param typeElement the type element
     * @throws IOException if an I/O error occurred
     */
    public void generate(TypeElement typeElement) throws IOException {
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);
        final DeclaredTypeData declaredTypeDataOfTypeElement = (DeclaredTypeData) TypeData.detectType(processingEnv, typeElement.asType());
        final String simpleClassName;
        if (downcall.name().isBlank()) {
            final String string = declaredTypeDataOfTypeElement.name();
            if (string.startsWith("C")) {
                simpleClassName = string.substring(1);
            } else {
                processingEnv.getMessager().printError("""
                    Class name must start with C if the name is not specified. Current name: %s
                         Possible solutions:
                         1) Add C as a prefix. For example: C%1$s
                         2) Specify the name in @Downcall. For example: @Downcall(name = "%1$s")"""
                    .formatted(string));
                return;
            }
        } else {
            simpleClassName = downcall.name();
        }

        processDowncallType(typeElement);

        final SourceFile file = new SourceFile(declaredTypeDataOfTypeElement.packageName());
        file.addClass(simpleClassName, classSpec -> {
            if (document != null) {
                classSpec.setDocument(document);
            }
            classSpec.setFinal(!nonFinal);

            addFields(classSpec);
        });
        importData.imports()
            .stream()
            .filter(declaredTypeData -> !"java.lang".equals(declaredTypeData.packageName()))
            .map(DeclaredTypeData::toString)
            .sorted((s1, s2) -> {
                final boolean s1Java = s1.startsWith("java.");
                final boolean s2Java = s2.startsWith("java.");
                if (s1Java && !s2Java) {
                    return -1;
                }
                if (!s1Java && s2Java) {
                    return 1;
                }
                return s1.compareTo(s2);
            })
            .forEach(file::addImport);

        final JavaFileObject sourceFile = processingEnv.getFiler()
            .createSourceFile(declaredTypeDataOfTypeElement.packageName() + "." + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private void addLookup(TypeElement typeElement) {
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);
        final String loader = typeElement.getAnnotationMirrors().stream()
            .filter(m -> Downcall.class.getCanonicalName().equals(m.getAnnotationType().toString()))
            .findFirst()
            .orElseThrow()
            .getElementValues().entrySet().stream()
            .filter(e -> "loader()".equals(e.getKey().toString()))
            .findFirst()
            .map(e -> e.getValue().getValue().toString())
            .orElse(null);
        final String libname = downcall.libname();
        final String libnameConstExp = getConstExp(processingEnv, libname);
        final TypeElement loaderTypeElement;
        final Optional<ExecutableElement> annotatedMethod;
        if (loader == null) {
            loaderTypeElement = null;
            annotatedMethod = Optional.empty();
        } else {
            loaderTypeElement = processingEnv.getElementUtils().getTypeElement(loader);
            annotatedMethod = findAnnotatedMethod(loaderTypeElement,
                Loader.class,
                executableElement -> {
                    if (isSameClass(executableElement.getReturnType(), SymbolLookup.class)) {
                        final var parameters = executableElement.getParameters();
                        return parameters.size() == 1 && isString(parameters.getFirst().asType());
                    }
                    return false;
                });
            if (annotatedMethod.isEmpty()) {
                processingEnv.getMessager().printError("""
                    Couldn't find loader method in %s while %s required. Please mark it with @Loader"""
                    .formatted(loader, typeElement));
                return;
            }
        }
        fields.add(new FieldData(
            null,
            AccessModifier.PRIVATE,
            true,
            true,
            TypeData.fromClass(SymbolLookup.class),
            "_LOOKUP",
            loader == null ?
                importData -> new InvokeSpec(importData.simplifyOrImport(SymbolLookup.class), "libraryLookup")
                    .addArgument(libnameConstExp)
                    .addArgument(new InvokeSpec(importData.simplifyOrImport(Arena.class), "global")) :
                importData -> new InvokeSpec(importData.simplifyOrImport(
                    TypeData.detectType(processingEnv, loaderTypeElement.asType())
                ), annotatedMethod.get().getSimpleName().toString())
                    .addArgument(libnameConstExp)
        ));
    }

    private void addFields(ClassSpec spec) {
        fields.forEach(fieldData -> {
            final TypeData type = fieldData.type();
            spec.addField(new VariableStatement(
                importData.simplifyOrImport(type),
                fieldData.name(),
                fieldData.value().apply(importData)
            ).setDocument(fieldData.document())
                .setAccessModifier(fieldData.accessModifier())
                .setStatic(fieldData.staticField())
                .setFinal(fieldData.finalField()));
        });
    }

    private String getDocComment(ProcessingEnvironment env, Element e) {
        return env.getElementUtils().getDocComment(e);
    }

    private String getConstExp(ProcessingEnvironment env, Object v) {
        return env.getElementUtils().getConstantExpression(v);
    }
}
