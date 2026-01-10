package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class AppTest {
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

        var actual = App.map(json, Artiodactyla.class);
        var expected = new Llama(
                "brown",
                150.5f,
                List.of(
                        new Run(12.3f),
                        new Spits()));
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
                        },
                    ]
                }
                """;
        var actual = App.map(json, Artiodactyla.class);
        var expected = new Huacaya(
                "white",
                10,
                8,
                List.of(
                        new Spits()));
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
        var actual = App.map(json, Artiodactyla.class);
        var expected = new Vicugna(
                "golden",
                List.of(new Bite()));
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
        var actual = App.map(json, Artiodactyla.class);
        var expected = new Suri(
                "gray",
                14,
                List.of(new Run(9.8f)));
        assertEquals(expected, actual);
    }
}
