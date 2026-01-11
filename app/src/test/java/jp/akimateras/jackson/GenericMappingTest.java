package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import jp.akimateras.jackson.models.Artiodactyla;

class GenericMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testJsonNodeReturn() throws Exception {
        String json = """
                {
                    "a": 1
                }
                """;
        JsonNode node = MAPPER.readValue(json, JsonNode.class);
        assertEquals(1, node.get("a").asInt());
    }

    @Test
    void testObjectReturnMap() throws Exception {
        String json = """
                {
                    "a": 1,
                    "b": {
                        "c": 2
                    }
                }
                """;
        Object actual = MAPPER.readValue(json, Object.class);
        assertTrue(actual instanceof Map);
        Map<?, ?> map = (Map<?, ?>) actual;
        assertEquals(1, map.get("a"));
        assertTrue(map.get("b") instanceof Map);
    }

    @Test
    void testTrailingComma() throws Exception {
        String json = """
                {
                    "name": "nori",
                }
                """;
        NameRecord actual = MAPPER.readValue(json, NameRecord.class);
        assertEquals("nori", actual.name());
    }

    @Test
    void testExtraPropertiesIgnoredInRecord() throws Exception {
        String json = """
                {
                    "name": "tama",
                    "extra": 3
                }
                """;
        NameRecord actual = MAPPER.readValue(json, NameRecord.class);
        assertEquals("tama", actual.name());
    }

    @Test
    void testNullForNullableRecordField() throws Exception {
        String json = """
                {
                    "name": "sora",
                    "note": null
                }
                """;
        NullableRecord actual = MAPPER.readValue(json, NullableRecord.class);
        assertEquals("sora", actual.name());
        assertEquals(null, actual.note);
    }

    @Test
    void testExplicitNullForPrimitiveFails() throws Exception {
        String json = """
                {
                    "count": null
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, PrimitiveHolder.class));
    }

    @Test
    void testArrayToArrayMapping() throws Exception {
        String json = """
                [
                    {
                        "move": "run",
                        "speed": 6.6
                    },
                    {
                        "move": "spits"
                    }
                ]
                """;
        Artiodactyla.Move[] actual = MAPPER.readValue(json, Artiodactyla.Move[].class);
        Artiodactyla.Move[] expected = new Artiodactyla.Move[] {
                new Artiodactyla.Move.Run(6.6f),
                new Artiodactyla.Move.Spits()
        };
        assertArrayEquals(expected, actual);
    }

    @Test
    void testRecordAliasMapping() throws Exception {
        String json = """
                {
                    "nick": "yama"
                }
                """;
        AliasRecord actual = MAPPER.readValue(json, AliasRecord.class);
        assertEquals("yama", actual.name());
    }

    record NameRecord(String name) {
    }

    record NullableRecord(String name, @Nullable String note) {
    }

    record AliasRecord(@JsonAlias("nick") String name) {
    }

    static final class PrimitiveHolder {
        int count = 1;

        public void setCount(int count) {
            this.count = count;
        }
    }
}
