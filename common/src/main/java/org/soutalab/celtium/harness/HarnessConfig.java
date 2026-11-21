package org.soutalab.celtium.harness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * The one place the test harness is configured from, since the 2026-11-20 flag consolidation.
 * <p>
 * The launch surface is a single switch: {@code -Dceltium.validation=true}. The
 * {@code celtium.validation.*} properties and the harness {@code celtium.vulkan.*} switches that used to
 * ride on the fixture's command line are gone, and their readers ask this class instead. A normal run
 * (no switch) must observe exactly the behaviour the code had with no {@code -D} at all, so every
 * accessor falls back to the reader's own shipping default while the switch is off.
 * <p>
 * The {@code celtium.debug.*} family is only partly consolidated, and the split is stated here because
 * it is invisible from any single call site: of the debug switches, exactly two ({@code buildFlow} and
 * {@code markTrace}) are read through this class. The rest - 48 distinct keys as of 2026-11-21 - are
 * still read directly from {@code -Dceltium.debug.*} at their call sites, take effect with or without
 * the master switch, and never consult the harness file: naming one of them in the file does nothing.
 * That is the current semantics, documented rather than changed (bughunt 09-02): routing those readers
 * through here would put them behind the master switch and change when they fire. So the route a given
 * switch takes is decided by its reader - if the reader calls this class, the file works; if it reads
 * {@code celtium.debug.*} itself, only the command line works.
 * <p>
 * Per-leg variation lives in a file, not on the command line: {@code celtium-harness.properties} in the
 * process working directory (the fixture's run directory), or in its {@code config} subdirectory. Keys are
 * the short names - the property name with its {@code celtium.validation.} / {@code celtium.vulkan.}
 * prefix removed (and, for the two debug traces named above, the {@code celtium.debug.} prefix) - so
 * {@code frameBudgetLines=2000} is what
 * {@code -Dceltium.vulkan.frameBudgetLines=2000} used to say. The file is read once, lazily, and a missing
 * or unreadable file is simply an empty one: the harness profile below is the standard leg, and the file
 * exists for the legs that differ from it (measurement legs turn the frame budget on and the periodic dumps
 * off; gate legs add tick timings and the build-flow trace).
 * <p>
 * Resolution order for every accessor: the file's value, then - with the master switch on - the harness
 * default the call site passes, then the shipping default the call site passes. Boolean accessors take no
 * separate shipping default: off is the only shipping state for a harness boolean, which keeps an
 * instrument from waking up in a player's run because a call site picked a cheerful default.
 */
public final class HarnessConfig {

    /**
     * The master switch, and the only {@code celtium.validation} property read anywhere in the tree.
     * It is not the only test-related property read in the tree: the unconsolidated
     * {@code celtium.debug.*} switches (see the class javadoc) are read directly at their call sites.
     */
    private static final String MASTER_PROPERTY = "celtium.validation";

    private HarnessConfig() {
    }

    /** Whether the validation harness is on: {@code -Dceltium.validation=true} was passed. */
    public static boolean enabled() {
        return Boolean.getBoolean(MASTER_PROPERTY);
    }

    /**
     * A harness boolean. With the switch on and no file entry, {@code harnessDefault} is the answer; with
     * the switch off, the answer is {@code false} unless the file names the key.
     */
    public static boolean bool(String key, boolean harnessDefault) {
        String value = FileValues.get(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return enabled() && harnessDefault;
    }

    /** A harness integer whose harness and shipping defaults differ. */
    public static int integer(String key, int harnessDefault, int shippingDefault) {
        String value = FileValues.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                // A malformed file entry is a harness typo, not a reason to fail a frame over.
            }
        }
        return enabled() ? harnessDefault : shippingDefault;
    }

    /** A harness integer with one default for both shapes of run. */
    public static int integer(String key, int defaultValue) {
        return integer(key, defaultValue, defaultValue);
    }

    /** A harness long with one default for both shapes of run. */
    public static long longValue(String key, long defaultValue) {
        String value = FileValues.get(key);
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ignored) {
                // As above: fall back rather than fail.
            }
        }
        return defaultValue;
    }

    /** A harness double with one default for both shapes of run. */
    public static double doubleValue(String key, double defaultValue) {
        String value = FileValues.get(key);
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                // As above: fall back rather than fail.
            }
        }
        return defaultValue;
    }

    /** A harness string whose harness and shipping defaults differ. */
    public static String string(String key, String harnessDefault, String shippingDefault) {
        String value = FileValues.get(key);
        if (value != null) {
            return value;
        }
        return enabled() ? harnessDefault : shippingDefault;
    }

    /** A harness string with one default for both shapes of run. */
    public static String string(String key, String defaultValue) {
        return string(key, defaultValue, defaultValue);
    }

    /**
     * A harness directory, named by the file only: there is no default, because a run that did not ask for
     * dumps must not grow a dump directory just by enabling the harness. A relative value resolves against
     * the working directory, so a fixture template copied into a fresh run directory points at that
     * directory's own folder without an absolute path being rewritten per leg.
     */
    public static String path(String key) {
        String value = FileValues.get(key);
        if (value == null) {
            return null;
        }
        Path parsed = Paths.get(value.trim());
        if (parsed.isAbsolute()) {
            return parsed.toString();
        }
        return Paths.get(System.getProperty("user.dir", ".")).resolve(parsed).toString();
    }

    /** The file's values, loaded once. Kept apart so the load order is the class-load order of this holder. */
    private static final class FileValues {

        private static final Properties VALUES = load();

        private FileValues() {
        }

        static String get(String key) {
            return VALUES.getProperty(key);
        }

        private static Properties load() {
            Properties properties = new Properties();
            Path directory = Paths.get(System.getProperty("user.dir", "."));
            Path[] candidates = {
                    directory.resolve("celtium-harness.properties"),
                    directory.resolve("config").resolve("celtium-harness.properties"),
            };
            for (Path candidate : candidates) {
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }
                try (InputStream input = Files.newInputStream(candidate)) {
                    properties.load(input);
                } catch (IOException ignored) {
                    // An unreadable harness file must not take the game down with it; the profile applies.
                }
                break;
            }
            return properties;
        }
    }
}
