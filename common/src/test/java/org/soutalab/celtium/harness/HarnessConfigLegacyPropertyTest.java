package org.soutalab.celtium.harness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy command-line layer of {@link HarnessConfig}: with the master switch on, a key set as
 * {@code -Dceltium.vulkan.<key>} (or, second, {@code -Dceltium.validation.<key>}) is honoured below the
 * harness file and above the defaults; with the switch off it is ignored entirely, so a player's run
 * cannot wake a harness instrument through a stray launcher argument.
 * <p>
 * The probe keys are chosen to not exist in any harness file the test working directory might carry, so
 * the file layer reads as absent throughout.
 */
class HarnessConfigLegacyPropertyTest {

    private static final String[] TOUCHED = {
            "celtium.validation",
            "celtium.vulkan.legacyProbeBool",
            "celtium.validation.legacyProbeBool",
            "celtium.vulkan.legacyProbeText",
            "celtium.validation.legacyProbeText",
            "celtium.validation.legacyProbeCount",
            "celtium.validation.legacyProbeLong",
            "celtium.validation.legacyProbeRatio",
            "celtium.vulkan.legacyProbeDir",
    };

    private final Map<String, String> previous = new HashMap<>();

    @BeforeEach
    void saveAndClear() {
        for (String name : TOUCHED) {
            previous.put(name, System.getProperty(name));
            System.clearProperty(name);
        }
    }

    @AfterEach
    void restore() {
        for (String name : TOUCHED) {
            String value = previous.get(name);
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        }
    }

    private static void switchOn() {
        System.setProperty("celtium.validation", "true");
    }

    @Test
    void vulkanSpellingIsHonouredWhenTheSwitchIsOn() {
        switchOn();
        System.setProperty("celtium.vulkan.legacyProbeBool", "false");
        assertFalse(HarnessConfig.bool("legacyProbeBool", true));

        System.setProperty("celtium.vulkan.legacyProbeBool", "true");
        assertTrue(HarnessConfig.bool("legacyProbeBool", false));
    }

    @Test
    void validationSpellingIsHonouredWhenTheSwitchIsOn() {
        switchOn();
        System.setProperty("celtium.validation.legacyProbeCount", "7");
        assertEquals(7, HarnessConfig.integer("legacyProbeCount", 1, 2));
    }

    @Test
    void vulkanSpellingWinsOverTheValidationSpelling() {
        switchOn();
        System.setProperty("celtium.validation.legacyProbeText", "from-validation");
        System.setProperty("celtium.vulkan.legacyProbeText", "from-vulkan");
        assertEquals("from-vulkan", HarnessConfig.string("legacyProbeText", "harness", "shipping"));
    }

    @Test
    void longAndDoubleComeFromTheLegacySpellings() {
        switchOn();
        System.setProperty("celtium.validation.legacyProbeLong", "1234567890123");
        System.setProperty("celtium.validation.legacyProbeRatio", "2.5");
        assertEquals(1234567890123L, HarnessConfig.longValue("legacyProbeLong", 1L));
        assertEquals(2.5D, HarnessConfig.doubleValue("legacyProbeRatio", 1.0D));
    }

    @Test
    void pathIsHonouredFromTheVulkanSpellingWhenTheSwitchIsOn() {
        switchOn();
        System.setProperty("celtium.vulkan.legacyProbeDir", "/tmp/legacy-probe-dir");
        assertEquals("/tmp/legacy-probe-dir", HarnessConfig.path("legacyProbeDir"));
    }

    @Test
    void legacySpellingsAreIgnoredWhenTheSwitchIsOff() {
        // No celtium.validation=true: the run is a player's run as far as this class is concerned.
        System.setProperty("celtium.vulkan.legacyProbeBool", "true");
        System.setProperty("celtium.validation.legacyProbeCount", "7");
        System.setProperty("celtium.validation.legacyProbeText", "from-validation");
        System.setProperty("celtium.vulkan.legacyProbeDir", "/tmp/legacy-probe-dir");

        assertFalse(HarnessConfig.bool("legacyProbeBool", true));
        assertEquals(2, HarnessConfig.integer("legacyProbeCount", 1, 2));
        assertEquals("shipping", HarnessConfig.string("legacyProbeText", "harness", "shipping"));
        assertNull(HarnessConfig.path("legacyProbeDir"));
    }
}
