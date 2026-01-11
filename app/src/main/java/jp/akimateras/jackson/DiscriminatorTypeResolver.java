package jp.akimateras.jackson;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DiscriminatorTypeResolver {
    private final ObjectMapper mapper;

    DiscriminatorTypeResolver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    boolean needsTypeResolution(Class<?> type) {
        return isAbstractOrInterface(type) || hasTypeInfo(type);
    }

    boolean hasTypeInfo(Class<?> type) {
        return type.getAnnotation(JsonTypeInfo.class) != null;
    }

    ResolvedType resolve(JsonNode node, Class<?> baseType) throws IOException {
        if ((isAbstractOrInterface(baseType) || hasTypeInfo(baseType)) && !node.isObject()) {
            throw new IOException("Expected object for type " + baseType.getName());
        }
        Set<String> toRemove = new LinkedHashSet<>();
        Class<?> current = baseType;
        while (needsTypeResolution(current)) {
            JsonTypeInfo typeInfo = current.getAnnotation(JsonTypeInfo.class);
            if (typeInfo == null) {
                if (isAbstractOrInterface(current)) {
                    throw new IOException("Missing @JsonTypeInfo for " + current.getName());
                }
                break;
            }
            if (typeInfo.use() != JsonTypeInfo.Id.NAME && typeInfo.use() != JsonTypeInfo.Id.SIMPLE_NAME) {
                throw new IOException("Unsupported JsonTypeInfo.Id for " + current.getName() + ": " + typeInfo.use());
            }
            JsonTypeInfo.As include = typeInfo.include();
            if (include != JsonTypeInfo.As.PROPERTY && include != JsonTypeInfo.As.EXISTING_PROPERTY) {
                throw new IOException("Unsupported JsonTypeInfo.As for " + current.getName() + ": " + include);
            }
            String property = resolveTypeProperty(typeInfo);
            JsonNode typeNode = node.get(property);
            Class<?> resolved;
            if (typeNode == null || typeNode.isNull()) {
                resolved = resolveDefaultImpl(typeInfo);
                if (resolved == null) {
                    throw new IOException("Missing discriminator property '" + property + "' for " + current.getName());
                }
            } else {
                String typeName = typeNode.asText();
                if (typeName.isEmpty()) {
                    resolved = resolveDefaultImpl(typeInfo);
                    if (resolved == null) {
                        throw new IOException("Empty discriminator property '" + property + "' for " + current.getName());
                    }
                } else {
                    resolved = resolveSubType(current, typeName);
                    if (resolved == null) {
                        resolved = resolveDefaultImpl(typeInfo);
                        if (resolved == null) {
                            throw new IOException("Unknown subtype '" + typeName + "' for " + current.getName());
                        }
                    }
                }
            }
            if (include == JsonTypeInfo.As.PROPERTY && !typeInfo.visible()) {
                toRemove.add(property);
            }
            if (resolved == current) {
                break;
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

    private String resolveTypeProperty(JsonTypeInfo typeInfo) throws IOException {
        String property = typeInfo.property();
        if (property == null || property.isEmpty()) {
            property = typeInfo.use().getDefaultPropertyName();
        }
        if (property == null || property.isEmpty()) {
            throw new IOException("Missing discriminator property name for JsonTypeInfo");
        }
        return property;
    }

    private @Nullable Class<?> resolveDefaultImpl(JsonTypeInfo typeInfo) {
        Class<?> defaultImpl = typeInfo.defaultImpl();
        if (defaultImpl == null) {
            return null;
        }
        if (defaultImpl == Void.class) {
            return null;
        }
        if (defaultImpl.isAnnotation()) {
            return null;
        }
        if ("com.fasterxml.jackson.annotation.JsonTypeInfo$None".equals(defaultImpl.getName())) {
            return null;
        }
        return defaultImpl;
    }

    private @Nullable Class<?> resolveSubType(Class<?> baseType, String name) {
        Map<String, Class<?>> candidates = new LinkedHashMap<>();
        collectAnnotatedSubTypes(baseType, candidates);
        collectRegisteredSubTypes(baseType, candidates);
        return candidates.get(name);
    }

    private void collectAnnotatedSubTypes(Class<?> baseType, Map<String, Class<?>> candidates) {
        JsonSubTypes subTypes = baseType.getAnnotation(JsonSubTypes.class);
        if (subTypes == null) {
            return;
        }
        for (JsonSubTypes.Type type : subTypes.value()) {
            Class<?> subtype = type.value();
            String explicitName = type.name();
            if (!explicitName.isEmpty()) {
                addCandidate(candidates, explicitName, subtype);
            }
            for (String alias : type.names()) {
                if (!alias.isEmpty()) {
                    addCandidate(candidates, alias, subtype);
                }
            }
            String annotatedName = findTypeName(subtype);
            if (annotatedName != null) {
                addCandidate(candidates, annotatedName, subtype);
            }
            if (explicitName.isEmpty() && annotatedName == null) {
                addCandidate(candidates, defaultTypeId(subtype), subtype);
            }
        }
    }

    private void collectRegisteredSubTypes(Class<?> baseType, Map<String, Class<?>> candidates) {
        MapperConfig<?> config = mapper.getDeserializationConfig();
        BeanDescription description = config.introspectClassAnnotations(baseType);
        AnnotatedClass annotatedBase = description.getClassInfo();
        Collection<NamedType> registered = mapper.getSubtypeResolver()
                .collectAndResolveSubtypesByClass(config, annotatedBase);
        for (NamedType namedType : registered) {
            Class<?> subtype = namedType.getType();
            if (subtype == null) {
                continue;
            }
            String name = namedType.hasName() ? namedType.getName() : null;
            if (name == null || name.isEmpty()) {
                name = findTypeName(subtype);
            }
            if (name == null || name.isEmpty()) {
                name = defaultTypeId(subtype);
            }
            addCandidate(candidates, name, subtype);
        }
    }

    private void addCandidate(Map<String, Class<?>> candidates, String name, Class<?> subtype) {
        if (name == null || name.isEmpty()) {
            return;
        }
        candidates.putIfAbsent(name, subtype);
    }

    private @Nullable String findTypeName(Class<?> subtype) {
        MapperConfig<?> config = mapper.getDeserializationConfig();
        if (!config.isAnnotationProcessingEnabled()) {
            return null;
        }
        BeanDescription description = config.introspectClassAnnotations(subtype);
        String name = config.getAnnotationIntrospector().findTypeName(description.getClassInfo());
        if (name == null || name.isEmpty()) {
            return null;
        }
        return name;
    }

    private static String defaultTypeId(Class<?> type) {
        String name = type.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        return name.substring(dot + 1);
    }

    record ResolvedType(Class<?> concreteType, Set<String> discriminatorsToRemove) {
    }
}
