package com.example;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class MultiDiscriminatorObjectMapper {
    private final ObjectMapper mapper;

    MultiDiscriminatorObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    <T> T readValue(String json, Class<T> clazz) throws IOException {
        JsonNode node = mapper.readTree(json);
        return readValue(node, clazz);
    }

    <T> T readValue(JsonNode node, Class<T> clazz) throws IOException {
        JavaType targetType = mapper.getTypeFactory().constructType(clazz);
        Object mapped = mapNode(node, targetType);
        if (mapped == null) {
            throw new IOException("Null value for " + clazz.getName());
        }
        return clazz.cast(mapped);
    }

    private @Nullable Object mapNode(@Nullable JsonNode node, JavaType targetType) throws IOException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (targetType.getRawClass().isPrimitive()) {
                throw new IOException("Missing value for primitive " + targetType);
            }
            return null;
        }
        Class<?> raw = targetType.getRawClass();
        if (JsonNode.class.isAssignableFrom(raw)) {
            return node;
        }
        if (targetType.isCollectionLikeType()) {
            return mapCollection(node, targetType);
        }
        if (isAbstractOrInterface(raw)) {
            Class<?> resolved = resolveConcreteType(node, raw);
            return mapNode(node, mapper.getTypeFactory().constructType(resolved));
        }
        if (raw.isRecord()) {
            return mapRecord(node, raw);
        }
        return mapper.treeToValue(node, raw);
    }

    private static boolean isAbstractOrInterface(Class<?> type) {
        if (type.isPrimitive()) {
            return false;
        }
        return type.isInterface() || Modifier.isAbstract(type.getModifiers());
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
        }
        return null;
    }

    private Object mapCollection(JsonNode node, JavaType targetType) throws IOException {
        if (!node.isArray()) {
            throw new IOException("Expected array for " + targetType);
        }
        JavaType contentType = targetType.getContentType();
        if (contentType == null) {
            contentType = mapper.getTypeFactory().constructType(Object.class);
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(mapNode(element, contentType));
        }
        Class<?> raw = targetType.getRawClass();
        if (List.class.isAssignableFrom(raw) || Collection.class.equals(raw)) {
            return values;
        }
        if (Set.class.isAssignableFrom(raw)) {
            return new LinkedHashSet<>(values);
        }
        return mapper.convertValue(values, raw);
    }

    private <T> T mapRecord(JsonNode node, Class<T> recordType) throws IOException {
        if (!node.isObject()) {
            throw new IOException("Expected object for record " + recordType.getName());
        }
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] argTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            argTypes[i] = component.getType();
            JsonNode valueNode = node.get(component.getName());
            JavaType componentType = mapper.getTypeFactory().constructType(component.getGenericType());
            Object value = mapNode(valueNode, componentType);
            if (value == null && component.getType().isPrimitive()) {
                throw new IOException("Missing value for primitive record component " + component.getName());
            }
            args[i] = value;
        }
        try {
            Constructor<T> constructor = recordType.getDeclaredConstructor(argTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new IOException("Unable to construct record " + recordType.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to construct record " + recordType.getName(), e.getCause());
        }
    }

    private Class<?> resolveConcreteType(JsonNode node, Class<?> baseType) throws IOException {
        if (isAbstractOrInterface(baseType) && !node.isObject()) {
            throw new IOException("Expected object for abstract type " + baseType.getName());
        }
        Class<?> current = baseType;
        while (isAbstractOrInterface(current)) {
            JsonTypeInfo typeInfo = current.getAnnotation(JsonTypeInfo.class);
            if (typeInfo == null) {
                throw new IOException("Missing @JsonTypeInfo for abstract type " + current.getName());
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
            current = resolved;
        }
        return current;
    }
}
