package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jp.akimateras.jackson.models.Artiodactyla;

class PojoConstructorMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testJsonCreatorConstructor() throws Exception {
        String json = """
                {
                    "name": "kai",
                    "age": 4
                }
                """;
        CreatorPet actual = MAPPER.readValue(json, CreatorPet.class);
        assertEquals("kai", actual.name);
        assertEquals(4, actual.age);
    }

    @Test
    void testSingleConstructorWithoutCreator() throws Exception {
        String json = """
                {
                    "name": "momo",
                    "age": 2
                }
                """;
        SingleConstructorPet actual = MAPPER.readValue(json, SingleConstructorPet.class);
        assertEquals("momo", actual.name);
        assertEquals(2, actual.age);
    }

    @Test
    void testConstructorUsesImplicitParameterNames() throws Exception {
        String json = """
                {
                    "name": "nana",
                    "age": 3
                }
                """;
        ImplicitConstructorPet actual = MAPPER.readValue(json, ImplicitConstructorPet.class);
        assertEquals("nana", actual.name);
        assertEquals(3, actual.age);
    }

    @Test
    void testConstructorAlias() throws Exception {
        String json = """
                {
                    "pet_name": "hana",
                    "age": 5
                }
                """;
        AliasConstructorPet actual = MAPPER.readValue(json, AliasConstructorPet.class);
        assertEquals("hana", actual.name);
        assertEquals(5, actual.age);
    }

    @Test
    void testConstructorNullableMissing() throws Exception {
        String json = """
                {
                    "name": "taro"
                }
                """;
        NullableConstructorPet actual = MAPPER.readValue(json, NullableConstructorPet.class);
        assertEquals("taro", actual.name);
        assertEquals(null, actual.nickname);
    }

    @Test
    void testConstructorPrimitiveMissingFails() throws Exception {
        String json = """
                {
                    "name": "sora"
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, PrimitiveMissingPet.class));
    }

    @Test
    void testConstructorExtraSetter() throws Exception {
        String json = """
                {
                    "name": "yuki",
                    "age": 7
                }
                """;
        ConstructorWithSetterPet actual = MAPPER.readValue(json, ConstructorWithSetterPet.class);
        assertEquals("yuki", actual.name);
        assertEquals(7, actual.age);
    }

    @Test
    void testConstructorWithMoveParam() throws Exception {
        String json = """
                {
                    "name": "riko",
                    "move": {
                        "move": "run",
                        "speed": 3.5
                    }
                }
                """;
        ConstructorMovePet actual = MAPPER.readValue(json, ConstructorMovePet.class);
        assertEquals("riko", actual.name);
        assertEquals(new Artiodactyla.Move.Run(3.5f), actual.move);
    }

    @Test
    void testConstructorWithUnionParam() throws Exception {
        String json = """
                {
                    "label": "alpha",
                    "animal": {
                        "species": "vicugna",
                        "color": "beige"
                    }
                }
                """;
        ConstructorUnionPet actual = MAPPER.readValue(json, ConstructorUnionPet.class);
        assertEquals("alpha", actual.label);
        assertEquals(new Artiodactyla.Vicugna("beige", null), actual.animal);
    }

    @Test
    void testJsonCreatorFactoryMethod() throws Exception {
        String json = """
                {
                    "name": "mei",
                    "age": 6
                }
                """;
        FactoryPet actual = MAPPER.readValue(json, FactoryPet.class);
        assertEquals("mei", actual.name);
        assertEquals(6, actual.age);
    }

    static final class CreatorPet {
        final String name;
        final int age;

        @JsonCreator
        CreatorPet(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    static final class SingleConstructorPet {
        final String name;
        final int age;

        SingleConstructorPet(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    static final class ImplicitConstructorPet {
        final String name;
        final int age;

        ImplicitConstructorPet(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static final class AliasConstructorPet {
        final String name;
        final int age;

        AliasConstructorPet(@JsonAlias("pet_name") @JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    static final class NullableConstructorPet {
        final String name;
        final @Nullable String nickname;

        NullableConstructorPet(@JsonProperty("name") String name, @JsonProperty("nickname") @Nullable String nickname) {
            this.name = name;
            this.nickname = nickname;
        }
    }

    static final class PrimitiveMissingPet {
        final String name;
        final int weight;

        PrimitiveMissingPet(@JsonProperty("name") String name, @JsonProperty("weight") int weight) {
            this.name = name;
            this.weight = weight;
        }
    }

    static final class ConstructorWithSetterPet {
        final String name;
        int age;

        ConstructorWithSetterPet(@JsonProperty("name") String name) {
            this.name = name;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    static final class ConstructorMovePet {
        final String name;
        final Artiodactyla.Move move;

        ConstructorMovePet(@JsonProperty("name") String name, @JsonProperty("move") Artiodactyla.Move move) {
            this.name = name;
            this.move = move;
        }
    }

    static final class ConstructorUnionPet {
        final String label;
        final Artiodactyla animal;

        ConstructorUnionPet(@JsonProperty("label") String label, @JsonProperty("animal") Artiodactyla animal) {
            this.label = label;
            this.animal = animal;
        }
    }

    static final class FactoryPet {
        final String name;
        int age;

        private FactoryPet(String name) {
            this.name = name;
        }

        @JsonCreator
        static FactoryPet create(@JsonProperty("name") String name) {
            return new FactoryPet(name);
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
