package com.erg.ergspringboot.platform.performance;

import graphql.language.FieldDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conservative SDL compatibility rules for pull-request contract checks. */
final class GraphQlSchemaCompatibility {

    private GraphQlSchemaCompatibility() {}

    static List<String> breakingChanges(String baseSdl, String revisionSdl) {
        TypeDefinitionRegistry base = new SchemaParser().parse(baseSdl);
        TypeDefinitionRegistry revision = new SchemaParser().parse(revisionSdl);
        List<String> changes = new ArrayList<>();

        base.types().forEach((name, baseType) -> {
            TypeDefinition revisionType = revision.types().get(name);
            if (revisionType == null) {
                changes.add(name + ": type removed");
                return;
            }
            if (!baseType.getClass().equals(revisionType.getClass())) {
                changes.add(name + ": type kind changed");
                return;
            }
            if (baseType instanceof ObjectTypeDefinition baseObject
                    && revisionType instanceof ObjectTypeDefinition revisionObject) {
                compareObject(baseObject, revisionObject, changes);
            } else if (baseType instanceof InputObjectTypeDefinition baseInput
                    && revisionType instanceof InputObjectTypeDefinition revisionInput) {
                compareInput(baseInput, revisionInput, changes);
            } else if (baseType instanceof EnumTypeDefinition baseEnum
                    && revisionType instanceof EnumTypeDefinition revisionEnum) {
                compareEnum(baseEnum, revisionEnum, changes);
            }
        });
        return changes;
    }

    private static void compareEnum(
            EnumTypeDefinition base, EnumTypeDefinition revision, List<String> changes) {
        var revisionValues = revision.getEnumValueDefinitions().stream()
                .map(value -> value.getName())
                .collect(java.util.stream.Collectors.toSet());
        base.getEnumValueDefinitions().stream()
                .map(value -> value.getName())
                .filter(value -> !revisionValues.contains(value))
                .forEach(value -> changes.add(base.getName() + "." + value + ": enum value removed"));
    }

    private static void compareObject(
            ObjectTypeDefinition base, ObjectTypeDefinition revision, List<String> changes) {
        Map<String, FieldDefinition> baseFields = byName(base.getFieldDefinitions());
        Map<String, FieldDefinition> revisionFields = byName(revision.getFieldDefinitions());
        baseFields.forEach((name, baseField) -> {
            FieldDefinition revisionField = revisionFields.get(name);
            if (revisionField == null) {
                changes.add(base.getName() + "." + name + ": field removed");
                return;
            }
            if (!baseField.getType().toString().equals(revisionField.getType().toString())) {
                changes.add(base.getName() + "." + name + ": return type changed");
            }
            compareArguments(base.getName() + "." + name, baseField.getInputValueDefinitions(),
                    revisionField.getInputValueDefinitions(), changes);
        });
    }

    private static void compareInput(
            InputObjectTypeDefinition base, InputObjectTypeDefinition revision, List<String> changes) {
        Map<String, InputValueDefinition> baseFields = byName(base.getInputValueDefinitions());
        Map<String, InputValueDefinition> revisionFields = byName(revision.getInputValueDefinitions());
        baseFields.forEach((name, baseField) -> {
            InputValueDefinition revisionField = revisionFields.get(name);
            if (revisionField == null) {
                changes.add(base.getName() + "." + name + ": input field removed");
            } else if (!baseField.getType().toString().equals(revisionField.getType().toString())) {
                String suffix = !isNonNull(baseField) && isNonNull(revisionField)
                        ? "input field became non-null" : "input field type changed";
                changes.add(base.getName() + "." + name + ": " + suffix);
            }
        });
    }

    private static void compareArguments(
            String coordinate,
            List<InputValueDefinition> base,
            List<InputValueDefinition> revision,
            List<String> changes) {
        Map<String, InputValueDefinition> baseArgs = byName(base);
        Map<String, InputValueDefinition> revisionArgs = byName(revision);
        baseArgs.forEach((name, baseArg) -> {
            InputValueDefinition revisionArg = revisionArgs.get(name);
            if (revisionArg == null) {
                changes.add(coordinate + "(" + name + "): argument removed");
            } else if (!baseArg.getType().toString().equals(revisionArg.getType().toString())) {
                changes.add(coordinate + "(" + name + "): argument type changed");
            }
        });
        revisionArgs.forEach((name, revisionArg) -> {
            if (!baseArgs.containsKey(name) && isNonNull(revisionArg) && revisionArg.getDefaultValue() == null) {
                changes.add(coordinate + "(" + name + "): required argument added");
            }
        });
    }

    private static boolean isNonNull(InputValueDefinition value) {
        return value.getType() instanceof NonNullType;
    }

    private static <T extends graphql.language.NamedNode<T>> Map<String, T> byName(List<T> values) {
        Map<String, T> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value.getName(), value));
        return result;
    }
}
