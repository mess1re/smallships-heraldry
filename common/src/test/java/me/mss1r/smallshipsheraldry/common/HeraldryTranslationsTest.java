package me.mss1r.smallshipsheraldry.common;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HeraldryTranslationsTest {
    private static final Pattern ARGUMENT = Pattern.compile("%(?:\\d+\\$)?s");

    @Test
    void languagesHaveTheSameKeysAndFormatArguments() throws Exception {
        var english = read("en_us");
        var russian = read("ru_ru");
        assertEquals(english.keySet(), russian.keySet());
        for (String key : english.keySet()) {
            assertEquals(ARGUMENT.matcher(english.get(key)).results().count(),
                    ARGUMENT.matcher(russian.get(key)).results().count(), key);
        }
    }

    @Test
    void guideSeparatesApplyingErasingAndCopying() throws Exception {
        for (String language : List.of("en_us", "ru_ru")) {
            var translations = read(language);
            for (String paragraph : List.of("section.ship", "apply", "remove", "duplicate")) {
                assertTrue(translations.containsKey("smallshipsheraldry.help." + paragraph),
                        language + ": " + paragraph);
            }
        }
    }

    @Test
    void libraryHasSeparateMessagesForFailedFileOperations() throws Exception {
        for (String language : List.of("en_us", "ru_ru")) {
            var translations = read(language);
            for (String operation : List.of("save", "rename", "delete", "folder")) {
                assertTrue(translations.containsKey("smallshipsheraldry.library." + operation + "_failed"),
                        language + ": " + operation);
            }
            assertFalse(translations.containsKey("smallshipsheraldry.library.io_error"));
        }
    }

    private Map<String, String> read(String language) throws Exception {
        String path = "/assets/smallshipsheraldry/lang/" + language + ".json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                var translations = new TreeMap<String, String>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    String value = reader.nextString();
                    assertFalse(value.isBlank(), key);
                    assertNull(translations.put(key, value), "Duplicate key: " + key);
                }
                reader.endObject();
                return translations;
            }
        }
    }
}
