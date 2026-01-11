package jp.akimateras.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class MultiDiscriminatorObjectMapper {
    private final ObjectMapper mapper;
    private final NodeMapper nodeMapper;

    public MultiDiscriminatorObjectMapper() {
        this(defaultObjectMapper(), true);
    }

    public MultiDiscriminatorObjectMapper(boolean defaultNonNull) {
        this(defaultObjectMapper(), defaultNonNull);
    }

    public MultiDiscriminatorObjectMapper(ObjectMapper mapper) {
        this(mapper, true);
    }

    public MultiDiscriminatorObjectMapper(ObjectMapper mapper, boolean defaultNonNull) {
        this.mapper = mapper;
        this.nodeMapper = new NodeMapper(mapper, new DiscriminatorTypeResolver(mapper), defaultNonNull);
    }

    public <T> T readValue(String json, Class<T> clazz) throws IOException {
        JsonNode node = mapper.readTree(json);
        return readValue(node, clazz);
    }

    public <T> T readValue(JsonNode node, Class<T> clazz) throws IOException {
        JavaType targetType = mapper.getTypeFactory().constructType(clazz);
        Object mapped = nodeMapper.mapNode(node, targetType);
        if (mapped == null) {
            throw new IOException("Null value for " + clazz.getName());
        }
        return clazz.cast(mapped);
    }

    public <T> T readValue(String json, TypeReference<T> typeRef) throws IOException {
        JsonNode node = mapper.readTree(json);
        return readValue(node, typeRef);
    }

    public <T> T readValue(JsonNode node, TypeReference<T> typeRef) throws IOException {
        JavaType targetType = mapper.getTypeFactory().constructType(typeRef);
        Object mapped = readValue(node, targetType);
        @SuppressWarnings("unchecked")
        T casted = (T) mapped;
        return casted;
    }

    public Object readValue(String json, JavaType type) throws IOException {
        JsonNode node = mapper.readTree(json);
        return readValue(node, type);
    }

    public Object readValue(JsonNode node, JavaType type) throws IOException {
        Object mapped = nodeMapper.mapNode(node, type);
        if (mapped == null) {
            throw new IOException("Null value for " + type);
        }
        return mapped;
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        return mapper;
    }

}
