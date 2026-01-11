package jp.akimateras.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MissingPropertyNullabilityTest {
    private static final MultiDiscriminatorObjectMapper MAPPER = new MultiDiscriminatorObjectMapper();
    private static final MultiDiscriminatorObjectMapper NULLABLE_MAPPER =
            new MultiDiscriminatorObjectMapper(false);

    @Test
    void testMissingNonNullFieldThrows() throws Exception {
        String json = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, RequiredField.class));
    }

    @Test
    void testMissingNullableFieldSetsNull() throws Exception {
        String json = """
                {
                }
                """;
        NullableField actual = MAPPER.readValue(json, NullableField.class);
        assertEquals(null, actual.note);
    }

    @Test
    void testMissingUnannotatedFieldDefaultNonNullThrows() throws Exception {
        String json = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, DefaultedField.class));
    }

    @Test
    void testMissingUnannotatedSetterDefaultNonNullThrows() throws Exception {
        String json = """
                {
                }
                """;
        assertThrows(IOException.class, () -> MAPPER.readValue(json, DefaultedSetter.class));
    }

    @Test
    void testMissingNullableSetterSetsNull() throws Exception {
        String json = """
                {
                }
                """;
        NullableSetter actual = MAPPER.readValue(json, NullableSetter.class);
        assertEquals(null, actual.note);
    }

    @Test
    void testMissingUnannotatedFieldDefaultNullableSetsNull() throws Exception {
        String json = """
                {
                }
                """;
        DefaultedField actual = NULLABLE_MAPPER.readValue(json, DefaultedField.class);
        assertEquals(null, actual.status);
    }

    @Test
    void testMissingUnannotatedSetterDefaultNullableSetsNull() throws Exception {
        String json = """
                {
                }
                """;
        DefaultedSetter actual = NULLABLE_MAPPER.readValue(json, DefaultedSetter.class);
        assertEquals(null, actual.name);
    }

    static final class RequiredField {
        @NonNull
        String name = "default";
    }

    static final class NullableField {
        @Nullable
        String note = "preset";
    }

    static final class DefaultedField {
        String status = "active";
    }

    static final class DefaultedSetter {
        String name = "guest";

        public void setName(String name) {
            this.name = name;
        }
    }

    static final class NullableSetter {
        @Nullable
        String note = "preset";

        public void setNote(@Nullable String note) {
            this.note = note;
        }
    }
}
