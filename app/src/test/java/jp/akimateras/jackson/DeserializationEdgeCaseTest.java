package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

class DeserializationEdgeCaseTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testVisibleTypePropertyPreserved() throws Exception {
        String json = """
                {
                    "type": "visible",
                    "value": "ok"
                }
                """;
        VisibleBase actual = MAPPER.readValue(json, VisibleBase.class);
        assertEquals(new VisibleItem("visible", "ok"), actual);
    }

    @Test
    void testRecordAccessorAlias() throws Exception {
        String json = """
                {
                    "nick": "sora"
                }
                """;
        AccessorAliasRecord actual = MAPPER.readValue(json, AccessorAliasRecord.class);
        assertEquals("sora", actual.name());
    }

    @Test
    void testRecordAccessorJsonProperty() throws Exception {
        String json = """
                {
                    "user_name": "momo"
                }
                """;
        AccessorPropertyRecord actual = MAPPER.readValue(json, AccessorPropertyRecord.class);
        assertEquals("momo", actual.name());
    }

    @Test
    void testDelegatingCreatorConstructor() throws Exception {
        String json = """
                {
                    "a": 1
                }
                """;
        DelegatingConstructorHolder actual = MAPPER.readValue(json, DelegatingConstructorHolder.class);
        assertEquals(1, actual.node.get("a").asInt());
    }

    @Test
    void testDelegatingCreatorFactory() throws Exception {
        String json = """
                {
                    "value": "hi"
                }
                """;
        DelegatingFactoryHolder actual = MAPPER.readValue(json, DelegatingFactoryHolder.class);
        assertEquals("hi", actual.value);
    }

    @Test
    void testDelegatingRecordCreator() throws Exception {
        String json = "\"token\"";
        DelegatingRecord actual = MAPPER.readValue(json, DelegatingRecord.class);
        assertEquals(new DelegatingRecord("token"), actual);
    }

    @Test
    void testNonJsonBuilderFactoryIgnored() throws Exception {
        String json = """
                {
                    "name": "nori"
                }
                """;
        NonJsonBuilderFactory actual = MAPPER.readValue(json, NonJsonBuilderFactory.class);
        assertEquals("nori", actual.name);
    }

    @Test
    void testNonJsonNestedBuilderIgnored() throws Exception {
        String json = """
                {
                    "name": "tori"
                }
                """;
        NonJsonNestedBuilder actual = MAPPER.readValue(json, NonJsonNestedBuilder.class);
        assertEquals("tori", actual.name);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = VisibleItem.class, name = "visible")
    })
    sealed interface VisibleBase permits VisibleItem {
    }

    record VisibleItem(String type, String value) implements VisibleBase {
    }

    record AccessorAliasRecord(String name) {
        @Override
        @JsonAlias("nick")
        public String name() {
            return name;
        }
    }

    record AccessorPropertyRecord(String name) {
        @Override
        @JsonProperty("user_name")
        public String name() {
            return name;
        }
    }

    static final class DelegatingConstructorHolder {
        final JsonNode node;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        DelegatingConstructorHolder(JsonNode node) {
            this.node = node;
        }
    }

    static final class DelegatingFactoryHolder {
        final String value;

        private DelegatingFactoryHolder(String value) {
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static DelegatingFactoryHolder from(JsonNode node) {
            return new DelegatingFactoryHolder(node.get("value").asText());
        }
    }

    static final class NonJsonBuilderFactory {
        String name = "";

        public void setName(String name) {
            this.name = name;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            public Builder withName(String name) {
                return this;
            }
        }
    }

    static final class NonJsonNestedBuilder {
        String name = "";

        public void setName(String name) {
            this.name = name;
        }

        static final class Builder {
            public Builder withName(String name) {
                return this;
            }
        }
    }

    record DelegatingRecord(String value) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        DelegatingRecord(String value) {
            this.value = value;
        }
    }
}
