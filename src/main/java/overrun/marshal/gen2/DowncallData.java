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
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static overrun.marshal.internal.Util.*;

/**
 * Holds downcall fields and functions
 *
 * @author squid233
 * @since 0.1.0
 */
public final class DowncallData extends BaseData {
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

    /**
     * Construct
     *
     * @param processingEnv the processing environment
     */
    public DowncallData(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    private void processDowncallType(TypeElement typeElement, String simpleClassName) {
        final var enclosedElements = typeElement.getEnclosedElements();

        final Downcall downcall = typeElement.getAnnotation(Downcall.class);

        this.document = getDocComment(typeElement);
        this.nonFinal = downcall.nonFinal();

        // process fieldDataList
        skipAnnotated(ElementFilter.fieldsIn(enclosedElements)).forEach(element -> {
            final Object constantValue = element.getConstantValue();
            if (constantValue == null) {
                return;
            }
            fieldDataList.add(new FieldData(
                getDocComment(element),
                List.of(),
                AccessModifier.PUBLIC,
                true,
                true,
                TypeData.detectType(processingEnv, element.asType()),
                element.getSimpleName().toString(),
                TypeUse.literal(getConstExp(constantValue))
            ));
        });

        final var methods = ElementFilter.methodsIn(enclosedElements);
        if (methods.isEmpty()) {
            return;
        }
        // collect method handles
        final Map<String, MethodHandleData> methodHandleDataMap = LinkedHashMap.newLinkedHashMap(methods.size());
        skipAnnotated(methods).forEach(executableElement -> {
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
                                    "sequenceLayout")
                                    .addArgument(getConstExp(sized.value()))
                                    .addArgument(switch (0) {
                                        default -> {
                                            final Optional<TypeUse> seqLayout;
                                            if (returnType.getKind() == TypeKind.ARRAY &&
                                                returnType instanceof ArrayType arrayType) {
                                                seqLayout = TypeUse.valueLayout(processingEnv, arrayType.getComponentType());
                                            } else {
                                                seqLayout = TypeUse.valueLayout(byte.class);
                                            }
                                            yield seqLayout.orElseThrow().apply(importData);
                                        }
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
        fieldDataList.add(new FieldData(
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
        methodHandleDataMap.forEach((name, methodHandleData) -> fieldDataList.add(new FieldData(
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
                        .addArgument(switch (0) {
                            default -> {
                                final var returnType = methodHandleData.returnType();
                                final String methodName = returnType.isPresent() ? "of" : "ofVoid";
                                final InvokeSpec fdOf = new InvokeSpec(importData.simplifyOrImport(FunctionDescriptor.class), methodName);
                                returnType.ifPresent(typeUse -> {
                                    final Spec spec = typeUse.apply(importData);
                                    fdOf.addArgument(spec);
                                });
                                methodHandleData.parameterTypes().forEach(typeUse ->
                                    fdOf.addArgument(typeUse.apply(importData)));
                                yield fdOf;
                            }
                        })
                        .addArgument(switch (0) {
                            default -> {
                                final Critical critical = methodHandleData.executableElement().getAnnotation(Critical.class);
                                yield critical != null ?
                                    new InvokeSpec(importData.simplifyOrImport(Linker.Option.class), "critical")
                                        .addArgument(getConstExp(critical.allowHeapAccess())) :
                                    null;
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
        final var fieldNames = fieldDataList.stream().map(FieldData::name).toList();
        skipAnnotated(methods).forEach(executableElement -> {
            final MethodHandleData methodHandleData = methodHandleDataMap.get(getMethodEntrypoint(executableElement));
            if (methodHandleData == null) {
                return;
            }

            final Custom custom = executableElement.getAnnotation(Custom.class);
            if (custom != null) {
                addCustomMethod(executableElement, methodHandleData, custom);
            } else {
                addDowncallMethod(simpleClassName,
                    executableElement,
                    fieldNames,
                    methodHandleData);
            }
        });
    }

    /**
     * Generates the file
     *
     * @param typeElement the type element
     * @throws IOException if an I/O error occurred
     */
    @Override
    public void generate(TypeElement typeElement) throws IOException {
        final Downcall downcall = typeElement.getAnnotation(Downcall.class);
        final DeclaredTypeData generatedClassName = generateClassName(downcall.name(), typeElement);

        processDowncallType(typeElement, generatedClassName.name());

        generate(generatedClassName);
    }

    private void addDowncallMethod(
        String simpleClassName,
        ExecutableElement executableElement,
        List<String> fieldNames,
        MethodHandleData methodHandleData) {
        final var returnType = TypeUse.toProcessedType(processingEnv, executableElement, ExecutableElement::getReturnType);

        final var parameters = executableElement.getParameters();
        final int skipFirst = shouldSkipFirstParameter(executableElement, parameters);
        if (skipFirst < 0) {
            return;
        }
        final var parameterDataList = parameters.stream()
            .map(element -> new ParameterData(
                getElementAnnotations(PARAMETER_ANNOTATIONS, element),
                TypeUse.toProcessedType(processingEnv, element).orElseThrow(),
                element.getSimpleName().toString()
            ))
            .toList();
        final var parameterNames = parameterDataList.stream()
            .skip(skipFirst > 0 ? 1 : 0)
            .map(ParameterData::name)
            .toList();

        final List<String> variablesInScope = new ArrayList<>(parameterNames);

        final String memoryStackName = tryInsertUnderline("stack", s -> variablesInScope.stream().anyMatch(s::equals));
        final boolean hasAllocatorParameter =
            !parameterDataList.isEmpty() &&
            isAExtendsB(processingEnv, parameters.getFirst().asType(), SegmentAllocator.class);
        final String allocatorParameter = hasAllocatorParameter ?
            parameterDataList.getFirst().name() :
            memoryStackName;
        final boolean shouldWrapWithMemoryStack =
            parameters.stream().anyMatch(element -> useAllocator(element.asType())) &&
            !hasAllocatorParameter;
        if (shouldWrapWithMemoryStack) {
            variablesInScope.add(memoryStackName);
        }

        final boolean shouldAddInvoker = parameterNames.stream().anyMatch(fieldNames::contains);

        final List<TypeUse> statements = new ArrayList<>();
        final List<TypeUse> tryStatements = new ArrayList<>();
        final List<TypeUse> invocationStatements = new ArrayList<>();

        // check array size of @Sized array
        parameters.stream()
            .filter(element -> element.getAnnotation(Sized.class) != null)
            .forEach(element -> {
                final Sized sized = element.getAnnotation(Sized.class);
                statements.add(importData ->
                    Spec.statement(new InvokeSpec(importData.simplifyOrImport(Checks.class), "checkArraySize")
                        .addArgument(getConstExp(sized.value()))
                        .addArgument(Spec.accessSpec(element.getSimpleName().toString(), "length"))));
            });

        // ref segments
        final Map<String, String> refNameMap = HashMap.newHashMap((int) parameters.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .count());
        parameters.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .forEach(element -> invocationStatements.add(importData -> {
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
        final String methodHandleName = methodHandleData.name();
        final TypeUse invokeTypeUse = importData -> {
            final InvokeSpec invokeSpec = new InvokeSpec(shouldAddInvoker ?
                Spec.accessSpec(simpleClassName, methodHandleName) :
                Spec.literal(methodHandleName), "invokeExact");
            // wrap parameters
            var stream = parameters.stream();
            if (skipFirst > 0) {
                stream = stream.skip(1);
            } else if (executableElement.getAnnotation(ByValue.class) != null &&
                       !isSameClass(parameters.getFirst().asType(), SegmentAllocator.class)) {
                invokeSpec.addArgument(Spec.cast(importData.simplifyOrImport(SegmentAllocator.class),
                    Spec.literal(allocatorParameter)));
                stream = stream.skip(1);
            }
            stream.map(element -> wrapParameter(importData, allocatorParameter, element, true, refNameMap::get))
                .forEach(invokeSpec::addArgument);
            return invokeSpec;
        };

        // store result
        final var downcallReturnType = TypeUse.toDowncallType(processingEnv, executableElement, ExecutableElement::getReturnType);
        final boolean shouldStoreResult =
            downcallReturnType.isPresent() &&
            (canConvertToAddress(executableElement, ExecutableElement::getReturnType) ||
             parameters.stream().anyMatch(element -> element.getAnnotation(Ref.class) != null));
        final String resultVarName;
        if (shouldStoreResult) {
            resultVarName = tryInsertUnderline("result", s -> variablesInScope.stream().anyMatch(s::equals));
            variablesInScope.add(resultVarName);
            invocationStatements.add(importData -> new VariableStatement("var",
                resultVarName,
                Spec.cast(downcallReturnType.get().apply(importData), invokeTypeUse.apply(importData)))
                .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                .setFinal(true));
        } else {
            resultVarName = null;
            if (downcallReturnType.isPresent()) {
                invocationStatements.add(importData -> Spec.returnStatement(wrapReturnValue(importData,
                    Spec.cast(downcallReturnType.get().apply(importData), invokeTypeUse.apply(importData)),
                    executableElement)));
            } else {
                invocationStatements.add(importData -> Spec.statement(invokeTypeUse.apply(importData)));
            }
        }

        // passing to ref
        parameters.stream()
            .filter(element -> element.getAnnotation(Ref.class) != null)
            .forEach(element -> invocationStatements.add(importData ->
                copyRefResult(importData, element, refNameMap::get)));

        // return result
        if (shouldStoreResult) {
            invocationStatements.add(importData -> Spec.returnStatement(wrapReturnValue(importData,
                Spec.literal(resultVarName),
                executableElement)));
        }

        statements.add(importData -> {
            final TryStatement tryStatement = new TryStatement();

            if (shouldWrapWithMemoryStack) {
                tryStatement.addResource(new VariableStatement(
                    "var",
                    memoryStackName,
                    new InvokeSpec(importData.simplifyOrImport(MemoryStack.class), "stackPush")
                ).setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .setAddSemicolon(false));
            }

            final Default defaultAnnotation = executableElement.getAnnotation(Default.class);
            if (defaultAnnotation != null) {
                final TypeUse ifTypeUse = importData1 -> {
                    final IfStatement ifStatement = new IfStatement(Spec.notNullSpec(methodHandleName));
                    invocationStatements.forEach(typeUse -> ifStatement.addStatement(typeUse.apply(importData1)));
                    final String defaultValue = defaultAnnotation.value();
                    if (!defaultValue.isBlank()) {
                        ifStatement.addElseClause(ElseClause.of(), elseClause ->
                            elseClause.addStatement(Spec.returnStatement(Spec.indentCodeBlock(defaultValue)))
                        );
                    }
                    return ifStatement;
                };
                tryStatements.add(ifTypeUse);
            } else {
                tryStatements.addAll(invocationStatements);
            }

            tryStatements.forEach(typeUse -> tryStatement.addStatement(typeUse.apply(importData)));

            tryStatement.addCatchClause(switch (0) {
                default -> {
                    final CatchClause catchClause = new CatchClause(
                        importData.simplifyOrImport(Throwable.class),
                        tryInsertUnderline("e", s -> fieldNames.contains(s) || parameterNames.contains(s))
                    );
                    catchClause.addStatement(Spec.throwStatement(
                        new ConstructSpec(importData.simplifyOrImport(RuntimeException.class))
                            .addArgument(Spec.literal(catchClause.name()))
                    ));
                    yield catchClause;
                }
            });
            return tryStatement;
        });

        functionDataList.add(new FunctionData(
            getDocComment(executableElement),
            getElementAnnotations(METHOD_ANNOTATIONS, executableElement),
            methodHandleData.accessModifier(),
            true,
            returnType.orElseGet(() -> TypeUse.literal(void.class)),
            executableElement.getSimpleName().toString(),
            parameterDataList,
            statements
        ));
    }

    private int shouldSkipFirstParameter(ExecutableElement executableElement,
                                         List<? extends VariableElement> rawParameterList) {
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
            processingEnv.getMessager().printError("The first parameter of method with upcall must be Arena", executableElement);
            return -1;
        }
        if (returnByValue) {
            if (firstParamSegmentAllocator) {
                return 0;
            }
            processingEnv.getMessager().printError("The first parameter of method marked as @ByValue must be SegmentAllocator", executableElement);
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
            true,
            TypeUse.of(processingEnv, executableElement.getReturnType()),
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
            .filter(e -> "loader".contentEquals(e.getKey().getSimpleName()))
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
        fieldDataList.add(new FieldData(
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
        if (isBooleanArray(typeMirror) || isPrimitiveArray(typeMirror)) {
            spec = new InvokeSpec(importData.simplifyOrImport(Unmarshal.class), "copy")
                .addArgument(refFunction.apply(name))
                .addArgument(name);
        } else if (isStringArray(typeMirror)) {
            final InvokeSpec invokeSpec = new InvokeSpec(importData.simplifyOrImport(Unmarshal.class), "copy")
                .addArgument(refFunction.apply(name))
                .addArgument(name);
            if (element.getAnnotation(StrCharset.class) != null) {
                invokeSpec.addArgument(createCharset(getCharset(element)).apply(importData));
            }
            spec = invokeSpec;
        } else {
            processingEnv.getMessager().printWarning("Using @Ref on unknown type %s".formatted(typeMirror), element);
            spec = null;
        }

        return spec != null ? Spec.statement(spec) : Spec.literal("");
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
            if (isBooleanArray(typeMirror) ||
                isPrimitiveArray(typeMirror)) {
                spec = new InvokeSpec(importData.simplifyOrImport(Unmarshal.class), "unmarshal")
                    .addArgument(TypeUse.valueLayout(processingEnv, getArrayComponentType(typeMirror))
                        .orElseThrow()
                        .apply(importData))
                    .addArgument(resultSpec);
            } else if (isStringArray(typeMirror)) {
                final InvokeSpec invokeSpec = new InvokeSpec(importData.simplifyOrImport(Unmarshal.class), "unmarshalAsString")
                    .addArgument(TypeUse.valueLayout(MemorySegment.class).orElseThrow().apply(importData))
                    .addArgument(resultSpec);
                spec = invokeSpec;
                if (executableElement.getAnnotation(StrCharset.class) != null) {
                    invokeSpec.addArgument(createCharset(getCharset(executableElement)).apply(importData));
                }
            } else if (isSameClass(typeMirror, String.class)) {
                final InvokeSpec invokeSpec = new InvokeSpec(importData.simplifyOrImport(Unmarshal.class), "unmarshalAsString")
                    .addArgument(resultSpec);
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
        final String name = element.getSimpleName().toString();
        final Spec spec;
        if (usingRef && element.getAnnotation(Ref.class) != null) {
            spec = Spec.literal(refFunction.apply(name));
        } else {
            final TypeMirror typeMirror = element.asType();
            if (shouldMarshal(processingEnv, element)) {
                final InvokeSpec invokeMarshal = new InvokeSpec(importData.simplifyOrImport(Marshal.class), "marshal");
                if (requireAllocator(processingEnv, typeMirror)) {
                    invokeMarshal.addArgument(allocatorParameter);
                }
                invokeMarshal.addArgument(name);
                if (element.getAnnotation(StrCharset.class) != null) {
                    invokeMarshal.addArgument(createCharset(getCharset(element)).apply(importData));
                }
                spec = invokeMarshal;
            } else {
                spec = Spec.literal(name);
            }
        }
        return spec;
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
        final Entrypoint entrypoint = executableElement.getAnnotation(Entrypoint.class);
        if (entrypoint != null) {
            final String value = entrypoint.value();
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
}
