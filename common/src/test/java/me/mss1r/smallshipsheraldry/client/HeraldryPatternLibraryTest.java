package me.mss1r.smallshipsheraldry.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryPatternLibraryTest {
    @Test
    void sanitizesNamesWithoutAllowingPaths() {
        String sanitized = HeraldryPatternLibrary.sanitizeName(" ../My: Pattern\\test ");

        assertEquals("My Patterntest", sanitized);
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
    }

    @Test
    void handlesReservedAndLongNames() {
        assertEquals("CON_", HeraldryPatternLibrary.sanitizeName("CON"));
        assertTrue(HeraldryPatternLibrary.sanitizeName("x".repeat(100)).length()
                <= HeraldryPatternLibrary.MAX_NAME_LENGTH);
    }
}
