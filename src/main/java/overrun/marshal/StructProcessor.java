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
import overrun.marshal.internal.Util;
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
        file.addImport("java.lang.foreign.*");
        file.addImports(
            MemoryLayout.PathElement.class,
            VarHandle.class
        );
        file.addClass(simpleClassName, classSpec -> {
            final Const typeConst = type.getAnnotation(Const.class);

            classSpec.setDocument(getDocument(type));
            classSpec.setFinal(!struct.nonFinal());
            classSpec.addSuperinterface(IStruct.class.getCanonicalName());

            // layout
            classSpec.addField(new VariableStatement(StructLayout.class, "LAYOUT",
                new InvokeSpec(MemoryLayout.class, "structLayout").also(invokeSpec -> fields.forEach(e -> {
                    final TypeMirror eType = e.asType();
                    final Padding padding = e.getAnnotation(Padding.class);
                    if (padding != null) {
                        invokeSpec.addArgument(new InvokeSpec(MemoryLayout.class, "paddingLayout")
                            .addArgument(getConstExp(padding.value())));
                    } else if (Util.isValueType(eType)) {
                        invokeSpec.addArgument(new InvokeSpec(toValueLayout(eType), "withName")
                            .addArgument(getConstExp(e.getSimpleName().toString())));
                    } else {
                        printError("Unsupported field: " + eType + e.getSimpleName());
                        // TODO: 2023/12/16 squid233: struct, upcall
                    }
                })))
                .setDocument(" The layout of this struct.")
                .setStatic(true)
                .setFinal(true));
            classSpec.addField(new VariableStatement(MemoryLayout.PathElement.class, "_SEQUENCE_ELEMENT",
                new InvokeSpec(MemoryLayout.PathElement.class, "sequenceElement")));
            fields.forEach(e -> classSpec.addField(new VariableStatement(MemoryLayout.PathElement.class, "_PE_" + e.getSimpleName(),
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
            fields.forEach(e -> classSpec.addField(new VariableStatement(VarHandle.class, e.getSimpleName().toString(), null)
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
                fields.forEach(e -> {
                    final var name = e.getSimpleName();
                    methodSpec.addStatement(Spec.assignStatement(Spec.accessSpec("this", name.toString()),
                        new InvokeSpec(Spec.literal("this._sequenceLayout"), "varHandle")
                            .addArgument("_SEQUENCE_ELEMENT")
                            .addArgument("_PE_" + name)));
                });
            });

            // allocator
            classSpec.addMethod(new MethodSpec(simpleClassName, "create"), methodSpec -> {
                methodSpec.setStatic(true);
                methodSpec.addParameter(SegmentAllocator.class, "allocator");
                methodSpec.addStatement(Spec.returnStatement(new ConstructSpec(simpleClassName)
                    .addArgument(new InvokeSpec("allocator", "allocate")
                        .addArgument("LAYOUT"))
                    .addArgument(Spec.literal("1"))));
            });
            classSpec.addMethod(new MethodSpec(simpleClassName, "create"), methodSpec -> {
                methodSpec.setStatic(true);
                methodSpec.addParameter(SegmentAllocator.class, "allocator");
                methodSpec.addParameter(long.class, "count");
                methodSpec.addStatement(Spec.returnStatement(new ConstructSpec(simpleClassName)
                    .addArgument(new InvokeSpec("allocator", "allocate")
                        .addArgument("LAYOUT")
                        .addArgument("count"))
                    .addArgument(Spec.literal("count"))));
            });


            // member
            fields.forEach(e -> {
                if (e.getAnnotation(Padding.class) == null) {
                    final TypeMirror eType = e.asType();
                    final String nameString = e.getSimpleName().toString();
                    if (isValueType(eType)) {
                        final boolean canConvertToAddress = canConvertToAddress(eType);
                        final String returnType = canConvertToAddress ? MemorySegment.class.getSimpleName() : eType.toString();
                        classSpec.addMethod(new MethodSpec(
                            returnType,
                            (canConvertToAddress ? "nget" : "get") + capitalize(nameString) + "At"
                        ), methodSpec -> {
                            methodSpec.addParameter(long.class, "index");
                            methodSpec.addStatement(Spec.returnStatement(Spec.cast(
                                returnType,
                                new InvokeSpec(Spec.accessSpec("this", nameString), "get")
                                    .addArgument("_memorySegment"))));
                        });
                        if (canConvertToAddress && !isMemorySegment(eType)) {
                            classSpec.addMethod(new MethodSpec(
                                simplify(eType.toString()),
                                "get" + capitalize(nameString) + "At"
                            ), methodSpec -> {
                                methodSpec.addParameter(long.class, "index");
                                methodSpec.addStatement(Spec.returnStatement(Spec.literal("null")));
                            });
                        }
                    }
                }
            });

            // override
            addIStructImpl(classSpec, MemorySegment.class, "segment", Spec.literal("_memorySegment"));
            addIStructImpl(classSpec, StructLayout.class, "layout", Spec.literal("LAYOUT"));
            addIStructImpl(classSpec, long.class, "elementCount", new InvokeSpec("_sequenceLayout", "elementCount"));
        });

        final JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + '.' + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private static void addIStructImpl(ClassSpec classSpec, Class<?> aClass, String name, Spec spec) {
        classSpec.addMethod(new MethodSpec(aClass.getSimpleName(), name), methodSpec -> {
            methodSpec.addAnnotation(new AnnotationSpec(Override.class));
            methodSpec.addStatement(Spec.returnStatement(spec));
        });
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Struct.class.getCanonicalName());
    }
}
