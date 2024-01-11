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

import overrun.marshal.gen.*;
import overrun.marshal.gen.struct.ByValue;
import overrun.marshal.gen1.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static overrun.marshal.internal.Util.*;

/**
 * Holds downcall fields and functions
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallData {
    private static final List<Class<? extends Annotation>> METHOD_ANNOTATIONS = List.of(
        ByValue.class,
        Critical.class,
        Default.class,
        SizedSeg.class,
        Sized.class,
        StrCharset.class
    );
    private static final List<Class<? extends Annotation>> PARAMETER_ANNOTATIONS = List.of(
        NullableRef.class,
        Ref.class,
        SizedSeg.class,
        Sized.class,
        StrCharset.class
    );
    private final ProcessingEnvironment processingEnv;
    private final ImportData imports = new ImportData(new ArrayList<>());
    private final List<FieldData> fields = new ArrayList<>();
    private final List<FunctionData> functionDataList = new ArrayList<>();
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
            if (element.getAnnotation(Skip.class) != null) {
                return;
            }
            final Object constantValue = element.getConstantValue();
            if (constantValue == null) {
                return;
            }
            fields.add(new FieldData(
                getDocComment(processingEnv, element),
                List.of(),
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
            final Map<String, MethodHandleData> methodHandleDataList = LinkedHashMap.newLinkedHashMap(methods.size());
            methods.forEach(executableElement -> {
                if (executableElement.getAnnotation(Skip.class) != null ||
                    executableElement.getAnnotation(Overload.class) != null) {
                    return;
                }
                final Access access = executableElement.getAnnotation(Access.class);
                final String entrypoint = getMethodEntrypoint(executableElement);
                methodHandleDataList.put(entrypoint,
                    new MethodHandleData(
                        executableElement,
                        access != null ? access.value() : AccessModifier.PUBLIC,
                        entrypoint,
                        TypeUse.valueLayout(executableElement.getReturnType()),
                        executableElement.getParameters()
                            .stream()
                            .filter(element -> element.getAnnotation(Skip.class) == null)
                            .map(TypeUse::valueLayout)
                            .map(Optional::orElseThrow)
                            .toList(),
                        executableElement.getAnnotation(Default.class) != null
                    ));
            });

            // add linker and lookup
            final Predicate<String> containsKey = methodHandleDataList::containsKey;
            final String lookupName = addLookup(typeElement, containsKey);
            final String linkerName = tryInsertUnderline("LINKER", containsKey);
            fields.add(new FieldData(
                null,
                List.of(),
                AccessModifier.PRIVATE,
                true,
                true,
                TypeData.fromClass(Linker.class),
                linkerName,
                importData -> new InvokeSpec(importData.simplifyOrImport(Linker.class), "nativeLinker")
            ));

            // add methods
            final var fieldNames = fields.stream().map(FieldData::name).toList();
            methodHandleDataList.values().forEach(methodHandleData -> {
                final String name = methodHandleData.name();
                fields.add(new FieldData(
                    " The method handle of {@code " + name + "}.",
                    List.of(),
                    methodHandleData.accessModifier(),
                    true,
                    true,
                    TypeData.fromClass(MethodHandle.class),
                    name,
                    importData -> {
                        final InvokeSpec invokeSpec = new InvokeSpec(
                            new InvokeSpec(lookupName, "find")
                                .addArgument(getConstExp(processingEnv, name)), "map"
                        ).addArgument(new LambdaSpec("s")
                            .addStatementThis(new InvokeSpec(linkerName, "downcallHandle")
                                .addArgument("s")
                                .also(invokeSpec1 -> {
                                    final var returnType = methodHandleData.returnType();
                                    final String methodName = returnType.isPresent() ? "of" : "ofVoid";
                                    invokeSpec1.addArgument(new InvokeSpec(importData.simplifyOrImport(FunctionDescriptor.class), methodName)
                                        .also(invokeSpec2 -> {
                                            returnType.ifPresent(typeUse -> invokeSpec2.addArgument(typeUse.apply(importData)));
                                            methodHandleData.parameterTypes().forEach(typeUse ->
                                                invokeSpec2.addArgument(typeUse.apply(importData)));
                                        }));
                                })));
                        if (methodHandleData.optional()) {
                            return new InvokeSpec(invokeSpec, "orElse").addArgument("null");
                        }
                        return new InvokeSpec(
                            invokeSpec, "orElseThrow"
                        );
                    }
                ));

                final ExecutableElement executableElement = methodHandleData.executableElement();
                final var returnType = TypeUse.toDowncallType(executableElement.getReturnType());
                final var parameters = executableElement.getParameters().stream()
                    .filter(element -> element.getAnnotation(Skip.class) == null)
                    .map(element -> new ParameterData(
                        null,
                        getElementAnnotations(PARAMETER_ANNOTATIONS, element),
                        TypeUse.toDowncallType(element.asType()).orElseThrow(),
                        tryInsertUnderline(element.getSimpleName().toString(), fieldNames::contains)
                    ))
                    .toList();
                final var parameterNames = parameters.stream().map(ParameterData::name).toList();
                functionDataList.add(new FunctionData(
                    getDocComment(processingEnv, executableElement),
                    getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
                    methodHandleData.accessModifier(),
                    returnType.orElseGet(() -> _ -> Spec.literal("void")),
                    executableElement.getSimpleName().toString(),
                    parameters,
                    List.of(
                        importData -> new TryCatchStatement().also(tryCatchStatement -> {
                            final String exceptionName = tryInsertUnderline("e", s -> fieldNames.contains(s) || parameterNames.contains(s));
                            final String methodHandleName = methodHandleData.name();
                            final InvokeSpec invokeSpec = new InvokeSpec(methodHandleName, "invokeExact");
                            parameterNames.forEach(invokeSpec::addArgument);
                            final Spec returnSpec = returnType
                                .map(typeUse -> Spec.returnStatement(Spec.cast(typeUse.apply(importData), invokeSpec)))
                                .orElseGet(() -> Spec.statement(invokeSpec));
                            final Spec optionalSpec;
                            final Default defaultAnnotation = executableElement.getAnnotation(Default.class);
                            if (defaultAnnotation != null) {
                                final IfStatement ifStatement = new IfStatement(Spec.notNullSpec(methodHandleName));
                                ifStatement.addStatement(returnSpec);
                                final String defaultValue = defaultAnnotation.value();
                                if (!defaultValue.isBlank()) {
                                    ifStatement.addElseClause(ElseClause.of(), elseClause ->
                                        elseClause.addStatement(Spec.returnStatement(Spec.literal(defaultValue)))
                                    );
                                }
                                optionalSpec = ifStatement;
                            } else {
                                optionalSpec = returnSpec;
                            }
                            tryCatchStatement.addStatement(optionalSpec);
                            tryCatchStatement.addCatchClause(new CatchClause(
                                importData.simplifyOrImport(Throwable.class),
                                exceptionName
                            ), catchClause ->
                                catchClause.addStatement(Spec.throwStatement(new ConstructSpec(importData.simplifyOrImport(RuntimeException.class))
                                    .addArgument(Spec.literal(exceptionName)))));
                        })
                    )
                ));
            });
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
                         1) Add C as a prefix e.g. C%1$s
                         2) Specify the name in @Downcall e.g. @Downcall(name = "%1$s")"""
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
            addMethods(classSpec);
        });
        imports.imports()
            .stream()
            .filter(declaredTypeData -> !"java.lang".equals(declaredTypeData.packageName()))
            .map(DeclaredTypeData::toString)
            .sorted((s1, s2) -> {
                final boolean s1Java = s1.startsWith("java.");
                final boolean s2Java = s2.startsWith("java.");
                if (s1Java && !s2Java) {
                    return 1;
                }
                if (!s1Java && s2Java) {
                    return -1;
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

    private String addLookup(TypeElement typeElement, Predicate<String> insertUnderlineTest) {
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);
        final String loader = typeElement.getAnnotationMirrors().stream()
            .filter(m -> Downcall.class.getCanonicalName().equals(m.getAnnotationType().toString()))
            .findFirst()
            .orElseThrow()
            .getElementValues().entrySet().stream()
            .filter(e -> "loader".equals(e.getKey().getSimpleName().toString()))
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
                return null;
            }
        }
        final String s = tryInsertUnderline("LOOKUP", insertUnderlineTest);
        fields.add(new FieldData(
            null,
            List.of(),
            AccessModifier.PRIVATE,
            true,
            true,
            TypeData.fromClass(SymbolLookup.class),
            s,
            loader == null ?
                importData -> new InvokeSpec(importData.simplifyOrImport(SymbolLookup.class), "libraryLookup")
                    .addArgument(libnameConstExp)
                    .addArgument(new InvokeSpec(importData.simplifyOrImport(Arena.class), "global")) :
                importData -> new InvokeSpec(importData.simplifyOrImport(
                    processingEnv, loaderTypeElement.asType()
                ), annotatedMethod.get().getSimpleName().toString())
                    .addArgument(libnameConstExp)
        ));
        return s;
    }

    private Stream<? extends AnnotationMirror> findElementAnnotations(
        List<? extends AnnotationMirror> annotationMirrors,
        Class<? extends Annotation> aClass
    ) {
        return annotationMirrors.stream()
            .filter(mirror -> isAExtendsB(processingEnv,
                mirror.getAnnotationType(),
                getTypeElementFromClass(processingEnv, aClass).asType()));
    }

    private List<AnnotationData> getElementAnnotations(List<Class<? extends Annotation>> list, Element e) {
        final var mirrors = e.getAnnotationMirrors();
        return list.stream()
            .filter(aClass -> e.getAnnotation(aClass) != null)
            .<AnnotationData>mapMulti((aClass, consumer) -> findElementAnnotations(mirrors, aClass)
                .map(mirror -> new AnnotationData(e.getAnnotation(aClass), mirror))
                .forEach(consumer))
            .toList();
    }

    private String getMethodEntrypoint(ExecutableElement executableElement) {
        final Entrypoint entrypoint = executableElement.getAnnotation(Entrypoint.class);
        if (entrypoint != null) {
            final String value = entrypoint.value();
            if (!value.isBlank()) {
                return value;
            }
        }
        return executableElement.getSimpleName().toString();
    }

    private void addFields(ClassSpec spec) {
        fields.forEach(fieldData -> {
            final TypeData type = fieldData.type();
            spec.addField(new VariableStatement(
                imports.simplifyOrImport(type),
                fieldData.name(),
                fieldData.value().apply(imports)
            ).setDocument(fieldData.document())
                .setAccessModifier(fieldData.accessModifier())
                .setStatic(fieldData.staticField())
                .setFinal(fieldData.finalField()));
        });
    }

    private void addMethods(ClassSpec spec) {
        functionDataList.forEach(functionData ->
            spec.addMethod(new MethodSpec(functionData.returnType().apply(imports), functionData.name()), methodSpec -> {
                methodSpec.setDocument(functionData.document());
                functionData.annotations().forEach(annotationData -> {
                    final AnnotationMirror annotationMirror = annotationData.mirror();
                    methodSpec.addAnnotation(new AnnotationSpec(
                        imports.simplifyOrImport(processingEnv, annotationMirror.getAnnotationType())
                    ).also(annotationSpec ->
                        annotationMirror.getElementValues().forEach((executableElement, annotationValue) ->
                            annotationSpec.addArgument(executableElement.getSimpleName().toString(), annotationValue.toString()))));
                });
                methodSpec.setAccessModifier(functionData.accessModifier());
                methodSpec.setStatic(true);
                functionData.parameters().forEach(parameterData ->
                    methodSpec.addParameter(new ParameterSpec(parameterData.type().apply(imports), parameterData.name()))
                );
                functionData.statements().forEach(typeUse ->
                    methodSpec.addStatement(typeUse.apply(imports)));
            }));
    }

    private String getDocComment(ProcessingEnvironment env, Element e) {
        return env.getElementUtils().getDocComment(e);
    }

    private String getConstExp(ProcessingEnvironment env, Object v) {
        return env.getElementUtils().getConstantExpression(v);
    }
}
