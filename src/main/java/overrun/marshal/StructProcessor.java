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

import overrun.marshal.gen.*;
import overrun.marshal.gen.struct.*;
import overrun.marshal.gen1.*;
import overrun.marshal.internal.Processor;
import overrun.marshal.struct.*;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static overrun.marshal.internal.Util.*;

/**
 * Struct annotation processor
 *
 * @author squid233
 * @since 0.1.0
 */
public final class StructProcessor extends Processor {
    /**
     * constructor
     */
    public StructProcessor() {
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
        ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(Struct.class)).forEach(e -> {
            final var enclosed = e.getEnclosedElements();
            try {
                writeFile(e, ElementFilter.fieldsIn(enclosed));
            } catch (IOException ex) {
                printStackTrace(ex);
            }
        });
    }

    private void writeFile(
        TypeElement type,
        List<VariableElement> fields
    ) throws IOException {
        final Struct struct = type.getAnnotation(Struct.class);
        final String className = type.getQualifiedName().toString();
        final int lastDot = className.lastIndexOf('.');
        final String packageName = lastDot > 0 ? className.substring(0, lastDot) : null;
        final String simpleClassName;
        if (struct.name().isBlank()) {
            final String string = className.substring(lastDot + 1);
            if (string.startsWith("C")) {
                simpleClassName = string.substring(1);
            } else {
                printError("""
                    Class name must start with C if the name is not specified. Current name: %s
                         Possible solutions:
                         1) Add C as a prefix. For example: C%1$s
                         2) Specify name in @Struct and rename this file. For example: @Struct(name = "%1$s")"""
                    .formatted(string));
                return;
            }
        } else {
            simpleClassName = struct.name();
        }

        final SourceFile file = new SourceFile(packageName);
        file.addImports("overrun.marshal.*", "java.lang.foreign.*");
        file.addImports(
            MemoryLayout.PathElement.class,
            VarHandle.class
        );
        file.addClass(simpleClassName, classSpec -> {
            final boolean typeConst = type.getAnnotation(Const.class) != null;

            classSpec.setDocument(getDocument(type));
            if (typeConst) {
                classSpec.addAnnotation(new AnnotationSpec(Const.class.getCanonicalName()));
            }
            classSpec.setFinal(!struct.nonFinal());
            classSpec.addSuperinterface(IStruct.class.getCanonicalName());

            // layout
            classSpec.addField(new VariableStatement(StructLayout.class, "LAYOUT",
                new InvokeSpec(MemoryLayout.class, "structLayout").also(invokeSpec -> forEach(fields, true, e -> {
                    final TypeMirror eType = e.asType();
                    final Padding padding = e.getAnnotation(Padding.class);
                    if (padding != null) {
                        invokeSpec.addArgument(new InvokeSpec(MemoryLayout.class, "paddingLayout")
                            .addArgument(getConstExp(padding.value())));
                    } else if (isValueType(eType)) {
                        InvokeSpec member = new InvokeSpec(e.getAnnotation(StructRef.class) != null ?
                            "ValueLayout.ADDRESS" :
                            toValueLayoutStr(eType), "withName")
                            .addArgument(getConstExp(e.getSimpleName().toString()));
                        if (isArray(eType)) {
                            final Sized sized = e.getAnnotation(Sized.class);
                            if (sized != null) {
                                member = new InvokeSpec(member, "withTargetLayout")
                                    .addArgument(new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                                        .addArgument(getConstExp(sized.value()))
                                        .addArgument(toValueLayoutStr(getArrayComponentType(eType))));
                            }
                        } else if (isMemorySegment(eType)) {
                            final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
                            if (sizedSeg != null) {
                                member = new InvokeSpec(member, "withTargetLayout")
                                    .addArgument(new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                                        .addArgument(getConstExp(sizedSeg.value()))
                                        .addArgument(Spec.accessSpec(ValueLayout.class, "JAVA_BYTE")));
                            }
                        }
                        invokeSpec.addArgument(member);
                    } else {
                        printError("Unsupported field: " + eType + ' ' + e.getSimpleName());
                    }
                })))
                .setStatic(true)
                .setFinal(true), variableStatement -> {
                // document
                final StringBuilder sb = new StringBuilder(512);
                sb.append(" The layout of this struct.\n <pre>{@code\n struct ").append(simpleClassName).append(" {\n");
                forEach(fields, false, e -> {
                    final String eTypeString = e.asType().toString();
                    final boolean endsWithArray = eTypeString.endsWith("[]");
                    final String simplifiedString = simplify(endsWithArray ?
                        eTypeString.substring(0, eTypeString.length() - 2) :
                        eTypeString) + (endsWithArray ? "[]" : "");
                    final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                    sb.append("     ");
                    if (strCharset != null) {
                        sb.append('(').append(getCustomCharset(e)).append(") ");
                    }
                    if (typeConst || e.getAnnotation(Const.class) != null) {
                        sb.append("final ");
                    }
                    final StructRef structRef = e.getAnnotation(StructRef.class);
                    if (structRef != null) {
                        sb.append("struct ").append(structRef.value());
                    } else if (isMemorySegmentSimple(simplifiedString)) {
                        final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
                        sb.append("void");
                        if (sizedSeg != null) {
                            sb.append('[').append(getConstExp(sizedSeg.value())).append(']');
                        } else {
                            sb.append('*');
                        }
                    } else {
                        final Sized sized = e.getAnnotation(Sized.class);
                        if (sized != null && simplifiedString.endsWith("[]")) {
                            sb.append(simplifiedString, 0, simplifiedString.length() - 1).append(getConstExp(sized.value())).append(']');
                        } else {
                            sb.append(simplifiedString);
                        }
                    }
                    sb.append(' ')
                        .append(e.getSimpleName())
                        .append(";\n");
                });
                sb.append(" }\n }</pre>");
                variableStatement.setDocument(sb.toString());
            });

            // path elements
            classSpec.addField(new VariableStatement(MemoryLayout.PathElement.class, "_SEQUENCE_ELEMENT",
                new InvokeSpec(MemoryLayout.PathElement.class, "sequenceElement"))
                .setAccessModifier(AccessModifier.PRIVATE)
                .setStatic(true)
                .setFinal(true));
            forEach(fields, false, e -> classSpec.addField(new VariableStatement(MemoryLayout.PathElement.class, "_PE_" + e.getSimpleName(),
                new InvokeSpec(MemoryLayout.PathElement.class, "groupElement").addArgument(getConstExp(e.getSimpleName().toString())))
                .setAccessModifier(AccessModifier.PRIVATE)
                .setStatic(true)
                .setFinal(true)));

            classSpec.addField(new VariableStatement(MemorySegment.class, "_memorySegment", null)
                .setAccessModifier(AccessModifier.PRIVATE)
                .setFinal(true));
            classSpec.addField(new VariableStatement(SequenceLayout.class, "_sequenceLayout", null)
                .setAccessModifier(AccessModifier.PRIVATE)
                .setFinal(true));

            // var handles
            forEach(fields, false, e -> classSpec.addField(new VariableStatement(VarHandle.class, e.getSimpleName().toString(), null)
                .setAccessModifier(AccessModifier.PRIVATE)
                .setFinal(true)));

            // constructor
            classSpec.addMethod(new MethodSpec(null, simpleClassName), methodSpec -> {
                methodSpec.setDocument("""
                     Creates {@code %s} with the given segment and element count.

                     @param segment the segment
                     @param count the element count\
                    """.formatted(simpleClassName));
                methodSpec.addParameter(MemorySegment.class, "segment");
                methodSpec.addParameter(long.class, "count");
                methodSpec.addStatement(Spec.assignStatement("this._memorySegment", Spec.literal("segment")));
                methodSpec.addStatement(Spec.assignStatement("this._sequenceLayout", new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                    .addArgument("count")
                    .addArgument("LAYOUT")));
                forEach(fields, false, e -> {
                    final var name = e.getSimpleName().toString();
                    methodSpec.addStatement(Spec.assignStatement(Spec.accessSpec("this", name),
                        new InvokeSpec(Spec.literal("this._sequenceLayout"), "varHandle")
                            .addArgument("_SEQUENCE_ELEMENT")
                            .addArgument("_PE_" + name)));
                });
            });
            classSpec.addMethod(new MethodSpec(null, simpleClassName), methodSpec -> {
                methodSpec.setDocument("""
                     Creates {@code %s} with the given segment. The count is auto-inferred.

                     @param segment the segment\
                    """.formatted(simpleClassName));
                methodSpec.addParameter(MemorySegment.class, "segment");
                methodSpec.addStatement(Spec.statement(InvokeSpec.invokeThis()
                    .addArgument("segment")
                    .addArgument(new InvokeSpec(IStruct.class.getCanonicalName(), "inferCount")
                        .addArgument("LAYOUT")
                        .addArgument("segment"))));
            });

            // allocator
            classSpec.addMethod(new MethodSpec(simpleClassName, "create"), methodSpec -> {
                methodSpec.setDocument("""
                     Allocates {@code %s}.

                     @param allocator the allocator
                     @return the allocated {@code %1$s}\
                    """.formatted(simpleClassName));
                methodSpec.setStatic(true);
                methodSpec.addParameter(SegmentAllocator.class, "allocator");
                methodSpec.addStatement(Spec.returnStatement(new ConstructSpec(simpleClassName)
                    .addArgument(new InvokeSpec("allocator", "allocate")
                        .addArgument("LAYOUT"))
                    .addArgument(Spec.literal("1"))));
            });
            classSpec.addMethod(new MethodSpec(simpleClassName, "create"), methodSpec -> {
                methodSpec.setDocument("""
                     Allocates {@code %s} with the given count.

                     @param allocator the allocator
                     @param count the element count
                     @return the allocated {@code %1$s}\
                    """.formatted(simpleClassName));
                methodSpec.setStatic(true);
                methodSpec.addParameter(SegmentAllocator.class, "allocator");
                methodSpec.addParameter(long.class, "count");
                methodSpec.addStatement(Spec.returnStatement(new ConstructSpec(simpleClassName)
                    .addArgument(new InvokeSpec("allocator", "allocate")
                        .addArgument("LAYOUT")
                        .addArgument("count"))
                    .addArgument(Spec.literal("count"))));
            });

            // slice
            classSpec.addMethod(new MethodSpec(simpleClassName, "get"), methodSpec -> {
                methodSpec.setDocument("""
                     {@return a slice of this struct}

                     @param index the index of the slice\
                    """);
                methodSpec.addParameter(long.class, "index");
                methodSpec.addStatement(Spec.returnStatement(new ConstructSpec(simpleClassName)
                    .addArgument(new InvokeSpec("this._memorySegment", "asSlice")
                        .addArgument(Spec.operatorSpec("*",
                            Spec.literal("index"),
                            new InvokeSpec("LAYOUT", "byteSize")))
                        .addArgument("LAYOUT"))
                    .addArgument(Spec.literal("1L"))));
            });

            // member
            forEach(fields, false, e -> {
                final TypeMirror eType = e.asType();
                final String nameString = e.getSimpleName().toString();
                if (isValueType(eType)) {
                    final String eTypeString = eType.toString();
                    final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
                    final Sized sized = e.getAnnotation(Sized.class);
                    final StructRef structRef = e.getAnnotation(StructRef.class);
                    final boolean isStruct = structRef != null;
                    final boolean canConvertToAddress = isStruct || canConvertToAddress(eType);
                    final String returnType = canConvertToAddress ? MemorySegment.class.getSimpleName() : eTypeString;
                    final String overloadReturnType = isStruct ? structRef.value() : simplify(eTypeString);
                    final String capitalized = capitalize(nameString);
                    final boolean isMemorySegment = isMemorySegment(eType);
                    final boolean isArray = isArray(eType);
                    final boolean isUpcall = isUpcall(eType);
                    final boolean generateN = canConvertToAddress && (isStruct || !isMemorySegment);

                    // getter
                    final Spec castSpec = Spec.cast(returnType,
                        new InvokeSpec(Spec.accessSpec("this", nameString), "get")
                            .addArgument("this._memorySegment")
                            .addArgument("0L")
                            .addArgument("index"));
                    addGetterAt(classSpec, e, generateN ? "nget" : "get", returnType, null,
                        (isMemorySegment && sizedSeg != null || isArray && sized != null) ?
                            new InvokeSpec(Spec.parentheses(castSpec), "reinterpret")
                                .addArgument(sizedSeg != null ?
                                    Spec.literal(getConstExp(sizedSeg.value())) :
                                    Spec.operatorSpec("*",
                                        Spec.literal(getConstExp(sized.value())),
                                        new InvokeSpec(toValueLayoutStr(getArrayComponentType(eType)), "byteSize"))) :
                            (isStruct ?
                                new InvokeSpec(Spec.parentheses(castSpec), "reinterpret")
                                    .addArgument(new InvokeSpec(Spec.accessSpec(structRef.value(), "LAYOUT"), "byteSize")) :
                                castSpec));
                    if (generateN) {
                        Spec storeResult = null;
                        final Spec returnValue = new InvokeSpec("this", "nget" + capitalized + "At")
                            .addArgument("index");
                        Spec finalReturnValue;
                        final boolean isString = isString(eType);
                        if (isString || isArray || isUpcall || isStruct) {
                            storeResult = new VariableStatement(MemorySegment.class, "$_marshalResult", returnValue)
                                .setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                                .setFinal(true);
                            finalReturnValue = Spec.literal("$_marshalResult");
                        } else {
                            finalReturnValue = returnValue;
                        }
                        if (isStruct) {
                            finalReturnValue = new ConstructSpec(overloadReturnType)
                                .addArgument(finalReturnValue);
                        } else if (isString) {
                            final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                            final InvokeSpec invokeSpec = new InvokeSpec(finalReturnValue, "getString")
                                .addArgument("0");
                            if (strCharset != null) {
                                invokeSpec.addArgument(createCharset(file, getCustomCharset(e)));
                            }
                            finalReturnValue = invokeSpec;
                        } else if (isArray) {
                            final TypeMirror arrayComponentType = getArrayComponentType(eType);
                            if (sized == null) {
                                finalReturnValue = new InvokeSpec(finalReturnValue, "reinterpret")
                                    .addArgument(Spec.operatorSpec("*", Spec.literal("count"),
                                        new InvokeSpec(toValueLayoutStr(arrayComponentType), "byteSize")));
                            }
                            if (isBooleanArray(eType)) {
                                finalReturnValue = new InvokeSpec(BoolHelper.class.getCanonicalName(), "toArray")
                                    .addArgument(finalReturnValue);
                            } else if (isStringArray(eType)) {
                                finalReturnValue = new InvokeSpec(StrHelper.class.getCanonicalName(), "toArray")
                                    .addArgument(finalReturnValue)
                                    .addArgument(createCharset(file, getCustomCharset(e)));
                            } else {
                                finalReturnValue = new InvokeSpec(finalReturnValue, "toArray")
                                    .addArgument(toValueLayoutStr(arrayComponentType));
                            }
                        } else if (isUpcall) {
                            // find wrap method
                            final var wrapMethod = findUpcallWrapperMethod(eType);
                            if (wrapMethod.isPresent()) {
                                finalReturnValue = new InvokeSpec(eTypeString, wrapMethod.get().getSimpleName().toString())
                                    .addArgument(finalReturnValue);
                            } else {
                                printError(wrapperNotFound(eType, type, ".", nameString, "Upcall.Wrapper"));
                                return;
                            }
                        }
                        if (storeResult != null) {
                            finalReturnValue = Spec.ternaryOp(Spec.neqSpec(new InvokeSpec("$_marshalResult", "address"), Spec.literal("0L")),
                                finalReturnValue,
                                Spec.literal("null"));
                        }
                        addGetterAt(classSpec, e, "get", overloadReturnType, storeResult, finalReturnValue);
                    }

                    // setter
                    if (!typeConst && e.getAnnotation(Const.class) == null) {
                        final String paramIndex = convertParamIndex(nameString);
                        final String paramSegmentAllocator = convertParamSegmentAllocator(nameString);
                        addSetterAt(classSpec, e, generateN ? "nset" : "set", returnType,
                            Spec.statement(new InvokeSpec(Spec.accessSpec("this", nameString), "set")
                                .addArgument("this._memorySegment")
                                .addArgument("0L")
                                .also(invokeSpec -> {
                                    invokeSpec.addArgument(paramIndex);
                                    invokeSpec.addArgument(nameString);
                                })));
                        if (generateN) {
                            final InvokeSpec invokeSpec = new InvokeSpec("this", "nset" + capitalized + "At");
                            invokeSpec.addArgument(paramIndex);
                            if (isString(eType)) {
                                final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                                final InvokeSpec alloc = new InvokeSpec(paramSegmentAllocator, "allocateFrom")
                                    .addArgument(nameString);
                                if (strCharset != null) {
                                    alloc.addArgument(createCharset(file, getCustomCharset(e)));
                                }
                                invokeSpec.addArgument(alloc);
                            } else if (isArray) {
                                if (isBooleanArray(eType)) {
                                    invokeSpec.addArgument(new InvokeSpec(BoolHelper.class.getCanonicalName(), "of")
                                        .addArgument(paramSegmentAllocator)
                                        .addArgument(nameString));
                                } else if (isStringArray(eType)) {
                                    invokeSpec.addArgument(new InvokeSpec(StrHelper.class.getCanonicalName(), "of")
                                        .addArgument(paramSegmentAllocator)
                                        .addArgument(nameString)
                                        .addArgument(createCharset(file, getCustomCharset(e))));
                                } else {
                                    invokeSpec.addArgument(new InvokeSpec(paramSegmentAllocator, "allocateFrom")
                                        .addArgument(toValueLayoutStr(getArrayComponentType(eType)))
                                        .addArgument(nameString));
                                }
                            } else if (isUpcall) {
                                invokeSpec.addArgument(new InvokeSpec(nameString, "stub")
                                    .addArgument(convertParamArena(nameString)));
                            } else if (isStruct) {
                                invokeSpec.addArgument(new InvokeSpec(nameString, "segment"));
                            } else {
                                invokeSpec.addArgument(nameString);
                            }
                            addSetterAt(classSpec, e, "set", overloadReturnType, Spec.statement(invokeSpec));
                        }
                    }
                }
            });

            // override
            addIStructImpl(classSpec, MemorySegment.class, "segment", Spec.literal("this._memorySegment"));
            addIStructImpl(classSpec, StructLayout.class, "layout", Spec.literal("LAYOUT"));
            addIStructImpl(classSpec, long.class, "elementCount", new InvokeSpec("this._sequenceLayout", "elementCount"));
        });

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + '.' + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private void addSetterAt(ClassSpec classSpec, VariableElement e, String setType, String valueType, Spec setter) {
        final String nameString = e.getSimpleName().toString();
        final TypeMirror eType = e.asType();
        final boolean insertArena = "set".equals(setType) && isUpcall(eType);
        final boolean insertAllocator = !insertArena && "set".equals(setType) && (isString(eType) || isArray(eType));
        classSpec.addMethod(new MethodSpec("void", setType + capitalize(nameString) + "At"), methodSpec -> {
            final String eDoc = getDocument(e);
            final String paramIndex = convertParamIndex(nameString);
            final String paramArena = convertParamArena(nameString);
            final String paramSegmentAllocator = convertParamSegmentAllocator(nameString);
            methodSpec.setDocument("""
                 Sets %s at the given index.

                 %s@param %s the index
                 @param %s the value\
                """.formatted(eDoc != null ? eDoc : "{@code " + nameString + '}',
                insertArena ?
                    "@param " + paramArena + " the arena\n " :
                    (insertAllocator ? "@param " + paramSegmentAllocator + " the allocator\n " : ""),
                paramIndex,
                nameString));
            if (insertArena) {
                methodSpec.addParameter(Arena.class, paramArena);
            } else if (insertAllocator) {
                methodSpec.addParameter(SegmentAllocator.class, paramSegmentAllocator);
            }
            methodSpec.addParameter(long.class, paramIndex);
            addSetterValue(e, valueType, methodSpec);
            methodSpec.addStatement(setter);
        });
        addSetter(classSpec, e, setType, valueType, insertArena, insertAllocator);
    }

    private void addSetter(ClassSpec classSpec, VariableElement e, String setType, String valueType, boolean insertArena, boolean insertAllocator) {
        final String nameString = e.getSimpleName().toString();
        final String name = setType + capitalize(nameString);
        classSpec.addMethod(new MethodSpec("void", name), methodSpec -> {
            final String eDoc = getDocument(e);
            final String paramArena = convertParamArena(nameString);
            final String paramSegmentAllocator = convertParamSegmentAllocator(nameString);
            methodSpec.setDocument("""
                 Sets the first %s.

                 %s@param %s the value\
                """.formatted(eDoc != null ? eDoc : "{@code " + nameString + '}',
                insertArena ?
                    ("@param " + paramArena + " the arena\n ") :
                    (insertAllocator ? ("@param " + paramSegmentAllocator + " the allocator\n ") : ""),
                nameString));
            if (insertArena) {
                methodSpec.addParameter(Arena.class, paramArena);
            } else if (insertAllocator) {
                methodSpec.addParameter(SegmentAllocator.class, paramSegmentAllocator);
            }
            addSetterValue(e, valueType, methodSpec);
            methodSpec.addStatement(Spec.statement(new InvokeSpec("this", name + "At")
                .also(invokeSpec -> {
                    if (insertArena) {
                        invokeSpec.addArgument(paramArena);
                    } else if (insertAllocator) {
                        invokeSpec.addArgument(paramSegmentAllocator);
                    }
                })
                .addArgument("0L")
                .addArgument(nameString)));
        });
    }

    private void addGetterAt(ClassSpec classSpec, VariableElement e, String getType, String returnType, Spec storeResult, Spec returnValue) {
        final String nameString = e.getSimpleName().toString();
        final Sized sized = e.getAnnotation(Sized.class);
        final boolean insertCount = "get".equals(getType) && sized == null && isArray(e.asType());
        classSpec.addMethod(new MethodSpec(returnType, getType + capitalize(nameString) + "At"), methodSpec -> {
            final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Gets %s at the given index.

                 %s@param index the index
                 @return %1$s\
                """.formatted(eDoc != null ? eDoc : "{@code " + nameString + '}', insertCount ? "@param count the length of the array\n " : ""));
            addGetterAnnotation(e, returnType, methodSpec, sizedSeg, sized);
            if (insertCount) {
                methodSpec.addParameter(int.class, "count");
            }
            methodSpec.addParameter(long.class, "index");
            if (storeResult != null) {
                methodSpec.addStatement(storeResult);
            }
            methodSpec.addStatement(Spec.returnStatement(returnValue));
        });
        addGetter(classSpec, e, getType, returnType, insertCount);
    }

    private void addGetter(ClassSpec classSpec, VariableElement e, String getType, String returnType, boolean insertCount) {
        final String nameString = e.getSimpleName().toString();
        final String name = getType + capitalize(nameString);
        classSpec.addMethod(new MethodSpec(returnType, name), methodSpec -> {
            final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
            final Sized sized = e.getAnnotation(Sized.class);
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Gets the first %s.

                 %s@return %1$s\
                """.formatted(eDoc != null ? eDoc : "{@code " + nameString + '}', insertCount ? "@param count the length of the array\n " : ""));
            addGetterAnnotation(e, returnType, methodSpec, sizedSeg, sized);
            if (insertCount) {
                methodSpec.addParameter(int.class, "count");
            }
            methodSpec.addStatement(Spec.returnStatement(new InvokeSpec("this", name + "At")
                .also(invokeSpec -> {
                    if (insertCount) {
                        invokeSpec.addArgument("count");
                    }
                })
                .addArgument("0L")));
        });
    }

    private void addSetterValue(VariableElement e, String valueType, MethodSpec methodSpec) {
        methodSpec.addParameter(new ParameterSpec(valueType, e.getSimpleName().toString()).also(parameterSpec -> {
            if (isMemorySegmentSimple(valueType)) {
                final SizedSeg sizedSeg = e.getAnnotation(SizedSeg.class);
                if (sizedSeg != null) {
                    addAnnotationValue(parameterSpec, sizedSeg, SizedSeg.class, SizedSeg::value);
                } else {
                    final Sized sized = e.getAnnotation(Sized.class);
                    if (sized != null) {
                        parameterSpec.addAnnotation(new AnnotationSpec(SizedSeg.class)
                            .addArgument("value", getConstExp(sized.value() * toValueLayout(e.asType()).byteSize())));
                    }
                }
            } else {
                addAnnotationValue(parameterSpec, e.getAnnotation(Sized.class), Sized.class, Sized::value);
            }
        }));
    }

    private void addGetterAnnotation(VariableElement e, String returnType, MethodSpec methodSpec, SizedSeg sizedSeg, Sized sized) {
        if (isMemorySegmentSimple(returnType)) {
            if (sizedSeg != null) {
                addAnnotationValue(methodSpec, sizedSeg, SizedSeg.class, SizedSeg::value);
            } else {
                if (sized != null) {
                    methodSpec.addAnnotation(new AnnotationSpec(SizedSeg.class)
                        .addArgument("value", getConstExp(sized.value() * toValueLayout(e.asType()).byteSize())));
                }
            }
        } else {
            addAnnotationValue(methodSpec, sized, Sized.class, Sized::value);
        }
    }

    private static String convertParamIndex(String nameString) {
        return insertUnderline("index", nameString);
    }

    private static String convertParamSegmentAllocator(String nameString) {
        return insertUnderline("segmentAllocator", nameString);
    }

    private static String convertParamArena(String nameString) {
        return insertUnderline("arena", nameString);
    }

    private static void addIStructImpl(ClassSpec classSpec, Class<?> aClass, String name, Spec spec) {
        classSpec.addMethod(new MethodSpec(aClass.getSimpleName(), name), methodSpec -> {
            methodSpec.addAnnotation(new AnnotationSpec(Override.class));
            methodSpec.addStatement(Spec.returnStatement(spec));
        });
    }

    private static void forEach(List<VariableElement> fields, boolean allowPadding, Consumer<VariableElement> action) {
        fields.stream().filter(e -> e.getAnnotation(Skip.class) == null &&
                                    (allowPadding || e.getAnnotation(Padding.class) == null)).forEach(action);
    }

    private static boolean isMemorySegmentSimple(String name) {
        return MemorySegment.class.getSimpleName().equals(name);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Struct.class.getCanonicalName());
    }
}
