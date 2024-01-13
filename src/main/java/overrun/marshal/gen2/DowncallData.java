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

import overrun.marshal.*;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
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

    private void processDowncallType(TypeElement typeElement, String simpleClassName) {
        final var enclosedElements = typeElement.getEnclosedElements();

        // annotations
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);

        document = getDocComment(typeElement);
        nonFinal = downcall.nonFinal();

        // process fields
        skipAnnotated(ElementFilter.fieldsIn(enclosedElements)).forEach(element -> {
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
        if (methods.isEmpty()) {
            return;
        }
        // collect method handles
        final Map<String, MethodHandleData> methodHandleDataMap = LinkedHashMap.newLinkedHashMap(methods.size());
        skipAnnotated(methods)
            .filter(executableElement -> executableElement.getAnnotation(Overload.class) == null)
            .forEach(executableElement -> {
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
                                        if (returnType.getKind() == TypeKind.ARRAY &&
                                            returnType instanceof ArrayType arrayType) {
                                            seqLayout = TypeUse.valueLayout(processingEnv, arrayType.getComponentType());
                                        } else {
                                            seqLayout = TypeUse.valueLayout(byte.class);
                                        }
                                        invokeSpec1.addArgument(seqLayout.orElseThrow().apply(importData));
                                    }));
                                }
                                return spec;
                            }),
                        skipAnnotated(executableElement.getParameters())
                            .map(element -> TypeUse.valueLayout(processingEnv, element))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
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
        final List<MethodHandleData> addedMethodHandleInvoker = new ArrayList<>(methodHandleDataMap.size());
        skipAnnotated(methods).forEach(executableElement -> {
            final Overload overload = executableElement.getAnnotation(Overload.class);
            final MethodHandleData methodHandleData = methodHandleDataMap.get(getMethodEntrypoint(executableElement));
            if (methodHandleData == null) {
                return;
            }

            final Custom custom = executableElement.getAnnotation(Custom.class);
            if (custom != null) {
                addCustomMethod(executableElement, methodHandleData, custom);
            } else {
                final String methodName = executableElement.getSimpleName().toString();
                final boolean containsNonDowncallType = containsNonDowncallType(executableElement);
                final boolean shouldGenOverload = overload != null || containsNonDowncallType;

                String finalMethodName = methodName;
                if (overload == null && !addedMethodHandleInvoker.contains(methodHandleData)) {
                    addedMethodHandleInvoker.add(methodHandleData);
                    if (containsNonDowncallType) {
                        boolean shouldAddPrefix = true;
                        if (parameterContainsNonDowncallType(executableElement)) {
                            shouldAddPrefix = false;
                        } else {
                            final var rawParameterList = skipAnnotated(executableElement.getParameters()).toList();
                            if (executableElement.getAnnotation(ByValue.class) == null &&
                                !rawParameterList.isEmpty() &&
                                isAExtendsB(processingEnv, rawParameterList.getFirst().asType(), SegmentAllocator.class)) {
                                shouldAddPrefix = false;
                            }
                        }
                        if (shouldAddPrefix) {
                            final String prefixed = "n" + methodName;
                            finalMethodName = "n" + tryInsertUnderline(methodName, _ -> functionDataList.stream()
                                .anyMatch(functionData -> prefixed.equals(functionData.name())));
                        }
                    }

                    addDowncallMethod(simpleClassName,
                        executableElement,
                        finalMethodName,
                        fieldNames,
                        methodHandleData);
                }

                if (shouldGenOverload) {
                    final String downcallName;
                    if (overload == null) {
                        downcallName = finalMethodName;
                    } else {
                        downcallName = getMethodAnnotationString(executableElement, Overload.class, Overload::value);
                    }

                    addOverloadMethod(simpleClassName,
                        executableElement,
                        methodName,
                        downcallName,
                        fieldNames,
                        methodHandleData);
                }
            }
        });
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
                    .formatted(string), typeElement);
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

    private void addOverloadMethod(
        String simpleClassName,
        ExecutableElement executableElement,
        String methodName,
        String downcallName,
        List<String> fieldNames,
        MethodHandleData methodHandleData) {
        final var rawParameterList = skipAnnotated(executableElement.getParameters()).toList();
        final var parameterDataList = parametersToParameterDataList(rawParameterList);
        final var parameterNames = parameterDataList.stream()
            .map(ParameterData::name)
            .toList();

        final int skipFirst = shouldSkipFirstParameter(executableElement, rawParameterList, false);
        if (skipFirst < 0) {
            return;
        }

        final List<String> variablesInScope = new ArrayList<>(parameterNames);

        final String memoryStackName = tryInsertUnderline("stack", s -> variablesInScope.stream().anyMatch(s::equals));
        final boolean hasAllocatorParameter =
            !parameterDataList.isEmpty() &&
            isAExtendsB(processingEnv, parameterDataList.getFirst().element().asType(), SegmentAllocator.class);
        final String allocatorParameter = hasAllocatorParameter ?
            parameterDataList.getFirst().name() :
            memoryStackName;
        final boolean shouldWrapWithMemoryStack =
            rawParameterList.stream().anyMatch(element -> useAllocator(element.asType())) &&
            !hasAllocatorParameter;
        if (shouldWrapWithMemoryStack) {
            variablesInScope.add(memoryStackName);
        }

        final boolean shouldAddInvoker = variablesInScope.stream().anyMatch(fieldNames::contains);
        final TypeMirror returnType = executableElement.getReturnType();
        final boolean returnVoid = returnType.getKind() == TypeKind.VOID;

        final List<TypeUse> statements = new ArrayList<>();

        // check array size of @Sized array
        rawParameterList.stream()
            .filter(element -> element.getAnnotation(Sized.class) != null)
            .forEach(element -> {
                final Sized sized = element.getAnnotation(Sized.class);
                statements.add(importData ->
                    Spec.statement(new InvokeSpec(importData.simplifyOrImport(Checks.class), "checkArraySize")
                        .addArgument(getConstExp(sized.value()))
                        .addArgument(Spec.accessSpec(element.getSimpleName().toString(), "length"))));
            });

        // ref segments
        final Map<String, String> refNameMap = HashMap.newHashMap(Math.toIntExact(rawParameterList.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .count()));
        rawParameterList.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .forEach(element -> statements.add(importData -> {
                final String elementName = element.getSimpleName().toString();
                final String refSegName = tryInsertUnderline(elementName,
                    s -> variablesInScope.stream().anyMatch(s::equals));
                variablesInScope.add(refSegName);
                refNameMap.put(elementName, refSegName);

                final Spec allocateSpec = wrapParameter(importData, allocatorParameter, element, false, null);

                return new VariableStatement("var", refSegName, allocateSpec)
                    .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .setFinal(true);
            }));

        // invocation
        final TypeUse invokeTypeUse = importData -> {
            final InvokeSpec invokeSpec = new InvokeSpec(shouldAddInvoker ? Spec.literal(simpleClassName) : null, downcallName);
            // wrap parameters
            var stream = parameterDataList.stream();
            if (skipFirst > 0) {
                stream = stream.skip(1);
            }
            stream.map(ParameterData::element)
                .map(element -> wrapParameter(importData, allocatorParameter, element, true, refNameMap::get))
                .forEach(invokeSpec::addArgument);
            return invokeSpec;
        };

        // store result
        final boolean shouldStoreResult =
            canConvertToAddress(executableElement, ExecutableElement::getReturnType) ||
            (!returnVoid &&
             rawParameterList.stream().anyMatch(element -> element.getAnnotation(Ref.class) != null));
        final String resultVarName;
        if (shouldStoreResult) {
            resultVarName = tryInsertUnderline("result", s -> variablesInScope.stream().anyMatch(s::equals));
            variablesInScope.add(resultVarName);
            statements.add(importData -> new VariableStatement("var", resultVarName, invokeTypeUse.apply(importData))
                .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                .setFinal(true));
        } else {
            resultVarName = null;
            if (returnVoid) {
                statements.add(importData -> Spec.statement(invokeTypeUse.apply(importData)));
            } else {
                statements.add(importData -> Spec.returnStatement(wrapReturnValue(importData,
                    invokeTypeUse.apply(importData),
                    executableElement)));
            }
        }

        // passing to ref
        rawParameterList.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .forEach(element -> statements.add(importData ->
                copyRefResult(importData, element, refNameMap::get)));

        // return result
        if (shouldStoreResult) {
            statements.add(importData -> Spec.returnStatement(wrapReturnValue(importData,
                Spec.literal(resultVarName),
                executableElement)));
        }

        final StructRef structRef = executableElement.getAnnotation(StructRef.class);
        functionDataList.add(new FunctionData(
            getDocComment(executableElement),
            getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
            methodHandleData.accessModifier(),
            structRef != null ?
                _ -> Spec.literal(structRef.value()) :
                importData -> Spec.literal(importData.simplifyOrImport(processingEnv, returnType)),
            methodName,
            parameterDataList,
            shouldWrapWithMemoryStack ?
                List.of(importData -> new TryWithResourceStatement(
                    new VariableStatement("var",
                        memoryStackName,
                        new InvokeSpec(importData.simplifyOrImport(MemoryStack.class), "stackPush"))
                        .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                        .setFinal(true)
                        .setAddSemicolon(false)
                ).also(tryWithResourceStatement ->
                    statements.forEach(typeUse ->
                        tryWithResourceStatement.addStatement(typeUse.apply(importData))))) :
                statements
        ));
    }

    private void addDowncallMethod(
        String simpleClassName,
        ExecutableElement executableElement,
        String methodName,
        List<String> fieldNames,
        MethodHandleData methodHandleData) {
        final String methodHandleName = methodHandleData.name();
        final var returnType = TypeUse.toDowncallType(processingEnv, executableElement, ExecutableElement::getReturnType);
        final boolean returnByValue = executableElement.getAnnotation(ByValue.class) != null;

        final var rawParameterList = skipAnnotated(executableElement.getParameters()).toList();
        final int skipFirst = shouldSkipFirstParameter(executableElement, rawParameterList, true);
        if (skipFirst < 0) {
            return;
        }
        var stream = rawParameterList.stream();
        if (skipFirst > 0) {
            stream = stream.skip(1);
        }
        final var parameters = stream.toList();
        final var parameterDataList = parameters.stream()
            .map(element -> new ParameterData(
                element,
                getElementAnnotations(PARAMETER_ANNOTATIONS, element),
                TypeUse.toDowncallType(processingEnv, element).orElseThrow(),
                element.getSimpleName().toString()
            ))
            .toList();
        final var parameterNames = parameterDataList.stream()
            .map(ParameterData::name)
            .toList();

        final boolean shouldAddInvoker = parameterNames.stream().anyMatch(fieldNames::contains);

        functionDataList.add(new FunctionData(
            getDocComment(executableElement),
            getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
            methodHandleData.accessModifier(),
            returnType.orElseGet(() -> _ -> Spec.literal("void")),
            methodName,
            parameterDataList,
            List.of(
                importData -> new TryCatchStatement().also(tryCatchStatement -> {
                    final InvokeSpec invokeSpec = new InvokeSpec(shouldAddInvoker ?
                        Spec.accessSpec(simpleClassName, methodHandleName) :
                        Spec.literal(methodHandleName), "invokeExact");
                    var stream1 = parameterNames.stream();
                    if (returnByValue) {
                        final ParameterData first = parameterDataList.getFirst();
                        if (!isSameClass(first.element().asType(), SegmentAllocator.class)) {
                            invokeSpec.addArgument(Spec.cast(importData.simplifyOrImport(SegmentAllocator.class),
                                Spec.literal(first.name())));
                            stream1 = stream1.skip(1);
                        }
                    }
                    stream1.forEach(invokeSpec::addArgument);

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

    private int shouldSkipFirstParameter(ExecutableElement executableElement,
                                         List<? extends VariableElement> rawParameterList,
                                         boolean printError) {
        final boolean returnByValue = executableElement.getAnnotation(ByValue.class) != null;
        final boolean rawParamListNotEmpty = !rawParameterList.isEmpty();
        final boolean firstParamArena =
            rawParamListNotEmpty &&
            isAExtendsB(processingEnv, rawParameterList.getFirst().asType(), Arena.class);
        final boolean firstParamSegmentAllocator =
            firstParamArena ||
            (rawParamListNotEmpty &&
             isAExtendsB(processingEnv, rawParameterList.getFirst().asType(), SegmentAllocator.class));
        if (rawParameterList.stream()
            .anyMatch(element -> isAExtendsB(processingEnv, element.asType(), Upcall.class))) {
            if (firstParamArena) {
                return 1;
            }
            if (printError) {
                processingEnv.getMessager().printError("The first parameter of method with upcall must be Arena", executableElement);
            }
            return -1;
        }
        if (returnByValue) {
            if (firstParamSegmentAllocator) {
                return 0;
            }
            if (printError) {
                processingEnv.getMessager().printError("The first parameter of method marked as @ByValue must be SegmentAllocator", executableElement);
            }
            return -1;
        }
        return firstParamSegmentAllocator ? 1 : 0;
    }

    private void addCustomMethod(ExecutableElement executableElement,
                                 MethodHandleData methodHandleData,
                                 Custom custom) {
        functionDataList.add(new FunctionData(
            getDocComment(executableElement),
            getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
            methodHandleData.accessModifier(),
            importData -> Spec.literal(importData.simplifyOrImport(processingEnv, executableElement.getReturnType())),
            executableElement.getSimpleName().toString(),
            parametersToParameterDataList(skipAnnotated(executableElement.getParameters()).toList()),
            List.of(_ -> Spec.indented(custom.value()))
        ));
    }

    private String addLookup(TypeElement typeElement, Predicate<String> insertUnderlineTest) {
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);
        final String loader = typeElement.getAnnotationMirrors().stream()
            .filter(m -> isSameClass(m.getAnnotationType(), Downcall.class))
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
                        return parameters.size() == 1 && isSameClass(parameters.getFirst().asType(), String.class);
                    }
                    return false;
                });
            if (annotatedMethod.isEmpty()) {
                processingEnv.getMessager().printError("Couldn't find loader method in %s. Please mark it with @Loader"
                        .formatted(loader),
                    typeElement);
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

    private Spec copyRefResult(
        ImportData importData,
        Element element,
        Function<String, String> refFunction
    ) {
        final TypeMirror typeMirror = element.asType();
        final String name = element.getSimpleName().toString();
        final Spec spec;
        if (isBooleanArray(typeMirror)) {
            spec = new InvokeSpec(importData.simplifyOrImport(BoolHelper.class), "copy")
                .addArgument(refFunction.apply(name))
                .addArgument(name);
        } else if (isPrimitiveArray(typeMirror)) {
            spec = new InvokeSpec(importData.simplifyOrImport(MemorySegment.class), "copy")
                .addArgument(refFunction.apply(name))
                .addArgument(TypeUse.valueLayout(processingEnv, getArrayComponentType(typeMirror))
                    .orElseThrow()
                    .apply(importData))
                .addArgument(getConstExp(0L))
                .addArgument(name)
                .addArgument(getConstExp(0))
                .addArgument(Spec.accessSpec(name, "length"));
        } else if (isStringArray(typeMirror)) {
            spec = new InvokeSpec(importData.simplifyOrImport(StrHelper.class), "copy")
                .addArgument(refFunction.apply(name))
                .addArgument(name)
                .addArgument(createCharset(getCharset(element)).apply(importData));
        } else {
            processingEnv.getMessager().printWarning("Using @Ref on unknown type %s".formatted(typeMirror), element);
            spec = null;
        }

        final Spec statement = spec != null ? Spec.statement(spec) : Spec.literal("");
        if (element.getAnnotation(NullableRef.class) != null) {
            final IfStatement ifStatement = new IfStatement(Spec.notNullSpec(name));
            ifStatement.addStatement(statement);
            return ifStatement;
        }
        return statement;
    }

    private Spec wrapReturnValue(
        ImportData importData,
        Spec resultSpec,
        ExecutableElement executableElement
    ) {
        final Spec spec;
        final StructRef structRef = executableElement.getAnnotation(StructRef.class);
        if (structRef != null) {
            spec = new ConstructSpec(structRef.value()).addArgument(resultSpec);
        } else {
            final TypeMirror typeMirror = executableElement.getReturnType();
            if (isBooleanArray(typeMirror)) {
                spec = new InvokeSpec(importData.simplifyOrImport(BoolHelper.class), "toArray").addArgument(resultSpec);
            } else if (isPrimitiveArray(typeMirror)) {
                spec = new InvokeSpec(resultSpec, "toArray")
                    .addArgument(TypeUse.valueLayout(processingEnv, getArrayComponentType(typeMirror))
                        .orElseThrow()
                        .apply(importData));
            } else if (isStringArray(typeMirror)) {
                spec = new InvokeSpec(importData.simplifyOrImport(StrHelper.class), "toArray")
                    .addArgument(resultSpec)
                    .addArgument(createCharset(getCharset(executableElement)).apply(importData));
            } else if (isSameClass(typeMirror, String.class)) {
                final InvokeSpec invokeSpec = new InvokeSpec(resultSpec, "getString").addArgument(getConstExp(0L));
                if (executableElement.getAnnotation(StrCharset.class) != null) {
                    invokeSpec.addArgument(createCharset(getCharset(executableElement)).apply(importData));
                }
                spec = invokeSpec;
            } else if (isAExtendsB(processingEnv, typeMirror, Upcall.class)) {
                final var annotatedMethod = findAnnotatedMethod(
                    (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror),
                    Upcall.Wrapper.class,
                    executableElement1 -> {
                        if (isAExtendsB(processingEnv, executableElement1.getReturnType(), typeMirror)) {
                            final var parameters = executableElement1.getParameters();
                            return parameters.size() == 1 && isSameClass(parameters.getFirst().asType(), MemorySegment.class);
                        }
                        return false;
                    });
                spec = annotatedMethod
                    .<Spec>map(executableElement1 -> new InvokeSpec(importData.simplifyOrImport(processingEnv, typeMirror),
                        executableElement1.getSimpleName().toString())
                        .addArgument(resultSpec))
                    .orElseGet(() -> {
                        processingEnv.getMessager().printError(
                            "Couldn't find wrapper in %s. Please mark it with @Wrapper".formatted(typeMirror),
                            executableElement
                        );
                        return resultSpec;
                    });
            } else if (isAExtendsB(processingEnv, typeMirror, CEnum.class)) {
                final var annotatedMethod = findAnnotatedMethod(
                    (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror),
                    CEnum.Wrapper.class,
                    executableElement1 -> {
                        if (isAExtendsB(processingEnv, executableElement1.getReturnType(), typeMirror)) {
                            final var parameters = executableElement1.getParameters();
                            return parameters.size() == 1 && isSameClass(parameters.getFirst().asType(), int.class);
                        }
                        return false;
                    });
                spec = annotatedMethod
                    .<Spec>map(executableElement1 -> new InvokeSpec(importData.simplifyOrImport(processingEnv, typeMirror),
                        executableElement1.getSimpleName().toString())
                        .addArgument(resultSpec))
                    .orElseGet(() -> {
                        processingEnv.getMessager().printError(
                            "Couldn't find wrapper in %s. Please mark it with @Wrapper".formatted(typeMirror),
                            executableElement
                        );
                        return resultSpec;
                    });
            } else {
                spec = resultSpec;
            }
        }
        return canConvertToAddress(executableElement, ExecutableElement::getReturnType) ?
            Spec.ternaryOp(Spec.neqSpec(new InvokeSpec(resultSpec, "address"), Spec.literal("0L")),
                spec,
                Spec.literal("null")) :
            spec;
    }

    private Spec wrapParameter(
        ImportData importData,
        String allocatorParameter,
        Element element,
        boolean usingRef,
        Function<String, String> refFunction
    ) {
        final Spec spec;
        final String name = element.getSimpleName().toString();
        if (element.getAnnotation(StructRef.class) != null) {
            spec = new InvokeSpec(name, "segment");
        } else if (usingRef && element.getAnnotation(Ref.class) != null) {
            spec = Spec.literal(refFunction.apply(name));
        } else {
            final TypeMirror typeMirror = element.asType();
            if (isBooleanArray(typeMirror)) {
                spec = new InvokeSpec(importData.simplifyOrImport(BoolHelper.class), "of")
                    .addArgument(allocatorParameter)
                    .addArgument(name);
            } else if (isPrimitiveArray(typeMirror)) {
                spec = new InvokeSpec(allocatorParameter, "allocateFrom")
                    .addArgument(TypeUse.valueLayout(processingEnv, getArrayComponentType(typeMirror))
                        .orElseThrow()
                        .apply(importData))
                    .addArgument(name);
            } else if (isStringArray(typeMirror)) {
                spec = new InvokeSpec(importData.simplifyOrImport(StrHelper.class), "of")
                    .addArgument(allocatorParameter)
                    .addArgument(name)
                    .addArgument(createCharset(getCharset(element)).apply(importData));
            } else if (isSameClass(typeMirror, String.class)) {
                final InvokeSpec invokeSpec = new InvokeSpec(allocatorParameter, "allocateFrom")
                    .addArgument(name);
                if (element.getAnnotation(StrCharset.class) != null) {
                    invokeSpec.addArgument(createCharset(getCharset(element)).apply(importData));
                }
                spec = invokeSpec;
            } else if (isAExtendsB(processingEnv, typeMirror, Upcall.class)) {
                spec = new InvokeSpec(name, "stub").addArgument(allocatorParameter);
            } else if (isAExtendsB(processingEnv, typeMirror, CEnum.class)) {
                spec = new InvokeSpec(name, "value");
            } else {
                spec = Spec.literal(name);
            }
        }
        return element.getAnnotation(NullableRef.class) != null ?
            Spec.ternaryOp(Spec.notNullSpec(name),
                spec,
                Spec.accessSpec(importData.simplifyOrImport(MemorySegment.class), "NULL")) :
            spec;
    }

    private boolean useAllocator(TypeMirror typeMirror) {
        return isBooleanArray(typeMirror) ||
               isPrimitiveArray(typeMirror) ||
               isStringArray(typeMirror) ||
               isSameClass(typeMirror, String.class) ||
               isAExtendsB(processingEnv, typeMirror, Upcall.class);
    }

    private List<ParameterData> parametersToParameterDataList(List<? extends VariableElement> parameters) {
        return parameters.stream()
            .map(element -> new ParameterData(
                element,
                getElementAnnotations(PARAMETER_ANNOTATIONS, element),
                importData -> {
                    final StructRef structRef = element.getAnnotation(StructRef.class);
                    if (structRef != null) {
                        return Spec.literal(structRef.value());
                    }
                    return Spec.literal(importData.simplifyOrImport(processingEnv, element.asType()));
                },
                element.getSimpleName().toString()
            ))
            .toList();
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

    private TypeUse createCharset(String name) {
        return importData -> {
            final String upperCase = name.toUpperCase(Locale.ROOT);
            return switch (upperCase) {
                case "UTF-8", "ISO-8859-1", "US-ASCII",
                    "UTF-16", "UTF-16BE", "UTF-16LE",
                    "UTF-32", "UTF-32BE", "UTF-32LE" ->
                    Spec.accessSpec(importData.simplifyOrImport(StandardCharsets.class), upperCase.replace('-', '_'));
                case "UTF_8", "ISO_8859_1", "US_ASCII",
                    "UTF_16", "UTF_16BE", "UTF_16LE",
                    "UTF_32", "UTF_32BE", "UTF_32LE" ->
                    Spec.accessSpec(importData.simplifyOrImport(StandardCharsets.class), upperCase);
                default -> new InvokeSpec(importData.simplifyOrImport(Charset.class), "forName")
                    .addArgument(getConstExp(name));
            };
        };
    }

    private static String getCharset(Element element) {
        final StrCharset strCharset = element.getAnnotation(StrCharset.class);
        if (strCharset != null) {
            final String value = strCharset.value();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "UTF-8";
    }

    private static String getMethodEntrypoint(ExecutableElement executableElement) {
        return getMethodAnnotationString(executableElement, Entrypoint.class, Entrypoint::value);
    }

    private static <T extends Annotation> String getMethodAnnotationString(
        ExecutableElement executableElement,
        Class<T> aClass,
        Function<T, String> function) {
        final T annotation = executableElement.getAnnotation(aClass);
        if (annotation != null) {
            final String value = function.apply(annotation);
            if (!value.isBlank()) {
                return value;
            }
        }
        return executableElement.getSimpleName().toString();
    }

    private <T extends Element> boolean canConvertToAddress(T element, Function<T, TypeMirror> function) {
        if (element.getAnnotation(StructRef.class) != null) {
            return true;
        }
        final TypeMirror type = function.apply(element);
        final TypeKind typeKind = type.getKind();
        final boolean nonAddressType =
            (typeKind.isPrimitive()) ||
            (typeKind == TypeKind.VOID) ||
            (typeKind == TypeKind.DECLARED &&
             (isSameClass(type, MemorySegment.class) ||
              isAExtendsB(processingEnv, type, SegmentAllocator.class) ||
              isAExtendsB(processingEnv, type, CEnum.class))
            );
        return !nonAddressType;
    }

    private <T extends Element> boolean isNotDowncallType(T element, Function<T, TypeMirror> function) {
        if (element.getAnnotation(StructRef.class) != null) {
            return true;
        }
        final TypeMirror type = function.apply(element);
        final TypeKind typeKind = type.getKind();
        final boolean downcallType =
            (typeKind.isPrimitive()) ||
            (typeKind == TypeKind.VOID) ||
            (typeKind == TypeKind.DECLARED &&
             (isSameClass(type, MemorySegment.class) ||
              isAExtendsB(processingEnv, type, SegmentAllocator.class))
            );
        return !downcallType;
    }

    private boolean returnNonDowncallType(ExecutableElement executableElement) {
        return isNotDowncallType(executableElement, ExecutableElement::getReturnType);
    }

    private boolean parameterContainsNonDowncallType(ExecutableElement executableElement) {
        return executableElement.getParameters().stream()
            .anyMatch(element -> isNotDowncallType(element, VariableElement::asType));
    }

    private boolean containsNonDowncallType(ExecutableElement executableElement) {
        return returnNonDowncallType(executableElement) ||
               parameterContainsNonDowncallType(executableElement);
    }

    private static <T extends Element> Stream<T> skipAnnotated(Stream<T> stream) {
        return stream.filter(t -> t.getAnnotation(Skip.class) == null);
    }

    private static <T extends Element> Stream<T> skipAnnotated(Collection<T> collection) {
        return skipAnnotated(collection.stream());
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
                addAnnotationSpec(functionData.annotations(), methodSpec);
                methodSpec.setAccessModifier(functionData.accessModifier());
                methodSpec.setStatic(true);
                functionData.parameters().forEach(parameterData ->
                    methodSpec.addParameter(new ParameterSpec(parameterData.type().apply(imports), parameterData.name()).also(parameterSpec -> {
                        addAnnotationSpec(parameterData.annotations(), parameterSpec);
                    }))
                );
                functionData.statements().forEach(typeUse ->
                    methodSpec.addStatement(typeUse.apply(imports)));
            }));
    }

    private void addAnnotationSpec(List<AnnotationData> list, Annotatable annotatable) {
        list.forEach(annotationData -> {
            final AnnotationMirror annotationMirror = annotationData.mirror();
            annotatable.addAnnotation(new AnnotationSpec(
                imports.simplifyOrImport(processingEnv, annotationMirror.getAnnotationType())
            ).also(annotationSpec ->
                annotationMirror.getElementValues().forEach((executableElement, annotationValue) ->
                    annotationSpec.addArgument(executableElement.getSimpleName().toString(), annotationValue.toString()))));
        });
    }

    private String getDocComment(Element e) {
        return processingEnv.getElementUtils().getDocComment(e);
    }

    private String getConstExp(Object v) {
        return processingEnv.getElementUtils().getConstantExpression(v);
    }
}
