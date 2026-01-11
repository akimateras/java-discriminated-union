package jp.akimateras.jackson;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DiscriminatorTypeResolver {
    ResolvedType resolve(JsonNode node, Class<?> baseType) throws IOException {
        if (isAbstractOrInterface(baseType) && !node.isObject()) {
            throw new IOException("Expected object for abstract type " + baseType.getName());
        }
        Set<String> toRemove = new LinkedHashSet<>();
        Class<?> current = baseType;
        while (isAbstractOrInterface(current)) {
            JsonTypeInfo typeInfo = current.getAnnotation(JsonTypeInfo.class);
            if (typeInfo == null) {
                throw new IOException("Missing @JsonTypeInfo for " + current.getName());
            }
            if (typeInfo.use() != JsonTypeInfo.Id.NAME) {
                throw new IOException("Unsupported JsonTypeInfo.Id for " + current.getName() + ": " + typeInfo.use());
            }
            JsonTypeInfo.As include = typeInfo.include();
            if (include != JsonTypeInfo.As.PROPERTY && include != JsonTypeInfo.As.EXISTING_PROPERTY) {
                throw new IOException("Unsupported JsonTypeInfo.As for " + current.getName() + ": " + include);
            }
            String property = typeInfo.property();
            JsonNode typeNode = node.get(property);
            if (typeNode == null || typeNode.isNull()) {
                throw new IOException("Missing discriminator property '" + property + "' for " + current.getName());
            }
            String typeName = typeNode.asText();
            if (typeName.isEmpty()) {
                throw new IOException("Empty discriminator property '" + property + "' for " + current.getName());
            }
            JsonSubTypes subTypes = current.getAnnotation(JsonSubTypes.class);
            if (subTypes == null) {
                throw new IOException("Missing @JsonSubTypes for " + current.getName());
            }
            Class<?> resolved = resolveSubType(subTypes, typeName);
            if (resolved == null) {
                throw new IOException("Unknown subtype '" + typeName + "' for " + current.getName());
            }
            if (include == JsonTypeInfo.As.PROPERTY && !typeInfo.visible()) {
                toRemove.add(property);
            }
            current = resolved;
        }
        return new ResolvedType(current, toRemove);
    }

    boolean isAbstractOrInterface(Class<?> type) {
        if (type.isPrimitive()) {
            return false;
        }
        return type.isInterface() || Modifier.isAbstract(type.getModifiers());
    }

    JsonNode stripDiscriminators(JsonNode node, Set<String> toRemove) {
        if (toRemove.isEmpty() || !(node instanceof ObjectNode objectNode)) {
            return node;
        }
        ObjectNode copy = objectNode.deepCopy();
        for (String property : toRemove) {
            copy.remove(property);
        }
        return copy;
    }

    private static @Nullable Class<?> resolveSubType(JsonSubTypes subTypes, String name) {
        for (JsonSubTypes.Type type : subTypes.value()) {
            if (name.equals(type.name())) {
                return type.value();
            }
            for (String alias : type.names()) {
                if (name.equals(alias)) {
                    return type.value();
                }
            }
            JsonTypeName typeName = type.value().getAnnotation(JsonTypeName.class);
            if (typeName != null && name.equals(typeName.value())) {
                return type.value();
            }
        }
        return null;
    }

    record ResolvedType(Class<?> concreteType, Set<String> discriminatorsToRemove) {
    }
}
