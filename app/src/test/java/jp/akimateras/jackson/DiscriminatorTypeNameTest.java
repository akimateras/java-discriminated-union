package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

class DiscriminatorTypeNameTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testJsonTypeNameSubtypeResolution() throws Exception {
        String json = """
                {
                    "type": "cat",
                    "name": "mike"
                }
                """;
        Animal actual = MAPPER.readValue(json, Animal.class);
        assertEquals(new Cat("mike"), actual);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Cat.class)
    })
    sealed interface Animal permits Cat {
    }

    @JsonTypeName("cat")
    record Cat(String name) implements Animal {
    }
}
