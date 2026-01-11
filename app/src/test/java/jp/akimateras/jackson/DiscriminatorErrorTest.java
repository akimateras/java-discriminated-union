package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.akimateras.jackson.models.Artiodactyla;

class DiscriminatorErrorTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testMissingSpeciesDiscriminator() throws Exception {
        String json = """
                {
                    "color": "brown"
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }

    @Test
    void testUnknownSpeciesDiscriminator() throws Exception {
        String json = """
                {
                    "species": "unicorn",
                    "color": "silver"
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }

    @Test
    void testMissingKindDiscriminator() throws Exception {
        String json = """
                {
                    "species": "alpaca",
                    "color": "white",
                    "hairLength": 10
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }

    @Test
    void testMissingMoveDiscriminator() throws Exception {
        String json = """
                {
                    "species": "llama",
                    "color": "brown",
                    "weightCapacityKg": 150.5,
                    "moves": [
                        {
                            "speed": 12.3
                        }
                    ]
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }

    @Test
    void testUnknownMoveDiscriminator() throws Exception {
        String json = """
                {
                    "species": "llama",
                    "color": "brown",
                    "weightCapacityKg": 150.5,
                    "moves": [
                        {
                            "move": "fly"
                        }
                    ]
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }

    @Test
    void testMissingDiscriminatorInListElement() throws Exception {
        String json = """
                [
                    {
                        "species": "vicugna",
                        "color": "tan"
                    },
                    {
                        "color": "gray"
                    }
                ]
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, new TypeReference<List<Artiodactyla>>() {
        }));
    }

    @Test
    void testNonObjectForAbstractType() throws Exception {
        String json = "\"llama\"";
        assertThrows(IOException.class, () -> MAPPER.readValue(json, Artiodactyla.class));
    }
}
