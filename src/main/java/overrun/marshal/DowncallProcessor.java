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

import overrun.marshal.gen1.*;
import overrun.marshal.gen2.DowncallData;
import overrun.marshal.internal.Processor;
import overrun.marshal.struct.ByValue;
import overrun.marshal.struct.StructRef;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static overrun.marshal.internal.Util.*;

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
        // TODO: 2024/1/7 squid233: rewrite
        final boolean debug = true;
        ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(Downcall.class)).forEach(e -> {
            try {
                if (debug) {
                    new DowncallData(processingEnv).generate(e);
                } else {
                    final var enclosed = e.getEnclosedElements();
                    writeFile(e, ElementFilter.fieldsIn(enclosed), ElementFilter.methodsIn(enclosed));
                }
            } catch (IOException ex) {
                printStackTrace(ex);
            }
        });
    }

    private void writeFile(
        TypeElement type,
        List<VariableElement> fields,
        List<ExecutableElement> methods
    ) throws IOException {
        final Downcall downcall = type.getAnnotation(Downcall.class);
        final String className = type.getQualifiedName().toString();
        final int lastDot = className.lastIndexOf('.');
        final String packageName = lastDot > 0 ? className.substring(0, lastDot) : null;
        final String simpleClassName;
        if (downcall.name().isBlank()) {
            final String string = className.substring(lastDot + 1);
            if (string.startsWith("C")) {
                simpleClassName = string.substring(1);
            } else {
                printError("""
                    Class name must start with C if the name is not specified. Current name: %s
                         Possible solutions:
                         1) Add C as a prefix. For example: C%1$s
                         2) Specify name in @Downcall and rename this file. For example: @Downcall(name = "%1$s")"""
                    .formatted(string));
                return;
            }
        } else {
            simpleClassName = downcall.name();
        }

        final SourceFile file = new SourceFile(packageName);
        file.addImports("overrun.marshal.*", "java.lang.foreign.*");
        file.addImport(MethodHandle.class);
        file.addClass(simpleClassName, classSpec -> {
            classSpec.setDocument(getDocument(type));
            classSpec.setFinal(!downcall.nonFinal());

            // fields
            addFields(fields, classSpec);

            if (methods.isEmpty()) {
                return;
            }

            // loader
            addLoader(type, classSpec);

            // method handles
            addMethodHandles(type, methods, classSpec);

            // method declarations
            methods.forEach(e -> {
                final TypeMirror returnType = e.getReturnType();
                final String methodName = e.getSimpleName().toString();
                final Overload overload = e.getAnnotation(Overload.class);
                final StructRef eStructRef = e.getAnnotation(StructRef.class);
                final boolean eIsStruct = eStructRef != null;
                final String overloadValue;
                if (overload != null) {
                    final String value = overload.value();
                    overloadValue = value.isBlank() ? methodName : value;
                } else {
                    overloadValue = null;
                }
                final String javaReturnType;
                if (eIsStruct) {
                    javaReturnType = overloadValue != null ? eStructRef.value() : MemorySegment.class.getSimpleName();
                } else {
                    javaReturnType = toTargetType(returnType, overloadValue);
                }

                classSpec.addMethod(new MethodSpec(javaReturnType, methodName), methodSpec -> {
                    final ByValue byValue = e.getAnnotation(ByValue.class);
                    final var parameters = e.getParameters();
                    final boolean shouldInsertArena = parameters.stream().map(VariableElement::asType).anyMatch(this::isUpcall);
                    final boolean shouldInsertAllocator = !shouldInsertArena && (parameters.stream().anyMatch(p -> {
                        final TypeMirror t = p.asType();
                        return isArray(t) || isString(t);
                    }) || byValue != null);
                    final boolean notVoid = returnType.getKind() != TypeKind.VOID;
                    final boolean shouldStoreResult = notVoid &&
                                                      (isArray(returnType) ||
                                                       isString(returnType) ||
                                                       isUpcall(returnType) ||
                                                       eIsStruct ||
                                                       parameters.stream().anyMatch(p -> p.getAnnotation(Ref.class) != null));
                    // annotations
                    final Access access = e.getAnnotation(Access.class);
                    final Critical critical = e.getAnnotation(Critical.class);
                    final Custom custom = e.getAnnotation(Custom.class);
                    final Default defaultAnnotation = e.getAnnotation(Default.class);
                    final boolean isDefaulted = defaultAnnotation != null;
                    final SizedSeg eSizedSeg = e.getAnnotation(SizedSeg.class);
                    final Sized eSized = e.getAnnotation(Sized.class);

                    final String arenaParameter = parameters.stream()
                        .map(VariableElement::getSimpleName)
                        .filter("arena"::contentEquals)
                        .findAny()
                        .map(Object::toString)
                        .orElse(null);
                    final String segmentAllocatorParameter = parameters.stream()
                        .map(VariableElement::getSimpleName)
                        .filter("segmentAllocator"::contentEquals)
                        .findAny()
                        .map(Object::toString)
                        .orElse(null);
                    final String allocatorParamName = shouldInsertArena ?
                        insertUnderline("arena", arenaParameter) :
                        insertUnderline("segmentAllocator", segmentAllocatorParameter);

                    String document = getDocument(e);
                    if (document != null) {
                        if (shouldInsertArena) {
                            document += " @param " + allocatorParamName + " an arena that allocates arguments";
                        } else if (shouldInsertAllocator) {
                            document += " @param " + allocatorParamName + " a segment allocator that allocates arguments";
                        }
                    }
                    methodSpec.setDocument(document);
                    methodSpec.setStatic(true);

                    if (critical != null) {
                        methodSpec.addAnnotation(new AnnotationSpec(Critical.class)
                            .addArgument("allowHeapAccess", getConstExp(critical.allowHeapAccess())));
                    }
                    if (isDefaulted) {
                        methodSpec.addAnnotation(new AnnotationSpec(Default.class).also(annotationSpec -> {
                            if (!defaultAnnotation.value().isBlank()) {
                                annotationSpec.addArgument("value", getConstExp(defaultAnnotation.value()));
                            }
                        }));
                    }
                    addAnnotationValue(methodSpec, eSizedSeg, SizedSeg.class, SizedSeg::value);
                    addAnnotationValue(methodSpec, eSized, Sized.class, Sized::value);

                    if (access != null) {
                        methodSpec.setAccessModifier(access.value());
                    }
                    if (shouldInsertArena || shouldInsertAllocator) {
                        methodSpec.addParameter(
                            shouldInsertArena ? Arena.class : SegmentAllocator.class,
                            allocatorParamName
                        );
                    }

                    final var parameterTypeFunction = parameterTypeFunction(custom, overloadValue);
                    parameters.forEach(p -> methodSpec.addParameter(new ParameterSpec(parameterTypeFunction.apply(p), p.getSimpleName().toString())
                        .also(parameterSpec -> {
                            if (p.getAnnotation(NullableRef.class) != null) {
                                parameterSpec.addAnnotation(new AnnotationSpec(NullableRef.class));
                            }
                            if (p.getAnnotation(Ref.class) != null) {
                                parameterSpec.addAnnotation(new AnnotationSpec(Ref.class));
                            }
                            addAnnotationValue(parameterSpec, p.getAnnotation(SizedSeg.class), SizedSeg.class, SizedSeg::value);
                            addAnnotationValue(parameterSpec, p.getAnnotation(Sized.class), Sized.class, Sized::value);
                        })));

                    if (custom != null) {
                        methodSpec.addStatement(Spec.indented(custom.value()));
                        return;
                    }

                    if (overload == null) {
                        if (isDefaulted && notVoid && defaultAnnotation.value().isBlank()) {
                            printError(type + "::" + e + ": Default non-void method must have a default return value");
                        }
                        final String entrypoint = methodEntrypoint(e);
                        methodSpec.addStatement(new TryCatchStatement().also(tryCatchStatement -> {
                            StatementBlock targetStatement = tryCatchStatement;
                            if (isDefaulted) {
                                final IfStatement ifStatement = new IfStatement(Spec.notNullSpec(entrypoint));
                                if (notVoid) {
                                    ifStatement.addElseClause(ElseClause.of(), elseClause ->
                                        elseClause.addStatement(Spec.returnStatement(Spec.indentedExceptFirstLine(defaultAnnotation.value())))
                                    );
                                }
                                targetStatement = ifStatement;
                                tryCatchStatement.addStatement(ifStatement);
                            }
                            final InvokeSpec invokeExact = new InvokeSpec(entrypoint, "invokeExact");
                            if (byValue != null) {
                                invokeExact.addArgument(allocatorParamName);
                            }
                            invokeExact.addArguments(e.getParameters().stream()
                                .map(p -> Spec.literal(p.getSimpleName().toString()))
                                .collect(Collectors.toList()));
                            if (notVoid) {
                                targetStatement.addStatement(Spec.returnStatement(Spec.cast(javaReturnType, invokeExact)));
                            } else {
                                targetStatement.addStatement(Spec.statement(invokeExact));
                            }
                            tryCatchStatement.addCatchClause(new CatchClause(Throwable.class.getSimpleName(), "_ex"), catchClause ->
                                catchClause.addStatement(Spec.throwStatement(new ConstructSpec(AssertionError.class.getSimpleName())
                                    .addArgument(Spec.literal(catchClause.name()))))
                            );
                        }));
                        return;
                    }

                    // check array size
                    parameters.stream()
                        .filter(p -> p.getAnnotation(Sized.class) != null)
                        .forEach(p -> {
                            final Sized sized = p.getAnnotation(Sized.class);
                            methodSpec.addStatement(Spec.statement(new InvokeSpec(Checks.class.getSimpleName(), "checkArraySize")
                                .addArgument(getConstExp(sized.value()))
                                .addArgument(Spec.accessSpec(p.getSimpleName().toString(), "length"))));
                        });

                    // ref
                    parameters.stream()
                        .filter(p -> p.getAnnotation(Ref.class) != null)
                        .forEach(p -> {
                            final TypeMirror pType = p.asType();
                            if (!isArray(pType)) {
                                return;
                            }
                            final TypeMirror arrayComponentType = getArrayComponentType(pType);
                            final Name name = p.getSimpleName();
                            final String nameString = name.toString();
                            final Spec invokeSpec;
                            if (isBooleanArray(pType)) {
                                invokeSpec = new InvokeSpec(BoolHelper.class, "of")
                                    .addArgument(allocatorParamName)
                                    .addArgument(nameString);
                            } else if (isPrimitiveArray(pType)) {
                                invokeSpec = new InvokeSpec(allocatorParamName, "allocateFrom")
                                    .addArgument(toValueLayoutStr(arrayComponentType))
                                    .addArgument(nameString);
                            } else if (isStringArray(pType)) {
                                invokeSpec = new InvokeSpec(StrHelper.class, "of")
                                    .addArgument(allocatorParamName)
                                    .addArgument(nameString)
                                    .addArgument(createCharset(file, getCustomCharset(p)));
                            } else {
                                invokeSpec = Spec.literal(nameString);
                            }
                            methodSpec.addStatement(new VariableStatement(MemorySegment.class, "_" + nameString,
                                p.getAnnotation(NullableRef.class) != null ?
                                    Spec.ternaryOp(Spec.notNullSpec(nameString),
                                        invokeSpec,
                                        Spec.accessSpec(MemorySegment.class, "NULL")) :
                                    invokeSpec
                            ).setAccessModifier(AccessModifier.PACKAGE_PRIVATE));
                        });

                    // invoke
                    final InvokeSpec invocation = new InvokeSpec(simpleClassName, overloadValue);
                    // add argument to overload invocation
                    if (byValue != null) {
                        invocation.addArgument(allocatorParamName);
                    }
                    parameters.forEach(p -> {
                        final TypeMirror pType = p.asType();
                        final TypeKind pTypeKind = pType.getKind();
                        final String nameString = p.getSimpleName().toString();
                        final Spec invocationArg;
                        if (p.getAnnotation(StructRef.class) != null) {
                            invocationArg = new InvokeSpec(nameString, "segment");
                        } else if (pTypeKind.isPrimitive()) {
                            invocationArg = Spec.literal(nameString);
                        } else {
                            invocationArg = switch (pTypeKind) {
                                case DECLARED -> {
                                    if (isString(pType)) {
                                        yield new InvokeSpec(allocatorParamName, "allocateFrom")
                                            .addArgument(nameString)
                                            .also(invokeSpec -> {
                                                if (p.getAnnotation(StrCharset.class) != null) {
                                                    invokeSpec.addArgument(createCharset(file, getCustomCharset(p)));
                                                }
                                            });
                                    }
                                    if (isUpcall(pType)) {
                                        yield new InvokeSpec(nameString, "stub")
                                            .addArgument(allocatorParamName);
                                    }
                                    if (isCEnum(pType)) {
                                        yield new InvokeSpec(nameString, "value");
                                    }
                                    yield Spec.literal(nameString);
                                }
                                case ARRAY -> {
                                    if (p.getAnnotation(Ref.class) != null) {
                                        yield Spec.literal('_' + nameString);
                                    }
                                    if (isBooleanArray(pType)) {
                                        yield new InvokeSpec(BoolHelper.class, "of")
                                            .addArgument(allocatorParamName)
                                            .addArgument(nameString);
                                    }
                                    if (isStringArray(pType)) {
                                        yield new InvokeSpec(StrHelper.class, "of")
                                            .addArgument(allocatorParamName)
                                            .addArgument(nameString)
                                            .addArgument(createCharset(file, getCustomCharset(p)));
                                    }
                                    yield new InvokeSpec(allocatorParamName, "allocateFrom")
                                        .addArgument(toValueLayoutStr(getArrayComponentType(pType)))
                                        .addArgument(nameString);
                                }
                                default -> null;
                            };
                            if (invocationArg == null) {
                                printError("Invalid type " + pType + " in " + type + "::" + e);
                                return;
                            }
                        }
                        invocation.addArgument(p.getAnnotation(NullableRef.class) != null ?
                            Spec.ternaryOp(Spec.notNullSpec(nameString),
                                invocationArg,
                                Spec.accessSpec(MemorySegment.class, "NULL")) :
                            invocationArg);
                    });
                    final Spec finalSpec;
                    if (notVoid) {
                        if (shouldStoreResult) {
                            finalSpec = new VariableStatement("var", "$_marshalResult", invocation)
                                .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                                .setFinal(true);
                        } else if (isCEnum(returnType)) {
                            final var wrapMethod = findCEnumWrapperMethod(returnType);
                            if (wrapMethod.isPresent()) {
                                finalSpec = Spec.returnStatement(new InvokeSpec(returnType.toString(), wrapMethod.get().getSimpleName().toString())
                                    .addArgument(invocation));
                            } else {
                                printError(wrapperNotFound(returnType, type, "::", e, "CEnum.Wrapper"));
                                return;
                            }
                        } else {
                            finalSpec = Spec.returnStatement(invocation);
                        }
                    } else {
                        finalSpec = Spec.statement(invocation);
                    }
                    methodSpec.addStatement(finalSpec);

                    // ref result
                    parameters.stream()
                        .filter(p -> p.getAnnotation(Ref.class) != null)
                        .forEach(p -> {
                            final Spec refReturnSpec;
                            final TypeMirror pType = p.asType();
                            final String nameString = p.getSimpleName().toString();
                            final Ref ref = p.getAnnotation(Ref.class);
                            final boolean nullable = p.getAnnotation(NullableRef.class) != null;
                            if (isArray(pType)) {
                                final Spec spec;
                                if (isBooleanArray(pType)) {
                                    spec = Spec.statement(new InvokeSpec(BoolHelper.class, "copy")
                                        .addArgument('_' + nameString)
                                        .addArgument(nameString));
                                } else {
                                    if (isPrimitiveArray(pType)) {
                                        spec = Spec.statement(new InvokeSpec(MemorySegment.class, "copy")
                                            .addArgument('_' + nameString)
                                            .addArgument(toValueLayoutStr(getArrayComponentType(pType)))
                                            .addArgument("0L")
                                            .addArgument(nameString)
                                            .addArgument("0")
                                            .addArgument(Spec.accessSpec(nameString, "length")));
                                    } else if (isStringArray(pType)) {
                                        spec = Spec.statement(new InvokeSpec(StrHelper.class, "copy")
                                            .addArgument('_' + nameString)
                                            .addArgument(nameString)
                                            .addArgument(createCharset(file, getCustomCharset(p))));
                                    } else {
                                        spec = null;
                                    }
                                }
                                if (nullable) {
                                    final IfStatement ifStatement = new IfStatement(Spec.notNullSpec(nameString));
                                    if (spec != null) {
                                        ifStatement.addStatement(spec);
                                    }
                                    refReturnSpec = ifStatement;
                                } else {
                                    refReturnSpec = spec;
                                }
                                methodSpec.addStatement(refReturnSpec);
                            }
                        });

                    // return
                    if (shouldStoreResult) {
                        // converting return value
                        Spec finalInvocation = Spec.literal("$_marshalResult");
                        if (eIsStruct) {
                            finalInvocation = new ConstructSpec(eStructRef.value())
                                .addArgument(finalInvocation);
                        } else if (isArray(returnType)) {
                            final TypeMirror arrayComponentType = getArrayComponentType(returnType);
                            if (isBooleanArray(returnType)) {
                                finalInvocation = new InvokeSpec(BoolHelper.class, "toArray")
                                    .addArgument(finalInvocation);
                            } else if (isStringArray(returnType)) {
                                finalInvocation = new InvokeSpec(StrHelper.class, "toArray")
                                    .addArgument(finalInvocation)
                                    .addArgument(createCharset(file, getCustomCharset(e)));
                            } else {
                                finalInvocation = new InvokeSpec(finalInvocation, "toArray")
                                    .addArgument(toValueLayoutStr(arrayComponentType));
                            }
                        } else if (isDeclared(returnType)) {
                            if (isString(returnType)) {
                                final InvokeSpec getString = new InvokeSpec(finalInvocation, "getString")
                                    .addArgument("0L");
                                if (e.getAnnotation(StrCharset.class) != null) {
                                    getString.addArgument(createCharset(file, getCustomCharset(e)));
                                }
                                finalInvocation = getString;
                            } else if (isUpcall(returnType)) {
                                // find wrap method
                                final var wrapMethod = findUpcallWrapperMethod(returnType);
                                if (wrapMethod.isPresent()) {
                                    finalInvocation = new InvokeSpec(returnType.toString(), wrapMethod.get().getSimpleName().toString())
                                        .addArgument(finalInvocation);
                                } else {
                                    printError(wrapperNotFound(returnType, type, "::", e, "Upcall.Wrapper"));
                                    return;
                                }
                            } else if (isCEnum(returnType)) {
                                final var wrapMethod = findCEnumWrapperMethod(returnType);
                                if (wrapMethod.isPresent()) {
                                    finalInvocation = new InvokeSpec(returnType.toString(), wrapMethod.get().getSimpleName().toString())
                                        .addArgument(finalInvocation);
                                } else {
                                    printError(wrapperNotFound(returnType, type, "::", e, "CEnum.Wrapper"));
                                    return;
                                }
                            }
                        }

                        methodSpec.addStatement(Spec.returnStatement(
                            (eIsStruct || canConvertToAddress(returnType)) ?
                                Spec.ternaryOp(Spec.neqSpec(new InvokeSpec("$_marshalResult", "address"), Spec.literal("0L")),
                                    finalInvocation,
                                    Spec.literal("null")
                                ) :
                                finalInvocation
                        ));
                    }
                });
            });
        });

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + '.' + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private Function<VariableElement, String> parameterTypeFunction(Custom custom, String overloadValue) {
        if (custom != null) {
            return p -> p.asType().toString();
        }
        return p -> {
            final StructRef structRef = p.getAnnotation(StructRef.class);
            if (structRef != null) {
                if (overloadValue != null) {
                    return structRef.value();
                }
                return MemorySegment.class.getSimpleName();
            }
            return toTargetType(p.asType(), overloadValue);
        };
    }

    private void addFields(List<VariableElement> fields, ClassSpec classSpec) {
        fields.forEach(e -> {
            final Object constantValue = e.getConstantValue();
            if (constantValue == null) {
                return;
            }
            classSpec.addField(new VariableStatement(simplify(e.asType().toString()),
                e.getSimpleName().toString(),
                Spec.literal(getConstExp(constantValue)))
                .setDocument(getDocument(e))
                .setStatic(true)
                .setFinal(true));
        });
    }

    private void addLoader(TypeElement type, ClassSpec classSpec) {
        final Downcall downcall = type.getAnnotation(Downcall.class);
        final String loader = type.getAnnotationMirrors().stream()
            .filter(m -> Downcall.class.getCanonicalName().equals(m.getAnnotationType().toString()))
            .findFirst()
            .orElseThrow()
            .getElementValues().entrySet().stream()
            .filter(e -> "loader()".equals(e.getKey().toString()))
            .findFirst()
            .map(e -> e.getValue().getValue().toString())
            .orElse(null);
        final String libname = downcall.libname();
        final InvokeSpec invocation;
        if (loader == null) {
            invocation = new InvokeSpec(SymbolLookup.class, "libraryLookup")
                .addArgument(getConstExp(libname))
                .addArgument(new InvokeSpec(Arena.class, "global"));
        } else {
            final var libLoader = findLibLoaderMethod(processingEnv.getElementUtils().getTypeElement(loader).asType());
            if (libLoader.isPresent()) {
                invocation = new InvokeSpec(loader, libLoader.get().getSimpleName().toString())
                    .addArgument(getConstExp(libname));
            } else {
                printError(specifiedMethodNotFound("loader", loader, type, "", "", "Loader"));
                return;
            }
        }
        classSpec.addField(new VariableStatement(SymbolLookup.class, "_LOOKUP", invocation)
            .setAccessModifier(AccessModifier.PRIVATE)
            .setStatic(true)
            .setFinal(true));
        classSpec.addField(new VariableStatement(Linker.class, "_LINKER",
            new InvokeSpec(Linker.class, "nativeLinker"))
            .setAccessModifier(AccessModifier.PRIVATE)
            .setStatic(true)
            .setFinal(true));
    }

    private void addMethodHandles(TypeElement type, List<ExecutableElement> methods, ClassSpec classSpec) {
        methods.stream().collect(Collectors.toMap(DowncallProcessor::methodEntrypoint, Function.identity(), (e1, e2) -> {
            final Overload o1 = e1.getAnnotation(Overload.class);
            final Overload o2 = e2.getAnnotation(Overload.class);
            // if e1 is not an overload
            if (o1 == null) {
                // if e2 is an overload
                if (o2 != null) {
                    return e1;
                }
                final Custom c1 = e1.getAnnotation(Custom.class);
                final Custom c2 = e2.getAnnotation(Custom.class);
                // if e1 is not custom
                if (c1 == null) {
                    // if e2 is custom
                    if (c2 != null) {
                        return e1;
                    }
                    // compare function descriptor
                    final List<String> p1 = collectConflictedMethodSignature(e1);
                    final List<String> p2 = collectConflictedMethodSignature(e2);
                    if (!p1.equals(p2)) {
                        printError("Overload not supported between " + type + "::" + e1 + " and ::" + e2);
                    }
                    return e1;
                }
                // e1 is custom
                if (c2 == null) {
                    return e2;
                }
                // overwrite it.
                return e2;
            }
            // e1 is an overload
            // overwrite it.
            return e2;
        }, LinkedHashMap::new)).forEach((k, v) -> {
            final TypeMirror returnType = v.getReturnType();
            final Critical critical = v.getAnnotation(Critical.class);
            final boolean defaulted = v.getAnnotation(Default.class) != null;
            classSpec.addField(new VariableStatement(MethodHandle.class, k,
                new InvokeSpec(new InvokeSpec(
                    new InvokeSpec("_LOOKUP", "find").addArgument(getConstExp(k)),
                    "map"
                ).addArgument(new LambdaSpec("_s")
                    .addStatementThis(new InvokeSpec("_LINKER", "downcallHandle")
                        .addArgument("_s")
                        .addArgument(new InvokeSpec(FunctionDescriptor.class,
                            returnType.getKind() == TypeKind.VOID ? "ofVoid" : "of").also(invokeSpec -> {
                            if (returnType.getKind() != TypeKind.VOID) {
                                final StructRef structRef = v.getAnnotation(StructRef.class);
                                final boolean isStruct = structRef != null;
                                final String valueLayoutStr = isStruct ? "ValueLayout.ADDRESS" : toValueLayoutStr(returnType);
                                final SizedSeg sizedSeg = v.getAnnotation(SizedSeg.class);
                                final Sized sized = v.getAnnotation(Sized.class);
                                if (isStruct) {
                                    if (v.getAnnotation(ByValue.class) != null) {
                                        invokeSpec.addArgument(Spec.accessSpec(structRef.value(), "LAYOUT"));
                                    } else {
                                        invokeSpec.addArgument(new InvokeSpec(valueLayoutStr, "withTargetLayout")
                                            .addArgument(Spec.accessSpec(structRef.value(), "LAYOUT")));
                                    }
                                } else if (sizedSeg != null && isMemorySegment(returnType)) {
                                    invokeSpec.addArgument(new InvokeSpec(valueLayoutStr, "withTargetLayout")
                                        .addArgument(new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                                            .addArgument(getConstExp(sizedSeg.value()))
                                            .addArgument(Spec.accessSpec(ValueLayout.class, "JAVA_BYTE"))));
                                } else if (sized != null && isArray(returnType)) {
                                    invokeSpec.addArgument(new InvokeSpec(valueLayoutStr, "withTargetLayout")
                                        .addArgument(new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                                            .addArgument(getConstExp(sized.value()))
                                            .addArgument(Spec.accessSpec(ValueLayout.class, toValueLayoutStr(getArrayComponentType(returnType))))));
                                } else {
                                    invokeSpec.addArgument(valueLayoutStr);
                                }
                            }
                            v.getParameters().forEach(e -> invokeSpec.addArgument(toValueLayoutStr(e.asType())));
                        })).also(invokeSpec -> {
                            if (critical != null) {
                                invokeSpec.addArgument(new InvokeSpec(Spec.accessSpec(Linker.class, Linker.Option.class), "critical")
                                    .addArgument(getConstExp(critical.allowHeapAccess())));
                            }
                        }))), defaulted ? "orElse" : "orElseThrow").also(invokeSpec -> {
                    if (defaulted) {
                        invokeSpec.addArgument("null");
                    }
                }))
                .setStatic(true)
                .setFinal(true)
                .setDocument("""
                     The method handle of {@code %s}.\
                    """.formatted(v.getSimpleName())), variableStatement -> {
                final Access access = v.getAnnotation(Access.class);
                if (access != null) {
                    variableStatement.setAccessModifier(access.value());
                }
            });
        });
    }

    private List<String> collectConflictedMethodSignature(ExecutableElement e) {
        return Stream.concat(Stream.of(e.getReturnType()), e.getParameters().stream().map(VariableElement::asType)).map(t -> {
            if (t.getKind() == TypeKind.VOID) {
                return ".VOID";
            }
            if (isValueType(t)) {
                return toValueLayoutStr(t);
            }
            return t.toString();
        }).toList();
    }

    private static String methodEntrypoint(ExecutableElement method) {
        final Entrypoint annotation = method.getAnnotation(Entrypoint.class);
        if (annotation != null) {
            final String entrypoint = annotation.value();
            if (!entrypoint.isBlank()) {
                return entrypoint;
            }
        }
        return method.getSimpleName().toString();
    }

    private String toTargetType(TypeMirror typeMirror, String overload) {
        final TypeKind typeKind = typeMirror.getKind();
        if (typeKind.isPrimitive()) {
            return typeMirror.toString();
        }
        return switch (typeKind) {
            case VOID -> typeMirror.toString();
            case DECLARED -> {
                if (isMemorySegment(typeMirror)) {
                    yield MemorySegment.class.getSimpleName();
                }
                if (isString(typeMirror)) {
                    yield overload == null ? MemorySegment.class.getSimpleName() : String.class.getSimpleName();
                }
                if (isUpcall(typeMirror)) {
                    yield overload == null ? MemorySegment.class.getSimpleName() : typeMirror.toString();
                }
                if (isCEnum(typeMirror)) {
                    yield overload == null ? int.class.getSimpleName() : typeMirror.toString();
                }
                throw invalidType(typeMirror);
            }
            case ARRAY -> {
                final boolean string = isString(getArrayComponentType(typeMirror));
                if (isPrimitiveArray(typeMirror) || string) {
                    yield overload == null ? MemorySegment.class.getSimpleName() : (string ? String.class.getSimpleName() + "[]" : typeMirror.toString());
                }
                throw invalidType(typeMirror);
            }
            default -> throw invalidType(typeMirror);
        };
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Downcall.class.getCanonicalName());
    }
}
