package me.mss1r.smallshipsheraldry.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeraldryClientSettingsTest {
    @Test
    void usesFallbackForMissingOrInvalidFloatSettings() {
        assertEquals(1.0F, HeraldryClientSettings.parseFloat(null, 1.0F, 0.3F, 10.0F));
        assertEquals(1.0F, HeraldryClientSettings.parseFloat("", 1.0F, 0.3F, 10.0F));
        assertEquals(1.0F, HeraldryClientSettings.parseFloat("not-a-number", 1.0F, 0.3F, 10.0F));
        assertEquals(1.0F, HeraldryClientSettings.parseFloat("NaN", 1.0F, 0.3F, 10.0F));
    }

    @Test
    void clampsValidFloatSettings() {
        assertEquals(0.3F, HeraldryClientSettings.parseFloat("0.1", 1.0F, 0.3F, 10.0F));
        assertEquals(2.5F, HeraldryClientSettings.parseFloat("2.5", 1.0F, 0.3F, 10.0F));
        assertEquals(10.0F, HeraldryClientSettings.parseFloat("15.0", 1.0F, 0.3F, 10.0F));
    }
}
