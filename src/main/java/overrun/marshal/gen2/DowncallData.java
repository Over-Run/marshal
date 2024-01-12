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
import overrun.marshal.gen.struct.StructRef;
import overrun.marshal.gen1.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    private void processDowncallType(TypeElement typeElement, String simpleClassName) {
        final var enclosedElements = typeElement.getEnclosedElements();

        // annotations
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);

        document = getDocComment(typeElement);
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
                getDocComment(element),
                List.of(),
                AccessModifier.PUBLIC,
                true,
                true,
                TypeData.detectType(processingEnv, element.asType()),
                element.getSimpleName().toString(),
                _ -> Spec.literal(getConstExp(constantValue))
            ));
        });

        final var methods = ElementFilter.methodsIn(enclosedElements);
        if (!methods.isEmpty()) {
            // collect method handles
            final Map<String, MethodHandleData> methodHandleDataMap = LinkedHashMap.newLinkedHashMap(methods.size());
            methods.forEach(executableElement -> {
                if (executableElement.getAnnotation(Skip.class) != null ||
                    executableElement.getAnnotation(Overload.class) != null) {
                    return;
                }
                final Access access = executableElement.getAnnotation(Access.class);
                final String entrypoint = getMethodEntrypoint(executableElement);
                final TypeMirror returnType = executableElement.getReturnType();
                methodHandleDataMap.put(entrypoint,
                    new MethodHandleData(
                        executableElement,
                        access != null ? access.value() : AccessModifier.PUBLIC,
                        entrypoint,
                        TypeUse.valueLayout(processingEnv, returnType)
                            .map(typeUse -> importData -> {
                                final ByValue byValue = executableElement.getAnnotation(ByValue.class);
                                final StructRef structRef = executableElement.getAnnotation(StructRef.class);
                                final SizedSeg sizedSeg = executableElement.getAnnotation(SizedSeg.class);
                                final Sized sized = executableElement.getAnnotation(Sized.class);
                                final boolean structRefNotNull = structRef != null;
                                final boolean sizedSegNotNull = sizedSeg != null;
                                final boolean sizedNotNull = sized != null;
                                final Spec spec = typeUse.apply(importData);
                                if (structRefNotNull || sizedSegNotNull || sizedNotNull) {
                                    final InvokeSpec invokeSpec = new InvokeSpec(spec, "withTargetLayout");
                                    if (structRefNotNull) {
                                        final Spec layout = Spec.accessSpec(structRef.value(), "LAYOUT");
                                        return byValue != null ? layout : invokeSpec.addArgument(layout);
                                    }
                                    if (sizedSegNotNull) {
                                        return invokeSpec.addArgument(new InvokeSpec(
                                            importData.simplifyOrImport(MemoryLayout.class),
                                            "sequenceLayout"
                                        ).addArgument(getConstExp(sizedSeg.value()))
                                            .addArgument(TypeUse.valueLayout(byte.class).orElseThrow().apply(importData)));
                                    }
                                    return invokeSpec.addArgument(new InvokeSpec(
                                        importData.simplifyOrImport(MemoryLayout.class),
                                        "sequenceLayout"
                                    ).also(invokeSpec1 -> {
                                        invokeSpec1.addArgument(getConstExp(sized.value()));
                                        final Optional<TypeUse> seqLayout;
                                        if (returnType instanceof ArrayType arrayType) {
                                            seqLayout = TypeUse.valueLayout(processingEnv, arrayType.getComponentType());
                                        } else {
                                            seqLayout = TypeUse.valueLayout(byte.class);
                                        }
                                        invokeSpec1.addArgument(seqLayout.orElseThrow().apply(importData));
                                    }));
                                }
                                return spec;
                            }),
                        executableElement.getParameters()
                            .stream()
                            .filter(element -> element.getAnnotation(Skip.class) == null)
                            .map(element -> TypeUse.valueLayout(processingEnv, element).orElseThrow())
                            .toList(),
                        executableElement.getAnnotation(Default.class) != null
                    ));
            });

            // add linker and lookup
            final Predicate<String> containsKey = methodHandleDataMap::containsKey;
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

            // adds methods
            // method handles
            methodHandleDataMap.forEach((name, methodHandleData) -> fields.add(new FieldData(
                " The method handle of {@code " + name + "}.",
                List.of(),
                methodHandleData.accessModifier(),
                true,
                true,
                TypeData.fromClass(MethodHandle.class),
                methodHandleData.name(),
                importData -> {
                    final InvokeSpec invokeSpec = new InvokeSpec(
                        new InvokeSpec(lookupName, "find")
                            .addArgument(getConstExp(name)), "map"
                    ).addArgument(new LambdaSpec("s")
                        .addStatementThis(new InvokeSpec(linkerName, "downcallHandle")
                            .addArgument("s")
                            .also(downcallHandle -> {
                                final var returnType = methodHandleData.returnType();
                                final String methodName = returnType.isPresent() ? "of" : "ofVoid";
                                downcallHandle.addArgument(new InvokeSpec(importData.simplifyOrImport(FunctionDescriptor.class), methodName)
                                    .also(fdOf -> {
                                        returnType.ifPresent(typeUse -> {
                                            final Spec spec = typeUse.apply(importData);
                                            fdOf.addArgument(spec);
                                        });
                                        methodHandleData.parameterTypes().forEach(typeUse ->
                                            fdOf.addArgument(typeUse.apply(importData)));
                                    }));
                                final Critical critical = methodHandleData.executableElement().getAnnotation(Critical.class);
                                if (critical != null) {
                                    downcallHandle.addArgument(new InvokeSpec(importData.simplifyOrImport(Linker.Option.class), "critical")
                                        .addArgument(getConstExp(critical.allowHeapAccess())));
                                }
                            })));
                    if (methodHandleData.optional()) {
                        return new InvokeSpec(invokeSpec, "orElse").addArgument("null");
                    }
                    return new InvokeSpec(
                        invokeSpec, "orElseThrow"
                    );
                }
            )));

            // methods
            final var fieldNames = fields.stream().map(FieldData::name).toList();
            methods.forEach(executableElement -> {
                if (executableElement.getAnnotation(Skip.class) != null) {
                    return;
                }
                final Overload overload = executableElement.getAnnotation(Overload.class);
                final boolean isOverload = overload != null || containsNonDowncallType(executableElement);
                final MethodHandleData methodHandleData = methodHandleDataMap.get(getMethodEntrypoint(executableElement));
                if (methodHandleData == null) {
                    return;
                }

                if (isOverload) {
                    // TODO: 2024/1/12 squid233: Overload
                    return;
                }

                addDowncallMethod(simpleClassName, executableElement, fieldNames, methodHandleData);
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

        processDowncallType(typeElement, simpleClassName);

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

    private void addDowncallMethod(String simpleClassName,
                                   ExecutableElement executableElement,
                                   List<String> fieldNames,
                                   MethodHandleData methodHandleData) {
        final String methodHandleName = methodHandleData.name();
        final var returnType = TypeUse.toDowncallType(processingEnv, executableElement.getReturnType());

        final var parameters = executableElement.getParameters().stream()
            .filter(element -> element.getAnnotation(Skip.class) == null)
            .map(element -> new ParameterData(
                null,
                getElementAnnotations(PARAMETER_ANNOTATIONS, element),
                TypeUse.toDowncallType(processingEnv, element.asType()).orElseThrow(),
                element.getSimpleName().toString()
            ))
            .collect(Collectors.toList());
        final var parameterNames = parameters.stream()
            .map(ParameterData::name)
            .collect(Collectors.toList());

        if (executableElement.getAnnotation(ByValue.class) != null) {
            final String allocatorName = tryInsertUnderline("segmentAllocator", parameterNames::contains);
            parameters.addFirst(new ParameterData(
                "the segment allocator",
                List.of(),
                importData -> Spec.literal(importData.simplifyOrImport(SegmentAllocator.class)),
                allocatorName
            ));
            parameterNames.addFirst(allocatorName);
        }

        final boolean shouldAddInvoker = parameterNames.stream().anyMatch(fieldNames::contains);

        String docComment = getDocComment(executableElement);
        if (docComment != null) {
            docComment += parameters.stream()
                .filter(parameterData -> parameterData.document() != null)
                .map(parameterData -> " @param " + parameterData.name() + " " + parameterData.document())
                .collect(Collectors.joining("\n"));
        }
        functionDataList.add(new FunctionData(
            docComment,
            getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
            methodHandleData.accessModifier(),
            returnType.orElseGet(() -> _ -> Spec.literal("void")),
            executableElement.getSimpleName().toString(),
            parameters,
            List.of(
                importData -> new TryCatchStatement().also(tryCatchStatement -> {
                    final InvokeSpec invokeSpec = new InvokeSpec(shouldAddInvoker ?
                        Spec.accessSpec(simpleClassName, methodHandleName) :
                        Spec.literal(methodHandleName), "invokeExact");
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

                    final String exceptionName = tryInsertUnderline("e", s -> fieldNames.contains(s) || parameterNames.contains(s));
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
        final String libnameConstExp = getConstExp(libname);
        final TypeElement loaderTypeElement;
        final Optional<ExecutableElement> annotatedMethod;
        if (loader == null) {
            loaderTypeElement = null;
            annotatedMethod = Optional.empty();
        } else {
            loaderTypeElement = getTypeElementFromClass(processingEnv, loader);
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
                aClass));
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

    private boolean containsNonDowncallType(ExecutableElement executableElement) {
        return executableElement.getParameters().stream()
            .anyMatch(element -> {
                final TypeMirror type = element.asType();
                final TypeKind typeKind = type.getKind();
                return !(typeKind.isPrimitive() ||
                         typeKind == TypeKind.DECLARED && isSameClass(type, MemorySegment.class));
            });
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

    private String getDocComment(Element e) {
        return processingEnv.getElementUtils().getDocComment(e);
    }

    private String getConstExp(Object v) {
        return processingEnv.getElementUtils().getConstantExpression(v);
    }
}
