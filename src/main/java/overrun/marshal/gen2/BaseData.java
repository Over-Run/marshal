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

import overrun.marshal.gen.Skip;
import overrun.marshal.gen1.*;
import overrun.marshal.gen2.struct.StructData;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static overrun.marshal.internal.Util.isAExtendsB;

/**
 * Base data
 *
 * @author squid233
 * @since 0.1.0
 */
public abstract sealed class BaseData permits StructData {
    /**
     * the processing environment
     */
    protected final ProcessingEnvironment processingEnv;
    /**
     * imports
     */
    protected final ImportData imports = new ImportData(new ArrayList<>());
    /**
     * fieldDataList
     */
    protected final List<FieldData> fieldDataList = new ArrayList<>();
    /**
     * functionDataList
     */
    protected final List<FunctionData> functionDataList = new ArrayList<>();
    /**
     * document
     */
    protected String document = null;
    /**
     * nonFinal
     */
    protected boolean nonFinal = false;
    /**
     * superclasses
     */
    protected final List<TypeData> superclasses = new ArrayList<>();
    /**
     * superinterfaces
     */
    protected final List<TypeData> superinterfaces = new ArrayList<>();

    /**
     * Construct
     *
     * @param processingEnv the processing environment
     */
    protected BaseData(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    /**
     * {@return generateClassName}
     *
     * @param name        name
     * @param typeElement typeElement
     */
    protected DeclaredTypeData generateClassName(String name, TypeElement typeElement) {
        final DeclaredTypeData declaredTypeDataOfTypeElement = (DeclaredTypeData) TypeData.detectType(processingEnv, typeElement.asType());
        final String simpleClassName;
        if (name.isBlank()) {
            final String string = declaredTypeDataOfTypeElement.name();
            if (string.startsWith("C")) {
                simpleClassName = string.substring(1);
            } else {
                processingEnv.getMessager().printError("""
                    Class name must start with C if the name is not specified. Current name: %s
                         Possible solutions:
                         1) Add C as a prefix e.g. C%1$s
                         2) Specify the name in @Struct e.g. @Struct(name = "%1$s")"""
                    .formatted(string), typeElement);
                return null;
            }
        } else {
            simpleClassName = name;
        }
        return new DeclaredTypeData(declaredTypeDataOfTypeElement.packageName(), simpleClassName);
    }

    /**
     * Generates the file
     *
     * @param declaredTypeData the class name
     * @throws IOException if an I/O error occurred
     */
    protected void generate(DeclaredTypeData declaredTypeData) throws IOException {
        final String packageName = declaredTypeData.packageName();
        final String simpleClassName = declaredTypeData.name();
        final SourceFile file = createSourceFile(packageName, simpleClassName);

        imports.imports()
            .stream()
            .filter(typeData -> !"java.lang".equals(typeData.packageName()))
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
            .createSourceFile(packageName + "." + simpleClassName);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            file.write(out);
        }
    }

    private SourceFile createSourceFile(String packageName, String simpleClassName) {
        final SourceFile file = new SourceFile(packageName);
        file.addClass(simpleClassName, classSpec -> {
            if (document != null) {
                classSpec.setDocument(document);
            }
            classSpec.setFinal(!nonFinal);
            superclasses.forEach(typeData -> classSpec.addSuperclass(imports.simplifyOrImport(typeData)));
            superinterfaces.forEach(typeData -> classSpec.addSuperinterface(imports.simplifyOrImport(typeData)));

            addFields(classSpec);
            addMethods(classSpec);
        });
        return file;
    }

    /**
     * Generates the file
     *
     * @param typeElement the type element
     * @throws IOException if an I/O error occurred
     */
    public abstract void generate(TypeElement typeElement) throws IOException;

    /**
     * addFields
     *
     * @param spec spec
     */
    protected void addFields(ClassSpec spec) {
        fieldDataList.forEach(fieldData -> {
            final TypeData type = fieldData.type();
            final TypeUse value = fieldData.value();
            spec.addField(new VariableStatement(
                imports.simplifyOrImport(type),
                fieldData.name(),
                value != null ? value.apply(imports) : null
            ).setDocument(fieldData.document())
                .setAccessModifier(fieldData.accessModifier())
                .setStatic(fieldData.staticField())
                .setFinal(fieldData.finalField()));
        });
    }

    /**
     * addMethods
     *
     * @param spec spec
     */
    protected void addMethods(ClassSpec spec) {
        functionDataList.forEach(functionData ->
            spec.addMethod(new MethodSpec(functionData.returnType().apply(imports), functionData.name()), methodSpec -> {
                methodSpec.setDocument(functionData.document());
                addAnnotationSpec(functionData.annotations(), methodSpec);
                methodSpec.setAccessModifier(functionData.accessModifier());
                methodSpec.setStatic(functionData.staticMethod());
                functionData.parameters().forEach(parameterData -> {
                    final ParameterSpec parameterSpec = new ParameterSpec(parameterData.type().apply(imports), parameterData.name());
                    addAnnotationSpec(parameterData.annotations(), parameterSpec);
                    methodSpec.addParameter(parameterSpec);
                });
                functionData.statements().forEach(typeUse ->
                    methodSpec.addStatement(typeUse.apply(imports)));
            }));
    }

    /**
     * addAnnotationSpec
     *
     * @param list              list
     * @param annotatable       annotatable
     * @param escapeAtCharacter escapeAtCharacter
     */
    protected static void addAnnotationSpec(List<AnnotationData> list, Annotatable annotatable, boolean escapeAtCharacter) {
        list.forEach(annotationData -> {
            final AnnotationSpec annotationSpec = new AnnotationSpec(
                annotationData.type(),
                escapeAtCharacter
            );
            annotationData.map().forEach(annotationSpec::addArgument);
            annotatable.addAnnotation(annotationSpec);
        });
    }

    /**
     * addAnnotationSpec
     *
     * @param list        list
     * @param annotatable annotatable
     */
    protected static void addAnnotationSpec(List<AnnotationData> list, Annotatable annotatable) {
        addAnnotationSpec(list, annotatable, false);
    }

    /**
     * {@return getDocComment}
     *
     * @param e e
     */
    protected String getDocComment(Element e) {
        return processingEnv.getElementUtils().getDocComment(e);
    }

    /**
     * {@return getConstExp}
     *
     * @param v v
     */
    protected String getConstExp(Object v) {
        return processingEnv.getElementUtils().getConstantExpression(v);
    }

    /**
     * {@return skipAnnotated}
     *
     * @param collection collection
     * @param aClass     aClass
     * @param <T>        type
     */
    protected static <T extends Element> Stream<T> skipAnnotated(Collection<T> collection, Class<? extends Annotation> aClass) {
        return collection.stream().filter(t -> t.getAnnotation(aClass) == null);
    }

    /**
     * {@return skipAnnotated}
     *
     * @param collection collection
     * @param <T>        type
     */
    protected static <T extends Element> Stream<T> skipAnnotated(Collection<T> collection) {
        return skipAnnotated(collection, Skip.class);
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

    /**
     * {@return getElementAnnotations}
     *
     * @param list list
     * @param e    e
     */
    protected List<AnnotationData> getElementAnnotations(List<Class<? extends Annotation>> list, Element e) {
        final var mirrors = e.getAnnotationMirrors();
        return list.stream()
            .filter(aClass -> e.getAnnotation(aClass) != null)
            .flatMap(aClass -> findElementAnnotations(mirrors, aClass)
                .map(mirror -> new AnnotationData(imports.simplifyOrImport(processingEnv, mirror.getAnnotationType()),
                    mapAnnotationMirrorValues(mirror.getElementValues()))))
            .toList();
    }

    private static Map<String, String> mapAnnotationMirrorValues(Map<? extends ExecutableElement, ? extends AnnotationValue> map) {
        return map.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().getSimpleName().toString(),
                entry -> entry.getValue().toString()
            ));
    }
}
