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

package overrun.marshal.gen2.struct;

import overrun.marshal.gen.AccessModifier;
import overrun.marshal.gen.Sized;
import overrun.marshal.gen.SizedSeg;
import overrun.marshal.gen.StrCharset;
import overrun.marshal.gen.struct.Const;
import overrun.marshal.gen.struct.Padding;
import overrun.marshal.gen.struct.Struct;
import overrun.marshal.gen.struct.StructRef;
import overrun.marshal.gen1.ConstructSpec;
import overrun.marshal.gen1.InvokeSpec;
import overrun.marshal.gen1.Spec;
import overrun.marshal.gen1.VariableStatement;
import overrun.marshal.gen2.*;
import overrun.marshal.struct.IStruct;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static overrun.marshal.internal.Util.getArrayComponentType;
import static overrun.marshal.internal.Util.tryInsertUnderline;

/**
 * Holds struct fields and methods
 *
 * @author squid233
 * @since 0.1.0
 */
public final class StructData extends BaseData {
    private static final String LAYOUT_NAME = "LAYOUT";
    private static final String PARAMETER_ALLOCATOR_NAME = "allocator";
    private static final String PARAMETER_COUNT_NAME = "count";
    private static final String PARAMETER_INDEX_NAME = "index";
    private static final String PARAMETER_SEGMENT_NAME = "segment";
    private static final List<Class<? extends Annotation>> MEMBER_ANNOTATION = List.of(
        SizedSeg.class,
        Sized.class,
        StrCharset.class
    );
    private final Predicate<String> insertPredicate = s -> fieldDataList.stream().anyMatch(fieldData -> s.equals(fieldData.name()));
    private String peSequenceElementName;
    private String segmentName;
    private String layoutName;

    /**
     * Construct
     *
     * @param processingEnv the processing environment
     */
    public StructData(ProcessingEnvironment processingEnv) {
        super(processingEnv);
    }

    private void processStructType(TypeElement typeElement, String simpleClassName) {
        final var enclosedElements = typeElement.getEnclosedElements();

        final var fields = skipAnnotated(ElementFilter.fieldsIn(enclosedElements)).toList();
        final var ignorePadding = skipAnnotated(fields, Padding.class).toList();
        final Map<String, String> memberPathElementNameMap = LinkedHashMap.newLinkedHashMap(ignorePadding.size());
        final Map<String, String> memberVarHandleNameMap = LinkedHashMap.newLinkedHashMap(ignorePadding.size());
        final Map<String, MemberData> memberDataMap = LinkedHashMap.newLinkedHashMap(ignorePadding.size());

        final Struct struct = typeElement.getAnnotation(Struct.class);
        final Const structConst = typeElement.getAnnotation(Const.class);

        document = getDocComment(typeElement);
        nonFinal = struct.nonFinal();
        superinterfaces.add(TypeData.fromClass(IStruct.class));

        addLayout(simpleClassName, fields, ignorePadding, structConst);
        addPathElements(ignorePadding, memberPathElementNameMap);
        addVarHandles(ignorePadding, memberVarHandleNameMap);
        addStructInfo();

        memberPathElementNameMap.forEach((name, pathElementName) -> memberDataMap.put(name,
            new MemberData(pathElementName, memberVarHandleNameMap.get(name))));

        addConstructors(simpleClassName, memberDataMap);
        addAllocators(simpleClassName);
        addSlices(simpleClassName);
        addStructImpl();
    }

    private void addLayout(String simpleClassName, List<VariableElement> fields, List<VariableElement> ignorePadding, Const structConst) {
        // layout document
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("""
                 The layout of this struct.
                 <pre><code>
                 struct \
                """)
            .append(simpleClassName)
            .append(" {\n");
        ignorePadding.forEach(element -> {
            final String typeNameString;
            final StructRef structRef = element.getAnnotation(StructRef.class);
            if (structRef != null) {
                typeNameString = "{@link " + structRef.value() + "}";
            } else {
                final StringBuilder typeName = new StringBuilder(8);
                appendMemberType(typeName, TypeData.detectType(processingEnv, element.asType()));
                typeNameString = typeName.toString();
            }
            final VariableStatement variableStatement = new VariableStatement(
                typeNameString,
                element.getSimpleName().toString(),
                null
            ).setAccessModifier(AccessModifier.PACKAGE_PRIVATE)
                .setFinal(structConst != null || element.getAnnotation(Const.class) != null);
            addAnnotationSpec(getElementAnnotations(MEMBER_ANNOTATION, element), variableStatement, true);
            variableStatement.append(sb, 5);
        });
        sb.append(" }</code></pre>\n");
        // layout field
        fieldDataList.add(new FieldData(
            sb.toString(),
            List.of(),
            AccessModifier.PUBLIC,
            true,
            true,
            TypeData.fromClass(StructLayout.class),
            LAYOUT_NAME,
            importData -> new InvokeSpec(importData.simplifyOrImport(MemoryLayout.class), "structLayout")
                .also(invokeSpec -> fields.forEach(element -> {
                    final Padding padding = element.getAnnotation(Padding.class);
                    if (padding != null) {
                        invokeSpec.addArgument(new InvokeSpec(importData.simplifyOrImport(MemoryLayout.class), "paddingLayout")
                            .addArgument(getConstExp(padding.value())));
                    } else {
                        final SizedSeg sizedSeg = element.getAnnotation(SizedSeg.class);
                        final Sized sized = element.getAnnotation(Sized.class);
                        final TypeMirror type = element.asType();

                        final InvokeSpec valueLayout = new InvokeSpec(TypeUse.valueLayout(processingEnv, type)
                            .orElseThrow()
                            .apply(importData), "withName"
                        ).addArgument(getConstExp(element.getSimpleName().toString()));
                        final Spec finalSpec;
                        if (sizedSeg != null) {
                            finalSpec = new InvokeSpec(valueLayout, "withTargetLayout")
                                .addArgument(new InvokeSpec(importData.simplifyOrImport(MemoryLayout.class), "sequenceLayout")
                                    .addArgument(getConstExp(sizedSeg.value()))
                                    .addArgument(TypeUse.valueLayout(byte.class).orElseThrow().apply(importData)));
                        } else if (sized != null) {
                            finalSpec = new InvokeSpec(valueLayout, "withTargetLayout")
                                .addArgument(new InvokeSpec(importData.simplifyOrImport(MemoryLayout.class), "sequenceLayout")
                                    .addArgument(getConstExp(sized.value()))
                                    .addArgument(TypeUse.valueLayout(processingEnv, getArrayComponentType(type)).orElseThrow().apply(importData)));
                        } else {
                            finalSpec = valueLayout;
                        }
                        invokeSpec.addArgument(finalSpec);
                    }
                }))
        ));
    }

    private void addPathElements(List<VariableElement> ignorePadding, Map<String, String> memberPathElementNameMap) {
        peSequenceElementName = tryInsertUnderline("PE_SEQUENCE_ELEMENT", insertPredicate);
        fieldDataList.add(new FieldData(
            null,
            List.of(),
            AccessModifier.PRIVATE,
            true,
            true,
            TypeData.fromClass(MemoryLayout.PathElement.class),
            peSequenceElementName,
            importData -> new InvokeSpec(importData.simplifyOrImport(MemoryLayout.PathElement.class), "sequenceElement")
        ));
        ignorePadding.stream()
            .map(element -> element.getSimpleName().toString())
            .forEach(s -> {
                final String name = tryInsertUnderline("PE_" + s, insertPredicate);
                fieldDataList.add(new FieldData(
                    null,
                    List.of(),
                    AccessModifier.PRIVATE,
                    true,
                    true,
                    TypeData.fromClass(MemoryLayout.PathElement.class),
                    name,
                    importData -> new InvokeSpec(importData.simplifyOrImport(MemoryLayout.PathElement.class), "groupElement")
                        .addArgument(getConstExp(s))
                ));
                memberPathElementNameMap.put(s, name);
            });
    }

    private void addVarHandles(List<VariableElement> ignorePadding, Map<String, String> memberVarHandleNameMap) {
        ignorePadding.stream()
            .map(element -> element.getSimpleName().toString())
            .forEach(s -> {
                final String name = tryInsertUnderline(s, insertPredicate);
                fieldDataList.add(new FieldData(
                    null,
                    List.of(),
                    AccessModifier.PRIVATE,
                    false,
                    true,
                    TypeData.fromClass(VarHandle.class),
                    name,
                    null
                ));
                memberVarHandleNameMap.put(s, name);
            });
    }

    private void addStructInfo() {
        segmentName = tryInsertUnderline("segment", insertPredicate);
        layoutName = tryInsertUnderline("layout", insertPredicate);
        fieldDataList.add(new FieldData(
            null,
            List.of(),
            AccessModifier.PRIVATE,
            false,
            true,
            TypeData.fromClass(MemorySegment.class),
            segmentName,
            null
        ));
        fieldDataList.add(new FieldData(
            null,
            List.of(),
            AccessModifier.PRIVATE,
            false,
            true,
            TypeData.fromClass(SequenceLayout.class),
            layoutName,
            null
        ));
    }

    private void addConstructors(String simpleClassName, Map<String, MemberData> memberDataMap) {
        final List<TypeUse> statements = new ArrayList<>(memberDataMap.size() + 2);

        statements.add(_ -> Spec.assignStatement(Spec.accessSpec("this", segmentName), Spec.literal(PARAMETER_SEGMENT_NAME)));
        statements.add(importData -> Spec.assignStatement(Spec.accessSpec("this", layoutName),
            new InvokeSpec(importData.simplifyOrImport(MemoryLayout.class), "sequenceLayout")
                .addArgument(PARAMETER_COUNT_NAME)
                .addArgument(LAYOUT_NAME)));
        memberDataMap.forEach((_, memberData) -> statements.add(_ ->
            Spec.assignStatement(Spec.accessSpec("this", memberData.varHandleName()),
                new InvokeSpec(Spec.accessSpec("this", layoutName), "varHandle")
                    .addArgument(peSequenceElementName)
                    .addArgument(memberData.pathElementName()))));

        functionDataList.add(new FunctionData(
            """
                 Creates {@code %s} with the given segment and element count.

                 @param %s the segment
                 @param %s the element count\
                """.formatted(simpleClassName, PARAMETER_SEGMENT_NAME, PARAMETER_COUNT_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            false,
            _ -> null,
            simpleClassName,
            List.of(
                new ParameterData(List.of(), TypeUse.of(MemorySegment.class), PARAMETER_SEGMENT_NAME),
                new ParameterData(List.of(), TypeUse.literal(long.class), PARAMETER_COUNT_NAME)
            ),
            statements
        ));

        functionDataList.add(new FunctionData(
            """
                 Creates {@code %s} with the given segment. The count is auto-inferred.

                 @param %s the segment\
                """.formatted(simpleClassName, PARAMETER_SEGMENT_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            false,
            _ -> null,
            simpleClassName,
            List.of(new ParameterData(List.of(), TypeUse.of(MemorySegment.class), PARAMETER_SEGMENT_NAME)),
            List.of(importData -> Spec.statement(new InvokeSpec((Spec) null, "this")
                .addArgument(PARAMETER_SEGMENT_NAME)
                .addArgument(new InvokeSpec(importData.simplifyOrImport(IStruct.class), "inferCount")
                    .addArgument(PARAMETER_SEGMENT_NAME)
                    .addArgument(LAYOUT_NAME))))
        ));
    }

    private void addAllocators(String simpleClassName) {
        functionDataList.add(new FunctionData(
            """
                 Allocates {@code %s}.

                 @param %s the allocator
                 @return the allocated {@code %1$s}\
                """.formatted(simpleClassName, PARAMETER_ALLOCATOR_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            true,
            TypeUse.literal(simpleClassName),
            "create",
            List.of(new ParameterData(List.of(), TypeUse.of(SegmentAllocator.class), PARAMETER_ALLOCATOR_NAME)),
            List.of(_ -> Spec.returnStatement(new ConstructSpec(simpleClassName)
                .addArgument(new InvokeSpec(PARAMETER_ALLOCATOR_NAME, "allocate")
                    .addArgument(LAYOUT_NAME))
                .addArgument(Spec.literal(getConstExp(1L)))))
        ));
        functionDataList.add(new FunctionData(
            """
                 Allocates {@code %s} with the given count..

                 @param %s the allocator
                 @param %s the element count
                 @return the allocated {@code %1$s}\
                """.formatted(simpleClassName, PARAMETER_ALLOCATOR_NAME, PARAMETER_COUNT_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            true,
            TypeUse.literal(simpleClassName),
            "create",
            List.of(
                new ParameterData(List.of(), TypeUse.of(SegmentAllocator.class), PARAMETER_ALLOCATOR_NAME),
                new ParameterData(List.of(), TypeUse.literal(long.class), PARAMETER_COUNT_NAME)
            ),
            List.of(_ -> Spec.returnStatement(new ConstructSpec(simpleClassName)
                .addArgument(new InvokeSpec(PARAMETER_ALLOCATOR_NAME, "allocate")
                    .addArgument(LAYOUT_NAME)
                    .addArgument(PARAMETER_COUNT_NAME))
                .addArgument(Spec.literal(PARAMETER_COUNT_NAME))))
        ));
    }

    private void addSlices(String simpleClassName) {
        functionDataList.add(new FunctionData(
            """
                 Returns a slice of this struct, at the given index.

                 @param %s The new struct base offset
                 @param %s The new struct size
                 @return a slice of this struct\
                """
                .formatted(PARAMETER_INDEX_NAME, PARAMETER_COUNT_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            false,
            TypeUse.literal(simpleClassName),
            "get",
            List.of(
                new ParameterData(List.of(), TypeUse.literal(long.class), PARAMETER_INDEX_NAME),
                new ParameterData(List.of(), TypeUse.literal(long.class), PARAMETER_COUNT_NAME)
            ),
            List.of(_ -> Spec.returnStatement(new ConstructSpec(simpleClassName)
                .addArgument(new InvokeSpec(segmentName, "asSlice")
                    .addArgument(Spec.operatorSpec("*", Spec.literal(PARAMETER_INDEX_NAME), new InvokeSpec(LAYOUT_NAME, "byteSize")))
                    .addArgument(Spec.operatorSpec("*", Spec.literal(PARAMETER_COUNT_NAME), new InvokeSpec(LAYOUT_NAME, "byteSize")))
                    .addArgument(new InvokeSpec(LAYOUT_NAME, "byteAlignment")))
                .addArgument(Spec.literal(PARAMETER_COUNT_NAME))))
        ));
        functionDataList.add(new FunctionData(
            """
                 Returns a slice of this struct.

                 @param %s The new struct base offset
                 @return a slice of this struct\
                """
                .formatted(PARAMETER_INDEX_NAME),
            List.of(),
            AccessModifier.PUBLIC,
            false,
            TypeUse.literal(simpleClassName),
            "get",
            List.of(
                new ParameterData(List.of(), TypeUse.literal(long.class), PARAMETER_INDEX_NAME)
            ),
            List.of(_ -> Spec.returnStatement(new InvokeSpec((Spec) null, "get")
                .addArgument(PARAMETER_INDEX_NAME)
                .addArgument(getConstExp(1L))))
        ));
    }

    private void addStructImpl() {
        addStructImpl(TypeUse.of(MemorySegment.class), "segment", Spec.literal(segmentName));
        addStructImpl(TypeUse.of(StructLayout.class), "layout", Spec.literal(LAYOUT_NAME));
        addStructImpl(TypeUse.literal(long.class), "elementCount", new InvokeSpec(layoutName, "elementCount"));
    }

    private void addStructImpl(TypeUse returnType, String name, Spec returnValue) {
        functionDataList.add(new FunctionData(
            null,
            List.of(new AnnotationData(imports.simplifyOrImport(Override.class), Map.of())),
            AccessModifier.PUBLIC,
            false,
            returnType,
            name,
            List.of(),
            List.of(_ -> Spec.returnStatement(returnValue))
        ));
    }

    /**
     * Generates the file
     *
     * @param typeElement the type element
     * @throws IOException if an I/O error occurred
     */
    @Override
    public void generate(TypeElement typeElement) throws IOException {
        final Struct struct = typeElement.getAnnotation(Struct.class);
        final DeclaredTypeData generatedClassName = generateClassName(struct.name(), typeElement);

        processStructType(typeElement, generatedClassName.name());

        generate(generatedClassName);
    }

    private void appendMemberType(StringBuilder sb, TypeData typeData) {
        switch (typeData) {
            case ArrayTypeData arrayTypeData -> {
                appendMemberType(sb, arrayTypeData.componentType());
                sb.append("[]");
            }
            case DeclaredTypeData _ -> sb.append("{@link ").append(imports.simplifyOrImport(typeData)).append("}");
            default -> sb.append(imports.simplifyOrImport(typeData));
        }
    }
}
