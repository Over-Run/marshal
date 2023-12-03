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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author squid233
 * @since 0.1.0
 */
@SupportedAnnotationTypes("overrun.marshal.NativeApi")
@SupportedSourceVersion(SourceVersion.RELEASE_22)
public class NativeApiProcessor extends AbstractProcessor {
    private static final String PACKAGE_NAME = "overrun.marshal";

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
            entry.getKey().forEach(variable -> {
                final Object constantValue = variable.getConstantValue();
                if (constantValue == null) return;
                final String typeStr = variable.asType().toString();
                sb.append("    public static final ")
                    .append(isString(typeStr) ? "String" : typeStr)
                    .append(' ')
                    .append(variable.getSimpleName())
                    .append(" = ");
                if (constantValue instanceof String) {
                    sb.append('"').append(constantValue).append('"');
                } else {
                    sb.append(constantValue);
                }
                sb.append(";\n");
            });
            sb.append('\n');

            final List<ExecutableElement> methods = entry.getValue();
            if (!methods.isEmpty()) {
                // load library
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

                // method handles
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
                            throw new IllegalStateException("Overload not supported");
                        }
                        return e1;
                    }, LinkedHashMap::new))
                    .entrySet()
                    .stream()
                    .map(entry1 -> {
                        final ExecutableElement method = entry1.getValue();
                        final Native annotation = method.getAnnotation(Native.class);
                        final StringBuilder sb1 = new StringBuilder(256);
                        sb1.append("    ")
                            .append(annotation != null ? annotation.scope() : "public")
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
                        if (annotation != null && annotation.optional()) {
                            sb1.append("(null)");
                        } else {
                            sb1.append("Throw()");
                        }
                        return sb1.toString();
                    })
                    .collect(Collectors.joining(";\n"))).append(";\n\n");

                // method declarations
                methods.forEach(method -> {
                    final TypeMirror returnType = method.getReturnType();
                    final TypeKind returnTypeKind = returnType.getKind();
                    final boolean array = returnTypeKind == TypeKind.ARRAY;
                    final boolean booleanArray = array && isBooleanArray(returnType);
                    final Native annotation = method.getAnnotation(Native.class);
                    final Overload overloadAnnotation = method.getAnnotation(Overload.class);
                    final var parameters = method.getParameters();
                    // check @Ref
                    if (parameters.stream()
                        .filter(e -> e.getAnnotation(Ref.class) != null)
                        .anyMatch(e -> e.asType().getKind() != TypeKind.ARRAY)) {
                        throw new IllegalStateException("@Ref must be used on array types");
                    }
                    final boolean shouldInsertAllocator = parameters.stream().anyMatch(e -> {
                        final TypeMirror type1 = e.asType();
                        final TypeKind kind = type1.getKind();
                        return kind == TypeKind.ARRAY || (kind == TypeKind.DECLARED && isString(type1)); // TODO: 2023/12/2 Struct
                    });
                    final boolean notVoid = returnTypeKind != TypeKind.VOID;
                    final boolean shouldStoreResult = notVoid &&
                                                      parameters.stream().anyMatch(e -> e.getAnnotation(Ref.class) != null);
                    final String entrypoint = methodEntrypoint(method);
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
                    if (annotation != null) {
                        final String doc = annotation.doc();
                        if (!doc.isBlank()) {
                            sb.append("    /**\n");
                            doc.lines().map(s -> "     * " + s).forEach(s -> {
                                sb.append(s);
                                sb.append('\n');
                            });
                            sb.append("     */\n");
                        }
                    }
                    sb.append("    ")
                        // access modifier
                        .append(annotation != null ? annotation.scope() : "public")
                        .append(" static ")
                        // return type
                        .append(javaReturnType)
                        .append(' ')
                        .append(method.getSimpleName())
                        .append('(');
                    // parameters
                    if (shouldInsertAllocator) {
                        sb.append("SegmentAllocator _segmentAllocator, ");
                    }
                    sb.append(parameters.stream()
                        .map(e -> {
                            final Ref ref = e.getAnnotation(Ref.class);
                            final String refString = ref != null ?
                                "@" + PACKAGE_NAME + ".Ref" + (ref.nullable() ? "(nullable = true) " : " ") :
                                "";
                            return refString + toTargetType(e.asType(), overload) + " " + e.getSimpleName();
                        })
                        .collect(Collectors.joining(", "))).append(") {\n");
                    // method body
                    if (isOverload) {
                        // send a warning if using any boolean array
                        if (booleanArray || parameters.stream().anyMatch(e -> {
                            final TypeMirror type1 = e.asType();
                            return type1.getKind() == TypeKind.ARRAY && isBooleanArray(type1);
                        })) {
                            processingEnv.getMessager().printWarning(type + "::" + method + ": Marshalling boolean array");
                        }
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
                        if (array && booleanArray) {
                            sb.append(PACKAGE_NAME)
                                .append(".BoolHelper.toArray(");
                        }
                        sb.append(overload).append('(');
                        appendOverloadArgs(sb, parameters);
                        sb.append(')');
                        if (array) {
                            if (booleanArray) {
                                sb.append(')');
                            } else {
                                sb.append(".toArray(")
                                    .append(toValueLayout(returnType))
                                    .append(')');
                            }
                        }
                        sb.append(";\n");
                        appendRefArgs(sb, parameters);
                        if (shouldStoreResult) {
                            sb.append("        return $_marshalResult;\n");
                        }
                    } else {
                        sb.append("        try {\n            ");
                        if (notVoid) {
                            sb.append("return (").append(javaReturnType).append(") ");
                        }
                        sb.append(entrypoint).append(".invokeExact(");
                        // parameters
                        sb.append(parameters.stream()
                            .map(VariableElement::getSimpleName)
                            .collect(Collectors.joining(", ")));
                        sb.append(");\n        } catch (Throwable e) { throw new AssertionError(\"should not reach here\", e); }\n");
                    }
                    sb.append("    }\n\n");
                });
            }

            // end
            sb.append("}\n");
            out.print(sb);
        }
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

    private static void prependOverloadArgs(StringBuilder sb, List<? extends VariableElement> parameters) {
        parameters.forEach(e -> {
            final TypeMirror type = e.asType();
            final Ref ref = e.getAnnotation(Ref.class);
            final boolean array = type.getKind() == TypeKind.ARRAY;
            if (array && isPrimitiveArray(type) && ref != null) {
                final Name name = e.getSimpleName();
                final TypeMirror arrayComponentType = getArrayComponentType(type);
                sb.append("        ")
                    .append(MemorySegment.class.getSimpleName())
                    .append(" _")
                    .append(name)
                    .append(" = ");
                if (ref.nullable()) sb.append(name).append(" != null ? ");
                if (arrayComponentType.getKind() == TypeKind.BOOLEAN) {
                    sb.append(PACKAGE_NAME)
                        .append(".BoolHelper.of(_segmentAllocator, ")
                        .append(name)
                        .append(')');
                } else {
                    sb.append("_segmentAllocator.allocateFrom(")
                        .append(toValueLayout(arrayComponentType))
                        .append(", ")
                        .append(name)
                        .append(')');
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
                        throw invalidType(type);
                    }
                    case ARRAY -> {
                        final Ref ref = e.getAnnotation(Ref.class);
                        if (ref != null) {
                            yield "_" + e.getSimpleName();
                        }
                        final TypeMirror arrayComponentType = getArrayComponentType(type);
                        if (arrayComponentType.getKind() == TypeKind.BOOLEAN) {
                            yield PACKAGE_NAME + ".BoolHelper.of(_segmentAllocator, " + e.getSimpleName() + ")";
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
                sb.append("        ");
                if (nullable) {
                    sb.append("if (")
                        .append(name)
                        .append(" != null) { ");
                }
                if (isBooleanArray(e.asType())) {
                    sb.append(PACKAGE_NAME)
                        .append(".BoolHelper.copy(")
                        .append("_")
                        .append(name)
                        .append(", ")
                        .append(name)
                        .append(')');
                } else {
                    sb.append("MemorySegment.copy(")
                        .append("_")
                        .append(name)
                        .append(", ")
                        .append(toValueLayout(getArrayComponentType(e.asType())))
                        .append(", 0L, ")
                        .append(name)
                        .append(", 0, ")
                        .append(name)
                        .append(".length)");
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
        final Native annotation = method.getAnnotation(Native.class);
        if (annotation != null) {
            final String entrypoint = annotation.entrypoint();
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
                if (!isPrimitiveArray(typeMirror)) {
                    throw invalidType(typeMirror);
                }
                yield overload == null ? MemorySegment.class.getSimpleName() : typeMirror.toString();
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
                processingEnv.getMessager().printError(sb.toString());
            }
        }) {
            t.printStackTrace(writer);
        }
    }
}
