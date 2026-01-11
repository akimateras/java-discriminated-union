package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

class PojoSetterAndFieldMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testSetterOnly() throws Exception {
        String json = """
                {
                    "name": "kumo"
                }
                """;
        SetterOnly actual = MAPPER.readValue(json, SetterOnly.class);
        assertEquals("kumo", actual.name);
    }

    @Test
    void testFieldOnly() throws Exception {
        String json = """
                {
                    "name": "hana"
                }
                """;
        FieldOnly actual = MAPPER.readValue(json, FieldOnly.class);
        assertEquals("hana", actual.name);
    }

    @Test
    void testJsonPropertyField() throws Exception {
        String json = """
                {
                    "first_name": "tama"
                }
                """;
        JsonPropertyField actual = MAPPER.readValue(json, JsonPropertyField.class);
        assertEquals("tama", actual.firstName);
    }

    @Test
    void testJsonPropertySetter() throws Exception {
        String json = """
                {
                    "nick": "maru"
                }
                """;
        JsonPropertySetter actual = MAPPER.readValue(json, JsonPropertySetter.class);
        assertEquals("maru", actual.nickname);
    }

    @Test
    void testJsonAliasField() throws Exception {
        String json = """
                {
                    "alias": "suzu"
                }
                """;
        JsonAliasField actual = MAPPER.readValue(json, JsonAliasField.class);
        assertEquals("suzu", actual.name);
    }

    @Test
    void testJsonAliasSetterMethod() throws Exception {
        String json = """
                {
                    "alias_name": "nagi"
                }
                """;
        JsonAliasSetterMethod actual = MAPPER.readValue(json, JsonAliasSetterMethod.class);
        assertEquals("nagi", actual.name);
    }

    @Test
    void testJsonAliasSetterParam() throws Exception {
        String json = """
                {
                    "param_alias": "ruru"
                }
                """;
        JsonAliasSetterParam actual = MAPPER.readValue(json, JsonAliasSetterParam.class);
        assertEquals("ruru", actual.name);
    }

    @Test
    void testJsonIgnoreField() throws Exception {
        String json = """
                {
                    "name": "mori",
                    "secret": "exposed"
                }
                """;
        JsonIgnoreField actual = MAPPER.readValue(json, JsonIgnoreField.class);
        assertEquals("mori", actual.name);
        assertEquals("hidden", actual.secret);
    }

    @Test
    void testSetterOverridesField() throws Exception {
        String json = """
                {
                    "name": "tento"
                }
                """;
        SetterOverridesField actual = MAPPER.readValue(json, SetterOverridesField.class);
        assertEquals("setter:tento", actual.name);
    }

    static final class SetterOnly {
        String name = "";

        public void setName(String name) {
            this.name = name;
        }
    }

    static final class FieldOnly {
        String name = "";
    }

    static final class JsonPropertyField {
        @JsonProperty("first_name")
        String firstName = "";
    }

    static final class JsonPropertySetter {
        String nickname = "";

        @JsonProperty("nick")
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    static final class JsonAliasField {
        @JsonAlias({ "alias", "nick" })
        String name = "";
    }

    static final class JsonAliasSetterMethod {
        String name = "";

        @JsonAlias("alias_name")
        public void setName(String name) {
            this.name = name;
        }
    }

    static final class JsonAliasSetterParam {
        String name = "";

        public void setName(@JsonAlias("param_alias") String name) {
            this.name = name;
        }
    }

    static final class JsonIgnoreField {
        String name = "";

        @JsonIgnore
        String secret = "hidden";
    }

    static final class SetterOverridesField {
        String name = "";

        public void setName(String name) {
            this.name = "setter:" + name;
        }
    }
}
