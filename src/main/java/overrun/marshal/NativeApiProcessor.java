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

import javax.annotation.processing.*;
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
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
@SupportedAnnotationTypes("overrun.marshal.NativeApi")
@SupportedSourceVersion(SourceVersion.RELEASE_22)
public final class NativeApiProcessor extends AbstractProcessor {
    private boolean disableBoolArrayWarn = false;

    /**
     * constructor
     */
    public NativeApiProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        disableBoolArrayWarn = Boolean.parseBoolean(processingEnv.getOptions().get("overrun.marshal.disableBoolArrayWarn"));
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
        final Set<? extends Element> marshalApis = roundEnv.getElementsAnnotatedWith(NativeApi.class);
        final Set<TypeElement> types = ElementFilter.typesIn(marshalApis);
        final var map = types.stream().collect(Collectors.toMap(Function.identity(), element -> {
            final var enclosed = element.getEnclosedElements();
            return Map.entry(ElementFilter.fieldsIn(enclosed), ElementFilter.methodsIn(enclosed));
        }));

        map.forEach((type, entry) -> {
            try {
                writeFile(type, map.get(type));
            } catch (IOException e) {
                printStackTrace(e);
            }
        });
    }

    private void writeFile(
        TypeElement type,
        Map.Entry<List<VariableElement>, List<javax.lang.model.element.ExecutableElement>> entry
    ) throws IOException {
        final NativeApi nativeApi = type.getAnnotation(NativeApi.class);
        final String className = type.getQualifiedName().toString();
        final int lastDot = className.lastIndexOf('.');
        final String packageName = lastDot > 0 ? className.substring(0, lastDot) : null;
        final String simpleClassName = nativeApi.name();

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            final StringBuilder sb = new StringBuilder(16384);
            sb.append("// This file is auto-generated. DO NOT EDIT!\n");

            // package
            if (packageName != null) {
                sb.append("package ")
                    .append(packageName)
                    .append(";\n")
                    .append('\n');
            }

            // import
            sb.append("""
                import overrun.marshal.BoolHelper;
                import overrun.marshal.Checks;
                import overrun.marshal.Default;
                import overrun.marshal.FixedSize;
                import overrun.marshal.Ref;
                import overrun.marshal.StrHelper;

                import java.lang.foreign.*;
                import java.lang.invoke.MethodHandle;

                """);

            // class declaration
            sb.append("public ");
            if (nativeApi.makeFinal()) {
                sb.append("final ");
            }
            sb.append("class ")
                .append(simpleClassName)
                .append(" {\n");

            // fields
            appendFields(entry.getKey(), sb);

            final List<ExecutableElement> methods = entry.getValue();
            if (!methods.isEmpty()) {
                // load library
                appendLoader(type, nativeApi, sb);

                // method handles
                appendMethodHandles(type, sb, methods);

                // method declarations
                appendMethodDecl(type, methods, sb);
            }

            // end
            sb.append("}\n");
            out.print(sb);
        }
    }

    private void appendMethodDecl(TypeElement type, List<ExecutableElement> methods, StringBuilder sb) {
        methods.forEach(method -> {
            final TypeMirror returnType = method.getReturnType();
            final TypeKind returnTypeKind = returnType.getKind();
            final var parameters = method.getParameters();
            // check annotations
            checkParamAnnotation(type, method, parameters);

            final Access accessAnnotation = method.getAnnotation(Access.class);
            final Overload overloadAnnotation = method.getAnnotation(Overload.class);
            final Custom customAnnotation = method.getAnnotation(Custom.class);
            final Default defaultAnnotation = method.getAnnotation(Default.class);
            final boolean shouldInsertAllocator = parameters.stream().anyMatch(e -> {
                final TypeMirror type1 = e.asType();
                return isArray(type1) || (type1.getKind() == TypeKind.DECLARED && isString(type1));
                // TODO: 2023/12/2 Struct
            });
            final boolean notVoid = returnTypeKind != TypeKind.VOID;
            final boolean shouldStoreResult = notVoid &&
                                              parameters.stream().anyMatch(e -> e.getAnnotation(Ref.class) != null);
            final boolean isOverload = overloadAnnotation != null;
            final String overload;
            if (isOverload) {
                final String value = overloadAnnotation.value();
                overload = value.isBlank() ? method.getSimpleName().toString() : value;
            } else {
                overload = null;
            }
            final String javaReturnType = toTargetType(returnType, overload);

            // javadoc
            appendJavadoc(sb, method.getAnnotation(Doc.class));

            // annotations
            if (defaultAnnotation != null) {
                sb.append("    @")
                    .append(Default.class.getSimpleName());
                if (!defaultAnnotation.value().isBlank()) {
                    final String indented = defaultAnnotation.value().indent(8);
                    sb.append("(\"\"\"\n")
                        .append(indented, 0, indented.length() - 1)
                        .append("\"\"\")");
                }
                sb.append('\n');
            }

            sb.append("    ")
                // access modifier
                .append(accessAnnotation != null ? accessAnnotation.value() : "public")
                .append(" static ")
                // return type
                .append(javaReturnType)
                .append(' ')
                .append(method.getSimpleName());

            // parameters
            sb.append('(');
            appendParameters(sb, shouldInsertAllocator, parameters, overload);
            sb.append(") {\n");

            // method body
            if (customAnnotation == null) {
                appendMethodBody(type,
                    sb,
                    method,
                    isOverload,
                    parameters,
                    notVoid,
                    shouldStoreResult,
                    overload,
                    returnType,
                    javaReturnType);
            } else {
                sb.append(customAnnotation.value().indent(8));
            }
            sb.append("    }\n\n");
        });
    }

    private void appendMethodBody(TypeElement type,
                                  StringBuilder sb,
                                  ExecutableElement method,
                                  boolean isOverload,
                                  List<? extends VariableElement> parameters,
                                  boolean notVoid,
                                  boolean shouldStoreResult,
                                  String overload,
                                  TypeMirror returnType,
                                  String javaReturnType) {
        if (isOverload) {
            final boolean returnArray = isArray(returnType);
            final boolean returnBooleanArray = returnArray && isBooleanArray(returnType);
            final boolean returnStringArray = returnArray && isString(getArrayComponentType(returnType));
            // send a warning if using any boolean array
            if (!disableBoolArrayWarn) {
                if (returnBooleanArray || parameters.stream().anyMatch(e -> {
                    final TypeMirror type1 = e.asType();
                    return isArray(type1) && isBooleanArray(type1);
                })) {
                    processingEnv.getMessager().printWarning(type + "::" + method + ": Marshalling boolean array");
                }
            }
            // check array size
            parameters.stream()
                .filter(e -> isArray(e.asType()) && e.getAnnotation(FixedSize.class) != null)
                .forEach(e -> {
                    final FixedSize fixedSize = e.getAnnotation(FixedSize.class);
                    sb.append("        ")
                        .append("Checks.checkArraySize(")
                        .append(fixedSize.value())
                        .append(", ")
                        .append(e.getSimpleName())
                        .append(".length);\n");
                });
            prependOverloadArgs(sb, parameters);
            if (notVoid) {
                if (shouldStoreResult) {
                    sb.append("        var $_marshalResult = ");
                } else {
                    sb.append("        return ");
                }
            } else {
                sb.append("        ");
            }
            if (returnArray) {
                if (returnBooleanArray) {
                    sb.append("BoolHelper.toArray(");
                } else if (returnStringArray) {
                    sb.append("StrHelper.toArray(");
                }
            }
            sb.append(overload).append('(');
            // arguments
            appendOverloadArgs(sb, parameters);
            sb.append(')');
            if (returnArray) {
                if (returnBooleanArray) {
                    sb.append(')');
                } else if (returnStringArray) {
                    sb.append(", ");
                    // TODO: 2023/12/3 charset
                    sb.append(StandardCharsets.class.getName())
                        .append(".UTF_8");
                    sb.append(')');
                } else {
                    sb.append(".toArray(")
                        .append(toValueLayout(returnType))
                        .append(')');
                }
            } else if (returnType.getKind() == TypeKind.DECLARED && isString(returnType)) {
                sb.append(".getString(0L)");
                // TODO: 2023/12/3 String charset
            }
            sb.append(";\n");
            appendRefArgs(sb, parameters);
            if (shouldStoreResult) {
                sb.append("        return $_marshalResult;\n");
            }
        } else {
            final Default defaulted = method.getAnnotation(Default.class);
            final boolean isDefaulted = defaulted != null;
            if (isDefaulted && notVoid && defaulted.value().isBlank()) {
                printError(type + "::" + method + ": Default non-void method must have a default return value");
                return;
            }
            final String entrypoint = methodEntrypoint(method);

            sb.append("        try {\n            ");

            if (isDefaulted) {
                sb.append("if (")
                    .append(entrypoint)
                    .append(" != null) {\n                ");
            }

            if (notVoid) {
                sb.append("return (").append(javaReturnType).append(") ");
            }
            sb.append(entrypoint).append(".invokeExact(")
                // arguments
                .append(parameters.stream()
                    .map(VariableElement::getSimpleName)
                    .collect(Collectors.joining(", ")))
                .append(");\n");

            if (isDefaulted) {
                sb.append("            }");
                if (notVoid) {
                    final String indented = defaulted.value().indent(16);
                    sb.append(" else { return \n")
                        .append(indented, 0, indented.length() - 1)
                        .append("; }");
                }
                sb.append('\n');
            }

            sb.append("        } catch (Throwable e) { throw new AssertionError(\"should not reach here\", e); }\n");
        }
    }

    private static void appendParameters(StringBuilder sb, boolean shouldInsertAllocator, List<? extends VariableElement> parameters, String overload) {
        if (shouldInsertAllocator) {
            sb.append("SegmentAllocator _segmentAllocator, ");
        }
        sb.append(parameters.stream()
            .map(e -> {
                final Ref ref = e.getAnnotation(Ref.class);
                final String refString = ref != null ?
                    "@" + Ref.class.getSimpleName() + (ref.nullable() ? "(nullable = true) " : " ") :
                    "";
                final FixedSize fixedSize = e.getAnnotation(FixedSize.class);
                final String fixedSizeString = fixedSize != null ?
                    "@" + FixedSize.class.getSimpleName() + "(" + fixedSize.value() + ") " :
                    "";
                return refString + fixedSizeString + toTargetType(e.asType(), overload) + " " + e.getSimpleName();
            })
            .collect(Collectors.joining(", ")));
    }

    private static void appendJavadoc(StringBuilder sb, Doc annotation) {
        if (annotation != null) {
            final String doc = annotation.value();
            if (!doc.isBlank()) {
                sb.append("    /**\n");
                doc.lines().map(s -> "     * " + s).forEach(s -> {
                    sb.append(s);
                    sb.append('\n');
                });
                sb.append("     */\n");
            }
        }
    }

    private void checkParamAnnotation(TypeElement type, ExecutableElement method, List<? extends VariableElement> parameters) {
        parameters.stream().filter(e -> !isArray(e.asType())).forEach(e -> {
            final Ref ref = e.getAnnotation(Ref.class);
            final FixedSize fixedSize = e.getAnnotation(FixedSize.class);
            final String prefix = type + "::" + method + ": Using ";
            final String suffix = " on non-array parameter " + e.asType() + " " + e.getSimpleName();
            if (ref != null) {
                printError(prefix + "@Ref" + suffix);
            }
            if (fixedSize != null) {
                printError(prefix + "@FixedSize" + suffix);
            }
        });
    }

    private void appendMethodHandles(TypeElement type, StringBuilder sb, List<ExecutableElement> methods) {
        // try to collect function names
        sb.append(methods.stream()
            .collect(Collectors.toMap(NativeApiProcessor::methodEntrypoint, Function.identity(), (e1, e2) -> {
                // conflict
                final Overload o1 = e1.getAnnotation(Overload.class);
                final Overload o2 = e2.getAnnotation(Overload.class);
                if (o1 != null) {
                    if (o2 != null) {
                        return e1;
                    }
                    return e2;
                }
                if (o2 != null) {
                    return e1;
                }
                // neither e1 nor e2 is marked as overload
                if (e1.getSimpleName().contentEquals(e2.getSimpleName())) {
                    printError("Overload not supported between " + type + "::" + e1 + " and " + e2);
                }
                return e1;
            }, LinkedHashMap::new))
            .entrySet()
            .stream()
            .map(entry1 -> {
                final ExecutableElement method = entry1.getValue();
                final Access access = method.getAnnotation(Access.class);
                final Default defaulted = method.getAnnotation(Default.class);
                final StringBuilder sb1 = new StringBuilder(256);
                sb1.append("    ")
                    .append(access != null ? access.value() : "public")
                    .append(" static final MethodHandle ")
                    .append(entry1.getKey())
                    .append(" = _LOOKUP.find(\"")
                    .append(entry1.getKey())
                    .append("\").map(_s -> _LINKER.downcallHandle(_s, FunctionDescriptor.of");
                // append function descriptor
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    sb1.append("Void(");
                } else {
                    sb1.append('(').append(toValueLayout(method.getReturnType()));
                    if (!method.getParameters().isEmpty()) sb1.append(", ");
                }
                sb1.append(method.getParameters().stream()
                    .map(e -> toValueLayout(e.asType()))
                    .collect(Collectors.joining(", ")));
                sb1.append("))).orElse");
                if (defaulted != null) {
                    sb1.append("(null)");
                } else {
                    sb1.append("Throw()");
                }
                return sb1.toString();
            })
            .collect(Collectors.joining(";\n"))).append(";\n\n");
    }

    private static void appendLoader(TypeElement type, NativeApi nativeApi, StringBuilder sb) {
        final String libname = nativeApi.libname();
        final AnnotationMirror annotationMirror = type.getAnnotationMirrors().stream()
            .filter(m -> NativeApi.class.getName().equals(m.getAnnotationType().toString()))
            .findFirst()
            .orElseThrow();
        final String selector = annotationMirror.getElementValues().entrySet().stream()
            .filter(entry1 -> "selector()".equals(entry1.getKey().toString()))
            .findFirst()
            .map(entry2 -> entry2.getValue().getValue().toString())
            .orElse(null);
        sb.append("    private static final SymbolLookup _LOOKUP = SymbolLookup.libraryLookup(");
        if (selector != null) {
            sb.append("new ")
                .append(selector)
                .append("().select(\"")
                .append(libname)
                .append("\"), Arena.global()");
        } else {
            sb.append('"').append(libname).append("\", Arena.global()");
        }
        sb.append(");\n    private static final Linker _LINKER = Linker.nativeLinker();\n\n");
    }

    private static void appendFields(List<VariableElement> fields, StringBuilder sb) {
        fields.forEach(e -> {
            final Object constantValue = e.getConstantValue();
            if (constantValue == null) return;
            final String typeStr = e.asType().toString();
            final Doc doc = e.getAnnotation(Doc.class);
            if (doc != null && !doc.value().isBlank()) {
                sb.append("    /**\n");
                doc.value().lines().map(s -> "     * " + s).forEach(s -> {
                    sb.append(s);
                    sb.append('\n');
                });
                sb.append("     */\n");
            }
            sb.append("    public static final ")
                .append(isString(typeStr) ? String.class.getSimpleName() : typeStr)
                .append(' ')
                .append(e.getSimpleName())
                .append(" = ");
            if (constantValue instanceof String) {
                sb.append('"').append(constantValue).append('"');
            } else {
                sb.append(constantValue);
            }
            sb.append(";\n");
        });
        sb.append('\n');
    }

    private static void prependOverloadArgs(StringBuilder sb, List<? extends VariableElement> parameters) {
        parameters.forEach(e -> {
            final TypeMirror type = e.asType();
            final Ref ref = e.getAnnotation(Ref.class);
            if (isArray(type) && ref != null) {
                final Name name = e.getSimpleName();
                final TypeMirror arrayComponentType = getArrayComponentType(type);
                final TypeKind typeKind = arrayComponentType.getKind();
                sb.append("        ")
                    .append(MemorySegment.class.getSimpleName())
                    .append(" _")
                    .append(name)
                    .append(" = ");
                if (ref.nullable()) sb.append(name).append(" != null ? ");
                if (typeKind == TypeKind.BOOLEAN) {
                    sb.append("BoolHelper.of(_segmentAllocator, ")
                        .append(name)
                        .append(')');
                } else if (typeKind.isPrimitive()) {
                    sb.append("_segmentAllocator.allocateFrom(")
                        .append(toValueLayout(arrayComponentType))
                        .append(", ")
                        .append(name)
                        .append(')');
                } else if (typeKind == TypeKind.DECLARED) {
                    if (isString(arrayComponentType)) {
                        sb.append("StrHelper.of(_segmentAllocator, ")
                            .append(name)
                            .append(", ")
                            .append(StandardCharsets.class.getName())
                            .append(".UTF_8")
                            .append(')');
                        // TODO: 2023/12/3 charset
                    }
                }
                if (ref.nullable()) sb.append(" : MemorySegment.NULL");
                sb.append(";\n");
            }
        });
    }

    private static void appendOverloadArgs(StringBuilder sb, List<? extends VariableElement> parameters) {
        sb.append(parameters.stream()
            .map(e -> {
                final TypeMirror type = e.asType();
                final TypeKind typeKind = type.getKind();
                if (typeKind.isPrimitive()) {
                    return e.getSimpleName();
                }
                return switch (typeKind) {
                    case DECLARED -> {
                        // TODO: 2023/12/2 Add support to struct
                        if (isMemorySegment(type)) yield e.getSimpleName();
                        if (isString(type)) yield "_segmentAllocator.allocateFrom(" + e.getSimpleName() + ")";
                        // TODO: 2023/12/3 String charset
                        throw invalidType(type);
                    }
                    case ARRAY -> {
                        final Ref ref = e.getAnnotation(Ref.class);
                        if (ref != null) {
                            yield "_" + e.getSimpleName();
                        }
                        final TypeMirror arrayComponentType = getArrayComponentType(type);
                        if (arrayComponentType.getKind() == TypeKind.BOOLEAN) {
                            yield "BoolHelper.of(_segmentAllocator, " + e.getSimpleName() + ")";
                        }
                        if (arrayComponentType.getKind() == TypeKind.DECLARED &&
                            isString(arrayComponentType)) {
                            yield "StrHelper.of(_segmentAllocator, " + e.getSimpleName() + ", " + StandardCharsets.class.getName() + ".UTF_8" + ")";
                            // TODO: 2023/12/3 charset
                        }
                        yield "_segmentAllocator.allocateFrom(" + toValueLayout(arrayComponentType) + ", " + e.getSimpleName() + ")";
                    }
                    default -> throw invalidType(type);
                };
            })
            .collect(Collectors.joining(", ")));
    }

    private static void appendRefArgs(StringBuilder sb, List<? extends VariableElement> parameters) {
        parameters.forEach(e -> {
            final Ref ref = e.getAnnotation(Ref.class);
            if (ref != null) {
                final boolean nullable = ref.nullable();
                final Name name = e.getSimpleName();
                final TypeMirror type = e.asType();
                sb.append("        ");
                if (nullable) {
                    sb.append("if (")
                        .append(name)
                        .append(" != null) { ");
                }
                if (isBooleanArray(type)) {
                    sb.append("BoolHelper.copy(")
                        .append("_")
                        .append(name)
                        .append(", ")
                        .append(name)
                        .append(')');
                } else {
                    final TypeMirror arrayComponentType = getArrayComponentType(type);
                    if (isPrimitiveArray(type)) {
                        sb.append("MemorySegment.copy(")
                            .append("_")
                            .append(name)
                            .append(", ")
                            .append(toValueLayout(arrayComponentType))
                            .append(", 0L, ")
                            .append(name)
                            .append(", 0, ")
                            .append(name)
                            .append(".length)");
                    } else if (arrayComponentType.getKind() == TypeKind.DECLARED &&
                               isString(arrayComponentType)) {
                        sb.append("StrHelper.copy(")
                            .append("_")
                            .append(name)
                            .append(", ")
                            .append(name)
                            .append(", ");
                        sb.append(StandardCharsets.class.getName())
                            .append(".UTF_8");
                        // TODO: 2023/12/3 charset
                        sb.append(')');
                    }
                }
                sb.append(';');
                if (nullable) {
                    sb.append(" }");
                }
                sb.append('\n');
            }
        });
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
}
