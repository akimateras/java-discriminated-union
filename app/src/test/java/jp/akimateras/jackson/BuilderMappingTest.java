package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import jp.akimateras.jackson.models.Artiodactyla;

class BuilderMappingTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testBuilderFromJsonDeserialize() throws Exception {
        String json = """
                {
                    "name": "mika",
                    "age": 4,
                    "mood": "calm"
                }
                """;
        BuiltPet actual = MAPPER.readValue(json, BuiltPet.class);
        assertEquals("mika", actual.name);
        assertEquals(4, actual.age);
        assertEquals("calm", actual.mood);
    }

    @Test
    void testBuilderFromFactoryMethod() throws Exception {
        String json = """
                {
                    "name": "sato",
                    "age": 2
                }
                """;
        FactoryBuiltPet actual = MAPPER.readValue(json, FactoryBuiltPet.class);
        assertEquals("sato", actual.name);
        assertEquals(2, actual.age);
    }

    @Test
    void testBuilderWithNoPrefix() throws Exception {
        String json = """
                {
                    "name": "kiri",
                    "age": 3
                }
                """;
        NoPrefixPet actual = MAPPER.readValue(json, NoPrefixPet.class);
        assertEquals("kiri", actual.name);
        assertEquals(3, actual.age);
    }

    @Test
    void testBuilderCustomBuildMethod() throws Exception {
        String json = """
                {
                    "name": "kazu",
                    "age": 6
                }
                """;
        CustomBuildPet actual = MAPPER.readValue(json, CustomBuildPet.class);
        assertEquals("kazu", actual.name);
        assertEquals(6, actual.age);
    }

    @Test
    void testBuilderWithUnionField() throws Exception {
        String json = """
                {
                    "label": "pen",
                    "animal": {
                        "species": "vicugna",
                        "color": "beige"
                    }
                }
                """;
        BuilderUnionPet actual = MAPPER.readValue(json, BuilderUnionPet.class);
        assertEquals("pen", actual.label);
        assertEquals(new Artiodactyla.Vicugna("beige", null), actual.animal);
    }

    @JsonDeserialize(builder = BuiltPet.Builder.class)
    static final class BuiltPet {
        final String name;
        final int age;
        String mood = "";

        private BuiltPet(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public void setMood(String mood) {
            this.mood = mood;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        static final class Builder {
            private String name = "";
            private int age;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withAge(int age) {
                this.age = age;
                return this;
            }

            public BuiltPet build() {
                return new BuiltPet(name, age);
            }
        }
    }

    static final class FactoryBuiltPet {
        final String name;
        final int age;

        private FactoryBuiltPet(String name, int age) {
            this.name = name;
            this.age = age;
        }

        static Builder builder() {
            return new Builder();
        }

        @JsonPOJOBuilder(withPrefix = "set")
        static final class Builder {
            private String name = "";
            private int age;

            public Builder setName(String name) {
                this.name = name;
                return this;
            }

            public Builder setAge(int age) {
                this.age = age;
                return this;
            }

            public FactoryBuiltPet build() {
                return new FactoryBuiltPet(name, age);
            }
        }
    }

    @JsonDeserialize(builder = NoPrefixPet.Builder.class)
    static final class NoPrefixPet {
        final String name;
        final int age;

        private NoPrefixPet(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @JsonPOJOBuilder(withPrefix = "")
        static final class Builder {
            private String name = "";
            private int age;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder age(int age) {
                this.age = age;
                return this;
            }

            public NoPrefixPet build() {
                return new NoPrefixPet(name, age);
            }
        }
    }

    @JsonDeserialize(builder = CustomBuildPet.Builder.class)
    static final class CustomBuildPet {
        final String name;
        final int age;

        private CustomBuildPet(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @JsonPOJOBuilder(withPrefix = "with", buildMethodName = "create")
        static final class Builder {
            private String name = "";
            private int age;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withAge(int age) {
                this.age = age;
                return this;
            }

            public CustomBuildPet create() {
                return new CustomBuildPet(name, age);
            }
        }
    }

    static final class BuilderUnionPet {
        final String label;
        final Artiodactyla animal;

        @JsonCreator
        BuilderUnionPet(@JsonProperty("label") String label, @JsonProperty("animal") Artiodactyla animal) {
            this.label = label;
            this.animal = animal;
        }

        static Builder builder() {
            return new Builder();
        }

        @JsonPOJOBuilder(withPrefix = "")
        static final class Builder {
            private String label = "";
            private Artiodactyla animal = new Artiodactyla.Vicugna("default", null);

            public Builder label(String label) {
                this.label = label;
                return this;
            }

            public Builder animal(Artiodactyla animal) {
                this.animal = animal;
                return this;
            }

            public BuilderUnionPet build() {
                return new BuilderUnionPet(label, animal);
            }
        }
    }
}
