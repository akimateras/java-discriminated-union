package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.akimateras.jackson.models.Artiodactyla;

class CollectionAndMapMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testMoveList() throws Exception {
        String json = """
                [
                    {
                        "move": "run",
                        "speed": 4.2
                    },
                    {
                        "move": "spits"
                    }
                ]
                """;
        List<Artiodactyla.Move> actual = MAPPER.readValue(json, new TypeReference<List<Artiodactyla.Move>>() {
        });
        List<Artiodactyla.Move> expected =
                List.of(new Artiodactyla.Move.Run(4.2f), new Artiodactyla.Move.Spits());
        assertEquals(expected, actual);
    }

    @Test
    void testMoveSet() throws Exception {
        String json = """
                [
                    {
                        "move": "bite"
                    },
                    {
                        "move": "run",
                        "speed": 2.1
                    }
                ]
                """;
        Set<Artiodactyla.Move> actual = MAPPER.readValue(json, new TypeReference<Set<Artiodactyla.Move>>() {
        });
        Set<Artiodactyla.Move> expected =
                Set.of(new Artiodactyla.Move.Bite(), new Artiodactyla.Move.Run(2.1f));
        assertEquals(expected, actual);
    }

    @Test
    void testMoveMapStringKey() throws Exception {
        String json = """
                {
                    "fast": {
                        "move": "run",
                        "speed": 5.5
                    },
                    "defend": {
                        "move": "bite"
                    }
                }
                """;
        Map<String, Artiodactyla.Move> actual =
                MAPPER.readValue(json, new TypeReference<Map<String, Artiodactyla.Move>>() {
        });
        Map<String, Artiodactyla.Move> expected = Map.of(
                "fast", new Artiodactyla.Move.Run(5.5f),
                "defend", new Artiodactyla.Move.Bite());
        assertEquals(expected, actual);
    }

    @Test
    void testMoveMapIntegerKey() throws Exception {
        String json = """
                {
                    "1": {
                        "move": "run",
                        "speed": 1.1
                    },
                    "2": {
                        "move": "spits"
                    }
                }
                """;
        Map<Integer, Artiodactyla.Move> actual =
                MAPPER.readValue(json, new TypeReference<Map<Integer, Artiodactyla.Move>>() {
        });
        Map<Integer, Artiodactyla.Move> expected = Map.of(
                1, new Artiodactyla.Move.Run(1.1f),
                2, new Artiodactyla.Move.Spits());
        assertEquals(expected, actual);
    }

    @Test
    void testSortedMapValue() throws Exception {
        String json = """
                {
                    "b": 2,
                    "a": 1
                }
                """;
        SortedMap<String, Integer> actual = MAPPER.readValue(json,
                new TypeReference<SortedMap<String, Integer>>() {
                });
        assertEquals(Map.of("a", 1, "b", 2), actual);
    }

    @Test
    void testMapStringToListMoves() throws Exception {
        String json = """
                {
                    "routine": [
                        {
                            "move": "run",
                            "speed": 3.1
                        },
                        {
                            "move": "bite"
                        }
                    ]
                }
                """;
        Map<String, List<Artiodactyla.Move>> actual =
                MAPPER.readValue(json, new TypeReference<Map<String, List<Artiodactyla.Move>>>() {
        });
        Map<String, List<Artiodactyla.Move>> expected = Map.of(
                "routine", List.of(new Artiodactyla.Move.Run(3.1f), new Artiodactyla.Move.Bite()));
        assertEquals(expected, actual);
    }

    @Test
    void testMapStringToUnionList() throws Exception {
        String json = """
                {
                    "group": [
                        {
                            "species": "vicugna",
                            "color": "beige"
                        },
                        {
                            "species": "llama",
                            "color": "black",
                            "weightCapacityKg": 80.0
                        }
                    ]
                }
                """;
        Map<String, List<Artiodactyla>> actual = MAPPER.readValue(json,
                new TypeReference<Map<String, List<Artiodactyla>>>() {
                });
        Map<String, List<Artiodactyla>> expected = Map.of(
                "group", List.of(
                        new Artiodactyla.Vicugna("beige", null),
                        new Artiodactyla.Llama("black", 80.0f, null)));
        assertEquals(expected, actual);
    }

    @Test
    void testListOfUnion() throws Exception {
        String json = """
                [
                    {
                        "species": "alpaca",
                        "kind": "huacaya",
                        "color": "white",
                        "hairLength": 7,
                        "fluffiness": 3
                    },
                    {
                        "species": "vicugna",
                        "color": "brown"
                    }
                ]
                """;
        List<Artiodactyla> actual = MAPPER.readValue(json, new TypeReference<List<Artiodactyla>>() {
        });
        List<Artiodactyla> expected = List.of(
                new Artiodactyla.Alpaca.Huacaya("white", 7, 3, null),
                new Artiodactyla.Vicugna("brown", null));
        assertEquals(expected, actual);
    }

    @Test
    void testNestedListMoves() throws Exception {
        String json = """
                [
                    [
                        {
                            "move": "run",
                            "speed": 8.0
                        }
                    ],
                    [
                        {
                            "move": "bite"
                        },
                        {
                            "move": "spits"
                        }
                    ]
                ]
                """;
        List<List<Artiodactyla.Move>> actual =
                MAPPER.readValue(json, new TypeReference<List<List<Artiodactyla.Move>>>() {
        });
        List<List<Artiodactyla.Move>> expected = List.of(
                List.of(new Artiodactyla.Move.Run(8.0f)),
                List.of(new Artiodactyla.Move.Bite(), new Artiodactyla.Move.Spits()));
        assertEquals(expected, actual);
    }
}
