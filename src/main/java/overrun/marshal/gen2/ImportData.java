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

import java.util.List;

/**
 * Holds imports
 *
 * @param imports the imports
 * @author squid233
 * @since 0.1.0
 */
public record ImportData(List<DeclaredTypeData> imports) {
    private boolean isImported(TypeData typeData) {
        return typeData instanceof DeclaredTypeData declaredTypeData &&
               imports.stream()
                   .map(DeclaredTypeData::name)
                   .anyMatch(name -> declaredTypeData.name().equals(name));
    }

    private boolean addImport(TypeData typeData) {
        return switch (typeData) {
            case ArrayTypeData arrayTypeData -> addImport(arrayTypeData.componentType());
            case DeclaredTypeData declaredTypeData when !isImported(typeData) -> imports().add(declaredTypeData);
            default -> false;
        };
    }

    /**
     * Simplifies type name or import
     *
     * @param typeData the type data
     * @return the name
     */
    public String simplifyOrImport(TypeData typeData) {
        return switch (typeData) {
            case ArrayTypeData arrayTypeData -> simplifyOrImport(arrayTypeData) + "[]";
            case DeclaredTypeData declaredTypeData when (isImported(typeData) || addImport(typeData)) ->
                declaredTypeData.name();
            default -> typeData.toString();
        };
    }

    /**
     * Simplifies type name or import
     *
     * @param aClass the class
     * @return the name
     */
    public String simplifyOrImport(Class<?> aClass) {
        return simplifyOrImport(TypeData.fromClass(aClass));
    }
}
