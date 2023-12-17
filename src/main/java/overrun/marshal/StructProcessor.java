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
import overrun.marshal.internal.Processor;
import overrun.marshal.struct.Const;
import overrun.marshal.struct.IStruct;
import overrun.marshal.struct.Padding;
import overrun.marshal.struct.Struct;

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
            classSpec.addField(new VariableStatement(StructLayout.class, "_LAYOUT",
                new InvokeSpec(MemoryLayout.class, "structLayout").also(invokeSpec -> forEach(fields, true, e -> {
                    final TypeMirror eType = e.asType();
                    final Padding padding = e.getAnnotation(Padding.class);
                    if (padding != null) {
                        invokeSpec.addArgument(new InvokeSpec(MemoryLayout.class, "paddingLayout")
                            .addArgument(getConstExp(padding.value())));
                    } else if (isValueType(eType)) {
                        InvokeSpec member = new InvokeSpec(toValueLayout(eType), "withName")
                            .addArgument(getConstExp(e.getSimpleName().toString()));
                        if (isArray(eType)) {
                            final Sized sized = e.getAnnotation(Sized.class);
                            if (sized != null) {
                                member = new InvokeSpec(member, "withTargetLayout")
                                    .addArgument(new InvokeSpec(MemoryLayout.class, "sequenceLayout")
                                        .addArgument(getConstExp(sized.value()))
                                        .addArgument(toValueLayout(getArrayComponentType(eType))));
                            }
                        }
                        invokeSpec.addArgument(member);
                    } else {
                        printError("Unsupported field: " + eType + ' ' + e.getSimpleName());
                        // TODO: 2023/12/16 squid233: struct, upcall
                    }
                })))
                .setStatic(true)
                .setFinal(true), variableStatement -> {
                // document
                final StringBuilder sb = new StringBuilder(512);
                sb.append(" The layout of this struct.\n <pre>{@code\n struct ").append(simpleClassName).append(" {\n");
                forEach(fields, false, e -> {
                    final String string = e.asType().toString();
                    final String substring = string.substring(string.lastIndexOf('.') + 1);
                    final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                    sb.append("     ");
                    if (strCharset != null) {
                        sb.append('(').append(strCharset.value()).append(") ");
                    }
                    if (typeConst || e.getAnnotation(Const.class) != null) {
                        sb.append("final ");
                    }
                    if (MemorySegment.class.getSimpleName().equals(substring)) {
                        final LongSized longSized = e.getAnnotation(LongSized.class);
                        sb.append("void");
                        if (longSized != null) {
                            sb.append('[').append(getConstExp(longSized.value())).append(']');
                        } else {
                            sb.append('*');
                        }
                    } else {
                        final Sized sized = e.getAnnotation(Sized.class);
                        if (sized != null && substring.endsWith("[]")) {
                            sb.append(substring, 0, substring.length() - 1).append(getConstExp(sized.value())).append(']');
                        } else {
                            sb.append(substring);
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
                    .addArgument("_LAYOUT")));
                forEach(fields, false, e -> {
                    final var name = e.getSimpleName();
                    methodSpec.addStatement(Spec.assignStatement(Spec.accessSpec("this", name.toString()),
                        new InvokeSpec(Spec.literal("this._sequenceLayout"), "varHandle")
                            .addArgument("_SEQUENCE_ELEMENT")
                            .addArgument("_PE_" + name)));
                });
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
                        .addArgument("_LAYOUT"))
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
                        .addArgument("_LAYOUT")
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
                            new InvokeSpec("_LAYOUT", "byteSize")))
                        .addArgument("_LAYOUT"))
                    .addArgument(Spec.literal("1L"))));
            });

            // member
            forEach(fields, false, e -> {
                final TypeMirror eType = e.asType();
                final String nameString = e.getSimpleName().toString();
                if (isValueType(eType)) {
                    final LongSized longSized = e.getAnnotation(LongSized.class);
                    final Sized sized = e.getAnnotation(Sized.class);
                    final boolean canConvertToAddress = canConvertToAddress(eType);
                    final String returnType = canConvertToAddress ? MemorySegment.class.getSimpleName() : eType.toString();
                    final String capitalized = capitalize(nameString);
                    final boolean isMemorySegment = isMemorySegment(eType);
                    final boolean isArray = isArray(eType);
                    final boolean generateN = canConvertToAddress && !isMemorySegment;

                    // getter
                    final Spec castSpec = Spec.cast(returnType,
                        new InvokeSpec(Spec.accessSpec("this", nameString), "get")
                            .addArgument("this._memorySegment")
                            .addArgument("0L")
                            .addArgument("index"));
                    addGetterAt(classSpec, e, generateN ? "nget" : "get", returnType,
                        (isMemorySegment && longSized != null || isArray && sized != null) ?
                            new InvokeSpec(Spec.parentheses(castSpec), "reinterpret")
                                .addArgument(longSized != null ?
                                    Spec.literal(getConstExp(longSized.value())) :
                                    Spec.operatorSpec("*",
                                        Spec.literal(getConstExp(sized.value())),
                                        new InvokeSpec(toValueLayout(getArrayComponentType(eType)), "byteSize"))) :
                            castSpec);
                    if (generateN) {
                        Spec returnValue = new InvokeSpec("this", "nget" + capitalized + "At")
                            .addArgument("index");
                        if (isString(eType)) {
                            final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                            final InvokeSpec invokeSpec = new InvokeSpec(returnValue, "getString")
                                .addArgument("0");
                            if (strCharset != null) {
                                invokeSpec.addArgument(createCharset(file, strCharset.value()));
                            }
                            returnValue = invokeSpec;
                        } else if (isArray) {
                            final TypeMirror arrayComponentType = getArrayComponentType(eType);
                            if (sized == null) {
                                returnValue = new InvokeSpec(returnValue, "reinterpret")
                                    .addArgument(Spec.operatorSpec("*", Spec.literal("count"),
                                        new InvokeSpec(toValueLayout(arrayComponentType), "byteSize")));
                            }
                            if (isBooleanArray(eType)) {
                                returnValue = new InvokeSpec(BoolHelper.class.getCanonicalName(), "toArray")
                                    .addArgument(returnValue);
                            } else if (isStringArray(eType)) {
                                returnValue = new InvokeSpec(StrHelper.class.getCanonicalName(), "toArray")
                                    .addArgument(returnValue)
                                    .addArgument(createCharset(file, getCustomCharset(e)));
                            } else {
                                returnValue = new InvokeSpec(returnValue, "toArray")
                                    .addArgument(toValueLayout(arrayComponentType));
                            }
                        }
                        addGetterAt(classSpec, e, "get", simplify(eType.toString()), returnValue);
                    }

                    // setter
                    if (!typeConst && e.getAnnotation(Const.class) == null) {
                        addSetterAt(classSpec, e, generateN ? "nset" : "set", returnType,
                            Spec.statement(new InvokeSpec(Spec.accessSpec("this", nameString), "set")
                                .addArgument("this._memorySegment")
                                .addArgument("0L")
                                .addArgument("index")
                                .addArgument("value")));
                        if (generateN) {
                            final InvokeSpec invokeSpec = new InvokeSpec("this", "nset" + capitalized + "At");
                            if (isString(eType)) {
                                final StrCharset strCharset = e.getAnnotation(StrCharset.class);
                                final InvokeSpec alloc = new InvokeSpec("_segmentAllocator", "allocateFrom")
                                    .addArgument("value");
                                if (strCharset != null) {
                                    alloc.addArgument(createCharset(file, strCharset.value()));
                                }
                                invokeSpec.addArgument(alloc);
                            } else if (isArray) {
                            } else {
                                invokeSpec.addArgument("value");
                            }
                            invokeSpec.addArgument("index");
                            addSetterAt(classSpec, e, "set", simplify(eType.toString()), Spec.statement(invokeSpec));
                        }
                    }
                }
                // TODO: 2023/12/17 squid233: struct, upcall
            });

            // override
            addIStructImpl(classSpec, MemorySegment.class, "segment", Spec.literal("this._memorySegment"));
            addIStructImpl(classSpec, StructLayout.class, "layout", Spec.literal("_LAYOUT"));
            addIStructImpl(classSpec, long.class, "elementCount", new InvokeSpec("this._sequenceLayout", "elementCount"));
        });

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + '.' + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private void addSetterAt(ClassSpec classSpec, VariableElement e, String setType, String valueType, Spec setter) {
        final String member = e.getSimpleName().toString();
        final TypeMirror eType = e.asType();
        final boolean insertAllocator = "set".equals(setType) && (isString(eType) || isArray(eType));
        classSpec.addMethod(new MethodSpec("void", setType + capitalize(member) + "At"), methodSpec -> {
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Sets %s at the given index.

                 %s@param value the value
                 @param index the index\
                """.formatted(eDoc != null ? eDoc : "{@code " + e.getSimpleName() + '}', insertAllocator ? "@param _segmentAllocator the allocator\n " : ""));
            if (insertAllocator) {
                methodSpec.addParameter(SegmentAllocator.class, "_segmentAllocator");
            }
            methodSpec.addParameter(valueType, "value");
            methodSpec.addParameter(long.class, "index");
            methodSpec.addStatement(setter);
        });
        addSetter(classSpec, e, setType, valueType, insertAllocator);
    }

    private void addSetter(ClassSpec classSpec, VariableElement e, String setType, String valueType, boolean insertAllocator) {
        final String name = setType + capitalize(e.getSimpleName().toString());
        classSpec.addMethod(new MethodSpec("void", name), methodSpec -> {
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Sets the first %s.

                 %s@param value the value\
                """.formatted(eDoc != null ? eDoc : "{@code " + e.getSimpleName() + '}', insertAllocator ? "@param _allocator the allocator\n " : ""));
            if (insertAllocator) {
                methodSpec.addParameter(SegmentAllocator.class, "_segmentAllocator");
            }
            methodSpec.addParameter(valueType, "value");
            methodSpec.addStatement(Spec.statement(new InvokeSpec("this", name + "At")
                .also(invokeSpec -> {
                    if (insertAllocator) {
                        invokeSpec.addArgument("_segmentAllocator");
                    }
                })
                .addArgument("value")
                .addArgument("0L")));
        });
    }

    private void addGetterAt(ClassSpec classSpec, VariableElement e, String getType, String returnType, Spec returnValue) {
        final Sized sized = e.getAnnotation(Sized.class);
        final boolean insertCount = "get".equals(getType) && sized == null && isArray(e.asType());
        classSpec.addMethod(new MethodSpec(returnType, getType + capitalize(e.getSimpleName().toString()) + "At"), methodSpec -> {
            final LongSized longSized = e.getAnnotation(LongSized.class);
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Gets %s at the given index.

                 %s@param index the index
                 @return %1$s\
                """.formatted(eDoc != null ? eDoc : "{@code " + e.getSimpleName() + '}', insertCount ? "@param count the length of the array\n " : ""));
            addAnnotationValue(methodSpec, longSized, LongSized.class, LongSized::value);
            addAnnotationValue(methodSpec, sized, Sized.class, Sized::value);
            if (insertCount) {
                methodSpec.addParameter(int.class, "count");
            }
            methodSpec.addParameter(long.class, "index");
            methodSpec.addStatement(Spec.returnStatement(returnValue));
        });
        addGetter(classSpec, e, getType, returnType, insertCount);
    }

    private void addGetter(ClassSpec classSpec, VariableElement e, String getType, String returnType, boolean insertCount) {
        final String name = getType + capitalize(e.getSimpleName().toString());
        classSpec.addMethod(new MethodSpec(returnType, name), methodSpec -> {
            final LongSized longSized = e.getAnnotation(LongSized.class);
            final Sized sized = e.getAnnotation(Sized.class);
            final String eDoc = getDocument(e);
            methodSpec.setDocument("""
                 Gets the first %s.

                 %s@return %1$s\
                """.formatted(eDoc != null ? eDoc : "{@code " + e.getSimpleName() + '}', insertCount ? "@param count the length of the array\n " : ""));
            addAnnotationValue(methodSpec, longSized, LongSized.class, LongSized::value);
            addAnnotationValue(methodSpec, sized, Sized.class, Sized::value);
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

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Struct.class.getCanonicalName());
    }
}
