package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import jp.akimateras.jackson.models.Artiodactyla;

class DiscriminatorUnionTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testLlama() throws Exception {
        String json = """
                {
                    "species": "llama",
                    "color": "brown",
                    "weightCapacityKg": 150.5,
                    "moves": [
                        {
                            "move": "run",
                            "speed": 12.3
                        },
                        {
                            "move": "spits"
                        }
                    ]
                }
                """;

        var actual = MAPPER.readValue(json, Artiodactyla.class);
        var expected = new Artiodactyla.Llama(
                "brown",
                150.5f,
                List.of(
                        new Artiodactyla.Move.Run(12.3f),
                        new Artiodactyla.Move.Spits()));
        assertEquals(expected, actual);
    }

    @Test
    void testHuacaya() throws Exception {
        String json = """
                {
                    "species": "alpaca",
                    "kind": "huacaya",
                    "color": "white",
                    "hairLength": 10,
                    "fluffiness": 8,
                    "moves": [
                        {
                            "move": "spits"
                        }
                    ]
                }
                """;
        var actual = MAPPER.readValue(json, Artiodactyla.class);
        var expected = new Artiodactyla.Alpaca.Huacaya(
                "white",
                10,
                8,
                List.of(
                        new Artiodactyla.Move.Spits()));
        assertEquals(expected, actual);
    }

    @Test
    void testVicugna() throws Exception {
        String json = """
                {
                    "species": "vicugna",
                    "color": "golden",
                    "moves": [
                        {
                            "move": "bite"
                        }
                    ]
                }
                """;
        var actual = MAPPER.readValue(json, Artiodactyla.class);
        var expected = new Artiodactyla.Vicugna(
                "golden",
                List.of(new Artiodactyla.Move.Bite()));
        assertEquals(expected, actual);
    }

    @Test
    void testSuri() throws Exception {
        String json = """
                {
                    "species": "alpaca",
                    "kind": "suri",
                    "color": "gray",
                    "hairLength": 14,
                    "moves": [
                        {
                            "move": "run",
                            "speed": 9.8
                        }
                    ]
                }
                """;
        var actual = MAPPER.readValue(json, Artiodactyla.class);
        var expected = new Artiodactyla.Alpaca.Suri(
                "gray",
                14,
                List.of(new Artiodactyla.Move.Run(9.8f)));
        assertEquals(expected, actual);
    }

    @Test
    void testUnionInMapValue() throws Exception {
        String json = """
                {
                    "first": {
                        "species": "vicugna",
                        "color": "tan"
                    },
                    "second": {
                        "species": "llama",
                        "color": "black",
                        "weightCapacityKg": 90.0
                    }
                }
                """;
        Map<String, Artiodactyla> actual = MAPPER.readValue(json, new TypeReference<Map<String, Artiodactyla>>() {
        });
        Map<String, Artiodactyla> expected = Map.of(
                "first", new Artiodactyla.Vicugna("tan", null),
                "second", new Artiodactyla.Llama("black", 90.0f, null));
        assertEquals(expected, actual);
    }

    @Test
    void testUnionInPojoSetter() throws Exception {
        String json = """
                {
                    "animal": {
                        "species": "vicugna",
                        "color": "silver"
                    }
                }
                """;
        Barn actual = MAPPER.readValue(json, Barn.class);
        assertEquals(new Artiodactyla.Vicugna("silver", null), actual.animal);
    }

    @Test
    void testUnionInRecordField() throws Exception {
        String json = """
                {
                    "label": "pen-a",
                    "animal": {
                        "species": "alpaca",
                        "kind": "suri",
                        "color": "cream",
                        "hairLength": 5
                    }
                }
                """;
        Pen actual = MAPPER.readValue(json, Pen.class);
        assertEquals(new Artiodactyla.Alpaca.Suri("cream", 5, null), actual.animal());
        assertEquals("pen-a", actual.label());
    }

    @Test
    void testUnionInPojoConstructor() throws Exception {
        String json = """
                {
                    "label": "north",
                    "animal": {
                        "species": "llama",
                        "color": "red",
                        "weightCapacityKg": 120.0
                    }
                }
                """;
        Stable actual = MAPPER.readValue(json, Stable.class);
        assertEquals("north", actual.label);
        assertEquals(new Artiodactyla.Llama("red", 120.0f, null), actual.animal);
    }

    static final class Barn {
        @Nullable
        Artiodactyla animal;

        public Barn() {
        }

        public void setAnimal(Artiodactyla animal) {
            this.animal = animal;
        }
    }

    record Pen(String label, Artiodactyla animal) {
    }

    static final class Stable {
        final String label;
        final Artiodactyla animal;

        @JsonCreator
        Stable(@JsonProperty("label") String label, @JsonProperty("animal") Artiodactyla animal) {
            this.label = label;
            this.animal = animal;
        }
    }
}
