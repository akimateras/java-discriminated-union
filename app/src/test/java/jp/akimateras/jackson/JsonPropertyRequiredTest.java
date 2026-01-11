package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class JsonPropertyRequiredTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();

    @Test
    void testRequiredFieldOverridesNullable() throws Exception {
        String missingJson = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(missingJson, RequiredNullableField.class));

        String nullJson = """
                {
                    "note": null
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(nullJson, RequiredNullableField.class));

        String presentJson = """
                {
                    "note": "ok"
                }
                """;
        RequiredNullableField actual = MAPPER.readValue(presentJson, RequiredNullableField.class);
        assertEquals("ok", actual.note);
    }

    @Test
    void testNonNullFieldOverridesRequiredFalse() throws Exception {
        String missingJson = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(missingJson, OptionalNonNullField.class));

        String nullJson = """
                {
                    "name": null
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(nullJson, OptionalNonNullField.class));
    }

    @Test
    void testRequiredCreatorOverridesNullable() throws Exception {
        String missingJson = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(missingJson, RequiredNullableCreator.class));

        String nullJson = """
                {
                    "name": null
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(nullJson, RequiredNullableCreator.class));

        String presentJson = """
                {
                    "name": "mona"
                }
                """;
        RequiredNullableCreator actual = MAPPER.readValue(presentJson, RequiredNullableCreator.class);
        assertEquals("mona", actual.name);
    }

    @Test
    void testNonNullCreatorOverridesRequiredFalse() throws Exception {
        String missingJson = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(missingJson, OptionalNonNullCreator.class));

        String nullJson = """
                {
                    "name": null
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(nullJson, OptionalNonNullCreator.class));
    }

    static final class RequiredNullableField {
        @JsonProperty(value = "note", required = true)
        @Nullable
        String note;
    }

    static final class OptionalNonNullField {
        @JsonProperty(value = "name", required = false)
        @NonNull
        String name = "default";
    }

    static final class RequiredNullableCreator {
        final @Nullable String name;

        private RequiredNullableCreator(@Nullable String name) {
            this.name = name;
        }

        @JsonCreator
        static RequiredNullableCreator create(
                @JsonProperty(value = "name", required = true) @Nullable String name) {
            return new RequiredNullableCreator(name);
        }
    }

    static final class OptionalNonNullCreator {
        final String name;

        private OptionalNonNullCreator(String name) {
            this.name = name;
        }

        @JsonCreator
        static OptionalNonNullCreator create(
                @JsonProperty(value = "name", required = false) @NonNull String name) {
            return new OptionalNonNullCreator(name);
        }
    }
}
