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

package overrun.marshal;

import overrun.marshal.gen.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
public final class NativeApiProcessor extends AbstractProcessor {
    /**
     * constructor
     */
    public NativeApiProcessor() {
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
        ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(NativeApi.class)).forEach(e -> {
            final var enclosed = e.getEnclosedElements();
            try {
                writeFile(e, ElementFilter.fieldsIn(enclosed), ElementFilter.methodsIn(enclosed));
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
        final NativeApi nativeApi = type.getAnnotation(NativeApi.class);
        final String className = type.getQualifiedName().toString();
        final int lastDot = className.lastIndexOf('.');
        final String packageName = lastDot > 0 ? className.substring(0, lastDot) : null;
        final String simpleClassName = nativeApi.name();

        final SourceFile file = new SourceFile(packageName);
        file.addImports(
            "overrun.marshal.BoolHelper",
            "overrun.marshal.Checks",
            "overrun.marshal.Default",
            "overrun.marshal.FixedSize",
            "overrun.marshal.Ref",
            "overrun.marshal.StrHelper",
            "java.lang.foreign.*",
            "java.lang.invoke.MethodHandle"
        );
        file.addClass(simpleClassName, classSpec -> {
            classSpec.setDocument(getDocument(type));
            classSpec.setFinal(nativeApi.makeFinal());

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
                final String overloadValue;
                if (overload != null) {
                    final String value = overload.value();
                    overloadValue = value.isBlank() ? methodName : value;
                } else {
                    overloadValue = null;
                }
                final String javaReturnType = toTargetType(returnType, overloadValue);

                classSpec.addMethod(new MethodSpec(javaReturnType, methodName), methodSpec -> {
                    final var parameters = e.getParameters();
                    final boolean shouldInsertAllocator = parameters.stream().anyMatch(p -> {
                        final TypeMirror t = p.asType();
                        return isArray(t) || (t.getKind() == TypeKind.DECLARED && isString(t));
                        // TODO: 2023/12/9 Struct
                    });
                    final boolean notVoid = returnType.getKind() != TypeKind.VOID;
                    final boolean returnArray = isArray(returnType);
                    final boolean returnBooleanArray = returnArray && isBooleanArray(returnType);
                    final boolean returnStringArray = returnArray && isString(getArrayComponentType(returnType));
                    final boolean shouldStoreResult = notVoid &&
                                                      parameters.stream().anyMatch(p -> p.getAnnotation(Ref.class) != null);
                    final Access access = e.getAnnotation(Access.class);
                    final Custom custom = e.getAnnotation(Custom.class);
                    final Default defaultAnnotation = e.getAnnotation(Default.class);
                    final boolean isDefaulted = defaultAnnotation != null;

                    methodSpec.setDocument(getDocument(e));
                    if (isDefaulted) {
                        methodSpec.addAnnotation(new AnnotationSpec(Default.class.getSimpleName()).also(annotationSpec -> {
                            if (!defaultAnnotation.value().isBlank()) {
                                annotationSpec.addArgument("value", getConstExp(defaultAnnotation.value()));
                            }
                        }));
                    }
                    if (access != null) {
                        methodSpec.setAccessModifier(access.value());
                    }
                    if (shouldInsertAllocator) {
                        methodSpec.addParameter("SegmentAllocator", "_segmentAllocator");
                    }

                    final Function<TypeMirror, String> parameterTypeFunction;
                    if (custom != null) {
                        parameterTypeFunction = TypeMirror::toString;
                    } else {
                        parameterTypeFunction = t -> toTargetType(t, overloadValue);
                    }
                    parameters.forEach(p -> methodSpec.addParameter(new ParameterSpec(parameterTypeFunction.apply(p.asType()), p.getSimpleName().toString())
                        .also(parameterSpec -> {
                            final FixedSize fixedSize = p.getAnnotation(FixedSize.class);
                            final Ref ref = p.getAnnotation(Ref.class);
                            if (fixedSize != null) {
                                parameterSpec.addAnnotation(new AnnotationSpec(FixedSize.class.getSimpleName())
                                    .addArgument("value", getConstExp(fixedSize.value())));
                            }
                            if (ref != null) {
                                parameterSpec.addAnnotation(new AnnotationSpec(Ref.class.getSimpleName()).also(annotationSpec -> {
                                    if (ref.nullable()) {
                                        annotationSpec.addArgument("nullable", "true");
                                    }
                                }));
                            }
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
                            final InvokeSpec invokeExact = new InvokeSpec(entrypoint, "invokeExact")
                                .addArguments(e.getParameters().stream()
                                    .map(p -> Spec.literal(p.getSimpleName().toString()))
                                    .collect(Collectors.toList()));
                            targetStatement.addStatement(notVoid ? Spec.returnStatement(Spec.cast(javaReturnType, invokeExact)) : Spec.statement(invokeExact));
                            tryCatchStatement.addCatchClause(new CatchClause("Throwable", "_ex"), catchClause ->
                                catchClause.addStatement(Spec.throwStatement(new ConstructSpec("AssertionError")
                                    .addArgument(Spec.literal("\"should not reach here\""))
                                    .addArgument(Spec.literal(catchClause.name()))))
                            );
                        }));
                        return;
                    }

                    // check array size
                    parameters.stream()
                        .filter(p -> p.getAnnotation(FixedSize.class) != null)
                        .forEach(p -> {
                            final FixedSize fixedSize = p.getAnnotation(FixedSize.class);
                            methodSpec.addStatement(Spec.statement(new InvokeSpec(Checks.class.getSimpleName(), "checkArraySize")
                                .addArgument(getConstExp(fixedSize.value()))
                                .addArgument(Spec.accessSpec(p.getSimpleName().toString(), "length"))));
                        });

                    // ref
                    parameters.stream()
                        .filter(p -> p.getAnnotation(Ref.class) != null)
                        .forEach(p -> {
                            final TypeMirror pType = p.asType();
                            final TypeMirror arrayComponentType = getArrayComponentType(pType);
                            final TypeKind typeKind = arrayComponentType.getKind();
                            final Name name = p.getSimpleName();
                            final String nameString = name.toString();
                            final Ref ref = p.getAnnotation(Ref.class);
                            final Spec invokeSpec;
                            if (typeKind == TypeKind.BOOLEAN) {
                                invokeSpec = new InvokeSpec(BoolHelper.class.getSimpleName(), "of")
                                    .addArgument("_segmentAllocator")
                                    .addArgument(nameString);
                            } else if (typeKind.isPrimitive()) {
                                invokeSpec = new InvokeSpec("_segmentAllocator", "allocateFrom")
                                    .addArgument(toValueLayout(arrayComponentType))
                                    .addArgument(nameString);
                            } else if (typeKind == TypeKind.DECLARED) {
                                if (isString(arrayComponentType)) {
                                    invokeSpec = new InvokeSpec(StrHelper.class.getSimpleName(), "of")
                                        .addArgument("_segmentAllocator")
                                        .addArgument(nameString)
                                        .addArgument(createCharset(file, getCustomCharset(p)));
                                } else {
                                    invokeSpec = Spec.literal(nameString);
                                }
                            } else {
                                invokeSpec = Spec.literal(nameString);
                            }
                            methodSpec.addStatement(new VariableStatement(MemorySegment.class.getSimpleName(), "_" + nameString,
                                ref.nullable() ?
                                    Spec.ternaryOp(Spec.notNullSpec(nameString),
                                        invokeSpec,
                                        Spec.accessSpec(MemorySegment.class.getSimpleName(), "NULL")) :
                                    invokeSpec
                            ).setAccessModifier(AccessModifier.PACKAGE_PRIVATE));
                        });

                    // invoke
                    final InvokeSpec invocation = new InvokeSpec(simpleClassName, overloadValue);
                    InvokeSpec finalInvocation = invocation;
                    parameters.forEach(p -> {
                        final TypeMirror pType = p.asType();
                        final TypeKind pTypeKind = pType.getKind();
                        final String nameString = p.getSimpleName().toString();
                        if (pTypeKind.isPrimitive()) {
                            invocation.addArgument(getConstExp(nameString));
                        } else {
                            final String pCustomCharset = getCustomCharset(p);
                            switch (pTypeKind) {
                                case DECLARED -> {
                                    if (isString(pType)) {
                                        invocation.addArgument(new InvokeSpec("_segmentAllocator", "allocateFrom")
                                            .addArgument(nameString)
                                            .addArgument(createCharset(file, pCustomCharset)));
                                    } else {
                                        invocation.addArgument(nameString);
                                        // TODO: 2023/12/10 Struct
                                    }
                                }
                                case ARRAY -> {
                                    if (p.getAnnotation(Ref.class) != null) {
                                        invocation.addArgument('_' + nameString);
                                    } else {
                                        final TypeMirror arrayComponentType = getArrayComponentType(pType);
                                        if (arrayComponentType.getKind() == TypeKind.BOOLEAN) {
                                            invocation.addArgument(new InvokeSpec(BoolHelper.class.getSimpleName(), "of")
                                                .addArgument("_segmentAllocator")
                                                .addArgument(nameString));
                                        } else if (arrayComponentType.getKind() == TypeKind.DECLARED &&
                                                   isString(arrayComponentType)) {
                                            invocation.addArgument(new InvokeSpec(StrHelper.class.getSimpleName(), "of")
                                                .addArgument("_segmentAllocator")
                                                .addArgument(nameString)
                                                .addArgument(createCharset(file, pCustomCharset)));
                                        } else {
                                            invocation.addArgument(new InvokeSpec("_segmentAllocator", "allocateFrom")
                                                .addArgument(toValueLayout(arrayComponentType))
                                                .addArgument(nameString));
                                        }
                                    }
                                }
                                default -> printError("Invalid type " + pType + " in " + type + "::" + e);
                            }
                        }
                    });
                    final String customCharset = getCustomCharset(e);
                    if (returnArray) {
                        if (returnBooleanArray) {
                            finalInvocation = new InvokeSpec(BoolHelper.class.getSimpleName(), "toArray")
                                .addArgument(invocation);
                        } else if (returnStringArray) {
                            finalInvocation = new InvokeSpec(StrHelper.class.getSimpleName(), "toArray")
                                .addArgument(invocation)
                                .addArgument(createCharset(file, customCharset));
                        } else {
                            finalInvocation = new InvokeSpec(invocation, "toArray")
                                .addArgument(toValueLayout(returnType));
                        }
                    } else if (returnType.getKind() == TypeKind.DECLARED && isString(returnType)) {
                        finalInvocation = new InvokeSpec(invocation, "getString")
                            .addArgument("0L")
                            .addArgument(createCharset(file, customCharset));
                    }
                    Spec finalSpec;
                    if (notVoid) {
                        if (shouldStoreResult) {
                            finalSpec = new VariableStatement("var", "$_marshalResult", finalInvocation)
                                .setAccessModifier(AccessModifier.PACKAGE_PRIVATE);
                        } else {
                            finalSpec = Spec.returnStatement(finalInvocation);
                        }
                    } else {
                        finalSpec = Spec.statement(finalInvocation);
                    }
                    methodSpec.addStatement(finalSpec);

                    // ref
                    parameters.stream()
                        .filter(p -> p.getAnnotation(Ref.class) != null)
                        .forEach(p -> {
                            final Spec refReturnSpec;
                            final TypeMirror pType = p.asType();
                            final String nameString = p.getSimpleName().toString();
                            final Ref ref = p.getAnnotation(Ref.class);
                            final boolean nullable = ref.nullable();
                            if (pType.getKind() == TypeKind.ARRAY) {
                                final Spec spec;
                                if (isBooleanArray(pType)) {
                                    spec = Spec.statement(new InvokeSpec(BoolHelper.class.getSimpleName(), "copy")
                                        .addArgument('_' + nameString)
                                        .addArgument(nameString));
                                } else {
                                    final TypeMirror arrayComponentType = getArrayComponentType(pType);
                                    if (isPrimitiveArray(pType)) {
                                        spec = Spec.statement(new InvokeSpec(MemorySegment.class.getSimpleName(), "copy")
                                            .addArgument('_' + nameString)
                                            .addArgument(toValueLayout(arrayComponentType))
                                            .addArgument("0L")
                                            .addArgument(nameString)
                                            .addArgument("0")
                                            .addArgument(Spec.accessSpec(nameString, "length")));
                                    } else if (arrayComponentType.getKind() == TypeKind.DECLARED &&
                                               isString(arrayComponentType)) {
                                        spec = Spec.statement(new InvokeSpec(StrHelper.class.getSimpleName(), "copy")
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
                        methodSpec.addStatement(Spec.returnStatement(Spec.literal("$_marshalResult")));
                    }
                });
            });
        });

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + '.' + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private void addFields(List<VariableElement> fields, ClassSpec classSpec) {
        fields.forEach(e -> {
            final Object constantValue = e.getConstantValue();
            if (constantValue == null) {
                return;
            }
            classSpec.addField(new VariableStatement(toTypeName(e.asType().toString()),
                e.getSimpleName().toString(),
                Spec.literal(getConstExp(constantValue)))
                .setDocument(getDocument(e))
                .setStatic(true)
                .setFinal(true));
        });
    }

    private void addLoader(TypeElement type, ClassSpec classSpec) {
        final NativeApi nativeApi = type.getAnnotation(NativeApi.class);
        final String loader = type.getAnnotationMirrors().stream()
            .filter(m -> NativeApi.class.getName().equals(m.getAnnotationType().toString()))
            .findFirst()
            .orElseThrow()
            .getElementValues().entrySet().stream()
            .filter(e -> "loader()".equals(e.getKey().toString()))
            .findFirst()
            .map(e -> e.getValue().getValue().toString())
            .orElse(null);
        final String libname = nativeApi.libname();
        classSpec.addField(new VariableStatement(SymbolLookup.class.getSimpleName(),
            "_LOOKUP",
            (loader == null ?
                new InvokeSpec(LibraryLoader.class.getName(), "loadLibrary") :
                new InvokeSpec(new ConstructSpec(loader), "load")).addArgument(getConstExp(libname))
        ).setAccessModifier(AccessModifier.PRIVATE)
            .setStatic(true)
            .setFinal(true));
        classSpec.addField(new VariableStatement(Linker.class.getSimpleName(),
            "_LINKER",
            new InvokeSpec(Linker.class.getSimpleName(), "nativeLinker"))
            .setAccessModifier(AccessModifier.PRIVATE)
            .setStatic(true)
            .setFinal(true));
    }

    private void addMethodHandles(TypeElement type, List<ExecutableElement> methods, ClassSpec classSpec) {
        methods.stream().collect(Collectors.toMap(NativeApiProcessor::methodEntrypoint, Function.identity(), (e1, e2) -> {
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
            final boolean defaulted = v.getAnnotation(Default.class) != null;
            classSpec.addField(new VariableStatement(MethodHandle.class.getSimpleName(), k,
                new InvokeSpec(new InvokeSpec(
                    new InvokeSpec("_LOOKUP", "find").addArgument(getConstExp(k)),
                    "map"
                ).addArgument(new LambdaSpec("_s")
                    .addStatementThis(new InvokeSpec("_LINKER", "downcallHandle")
                        .addArgument("_s")
                        .addArgument(new InvokeSpec(FunctionDescriptor.class.getSimpleName(),
                            returnType.getKind() == TypeKind.VOID ? "ofVoid" : "of").also(invokeSpec -> {
                            if (returnType.getKind() != TypeKind.VOID) {
                                invokeSpec.addArgument(toValueLayout(returnType));
                            }
                            v.getParameters().forEach(e -> invokeSpec.addArgument(toValueLayout(e.asType())));
                        })))), defaulted ? "orElse" : "orElseThrow").also(invokeSpec -> {
                    if (defaulted) {
                        invokeSpec.addArgument("null");
                    }
                })).setStatic(true).setFinal(true), variableStatement -> {
                final Access access = v.getAnnotation(Access.class);
                if (access != null) {
                    variableStatement.setAccessModifier(access.value());
                }
            });
        });
    }

    private String getDocument(Element element) {
        return processingEnv.getElementUtils().getDocComment(element);
    }

    private String getConstExp(Object value) {
        return processingEnv.getElementUtils().getConstantExpression(value);
    }

    private InvokeSpec createCharset(SourceFile file, String name) {
        file.addImport(Charset.class.getName());
        return new InvokeSpec(Charset.class.getSimpleName(), "forName").addArgument(getConstExp(name));
    }

    private static String getCustomCharset(Element e) {
        final SetCharset setCharset = e.getAnnotation(SetCharset.class);
        return setCharset != null ? setCharset.value() : "UTF-8";
    }

    private static String toTypeName(String rawClassName) {
        if (rawClassName.equals(String.class.getName())) {
            return String.class.getSimpleName();
        }
        if (rawClassName.equals(MemorySegment.class.getName())) {
            return MemorySegment.class.getSimpleName();
        }
        return rawClassName;
    }

    private static List<String> collectConflictedMethodSignature(ExecutableElement e) {
        return Stream.concat(Stream.of(e.getReturnType()), e.getParameters().stream().map(VariableElement::asType)).map(t -> {
            if (t.getKind() == TypeKind.VOID) {
                return ".VOID";
            }
            try {
                return toValueLayout(t);
            } catch (Exception ex) {
                return t.toString();
            }
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

    private static String toTargetType(TypeMirror typeMirror, String overload) {
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
                // TODO: 2023/12/2 Add support to struct
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

    private static IllegalStateException invalidType(TypeMirror typeMirror) {
        return new IllegalStateException("Invalid type: " + typeMirror);
    }

    private static boolean isMemorySegment(TypeMirror typeMirror) {
        return MemorySegment.class.getName().equals(typeMirror.toString());
    }

    private static boolean isString(String clazzName) {
        return String.class.getName().equals(clazzName);
    }

    private static boolean isString(TypeMirror typeMirror) {
        return isString(typeMirror.toString());
    }

    private static TypeMirror getArrayComponentType(TypeMirror typeMirror) {
        return ((ArrayType) typeMirror).getComponentType();
    }

    private static boolean isArray(TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.ARRAY;
    }

    private static boolean isBooleanArray(TypeMirror typeMirror) {
        return getArrayComponentType(typeMirror).getKind() == TypeKind.BOOLEAN;
    }

    private static boolean isPrimitiveArray(TypeMirror typeMirror) {
        return getArrayComponentType(typeMirror).getKind().isPrimitive();
    }

    private static String toValueLayout(TypeMirror typeMirror) {
        final TypeKind typeKind = typeMirror.getKind();
        return switch (typeKind) {
            case BOOLEAN -> "ValueLayout.JAVA_BOOLEAN";
            case BYTE -> "ValueLayout.JAVA_BYTE";
            case SHORT -> "ValueLayout.JAVA_SHORT";
            case INT -> "ValueLayout.JAVA_INT";
            case LONG -> "ValueLayout.JAVA_LONG";
            case CHAR -> "ValueLayout.JAVA_CHAR";
            case FLOAT -> "ValueLayout.JAVA_FLOAT";
            case DOUBLE -> "ValueLayout.JAVA_DOUBLE";
            case ARRAY -> {
                if (isPrimitiveArray(typeMirror)) yield "ValueLayout.ADDRESS";
                else throw invalidType(typeMirror);
            }
            case DECLARED -> {
                if (isMemorySegment(typeMirror) || isString(typeMirror)) yield "ValueLayout.ADDRESS";
                // TODO: 2023/12/2 Add support to struct
                throw invalidType(typeMirror);
            }
            default -> throw invalidType(typeMirror);
        };
    }

    private void printError(String msg) {
        processingEnv.getMessager().printError(msg);
    }

    private void printStackTrace(Throwable t) {
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
                printError(sb.toString());
            }
        }) {
            t.printStackTrace(writer);
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_22;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(NativeApi.class.getName());
    }
}
