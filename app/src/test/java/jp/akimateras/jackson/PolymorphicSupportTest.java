package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

class PolymorphicSupportTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testConcreteBaseTypeInfo() throws Exception {
        String json = """
                {
                    "kind": "dog",
                    "name": "poco"
                }
                """;
        ConcreteAnimal actual = MAPPER.readValue(json, ConcreteAnimal.class);
        assertEquals(ConcreteDog.class, actual.getClass());
        assertEquals("poco", ((ConcreteDog) actual).name);
    }

    @Test
    void testDefaultImplUsedWhenMissingType() throws Exception {
        String json = """
                {
                    "value": "fallback"
                }
                """;
        DefaultBase actual = MAPPER.readValue(json, DefaultBase.class);
        assertEquals(new DefaultItem("fallback"), actual);
    }

    @Test
    void testDefaultImplUsedWhenUnknownType() throws Exception {
        String json = """
                {
                    "type": "unknown",
                    "value": "fallback"
                }
                """;
        DefaultBase actual = MAPPER.readValue(json, DefaultBase.class);
        assertEquals(new DefaultItem("fallback"), actual);
    }

    @Test
    void testRegisteredSubTypeResolution() throws Exception {
        ObjectMapper jackson = new ObjectMapper();
        jackson.registerSubtypes(new NamedType(RegisteredItem.class, "registered"));
        MultiDiscriminatorObjectMapper mapper = new MultiDiscriminatorObjectMapper(jackson);
        String json = """
                {
                    "type": "registered",
                    "value": "ok"
                }
                """;
        RegisteredBase actual = mapper.readValue(json, RegisteredBase.class);
        assertEquals(new RegisteredItem("ok"), actual);
    }

    @Test
    void testUnnamedSubTypeUsesDefaultTypeId() throws Exception {
        String json = """
                {
                    "type": "PolymorphicSupportTest$UnnamedItem",
                    "value": "ok"
                }
                """;
        UnnamedBase actual = MAPPER.readValue(json, UnnamedBase.class);
        assertEquals(new UnnamedItem("ok"), actual);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ConcreteDog.class, name = "dog")
    })
    static class ConcreteAnimal {
        ConcreteAnimal() {
        }
    }

    static final class ConcreteDog extends ConcreteAnimal {
        final String name;

        @JsonCreator
        ConcreteDog(@JsonProperty("name") String name) {
            this.name = name;
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type",
            defaultImpl = DefaultItem.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = KnownItem.class, name = "known")
    })
    interface DefaultBase {
    }

    record KnownItem(String value) implements DefaultBase {
    }

    record DefaultItem(String value) implements DefaultBase {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    interface RegisteredBase {
    }

    record RegisteredItem(String value) implements RegisteredBase {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = UnnamedItem.class)
    })
    interface UnnamedBase {
    }

    record UnnamedItem(String value) implements UnnamedBase {
    }
}
