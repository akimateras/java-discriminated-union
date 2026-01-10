package com.example;

import java.io.IOException;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    public static <T> T map(String json, Class<T> clazz) throws IOException {
        ObjectMapper jackson = new ObjectMapper();
        jackson.enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        try {
            MultiDiscriminatorObjectMapper mapper = new MultiDiscriminatorObjectMapper(jackson);
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static void main(String[] args) {
    }
}
