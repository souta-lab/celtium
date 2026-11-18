package org.soutalab.celtium.patch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The persistent patch ledger: which patch groups are switched off or quarantined, read from
 * {@code config/celtium-patches.cfg} plus the {@code -Dceltium.patch.<group>=off} kill switches. This is the
 * decision half of the item 356 quarantine design (step 356-B); the gate that acts on it is {@link PatchGate},
 * and the caller that announces it is the mixin plugin in {@code forge122}.
 * <p>
 * The class is deliberately pure: {@code java.util} and {@code java.nio.file} only. It runs while Mixin is
 * preparing its configs, before the game's own configuration machinery exists, and {@code celtium-vulkan} has
 * no launchwrapper on its classpath - so the {@code Launch.blackboard} hand-off belongs to the plugin, which
 * publishes {@link #skipped()} under {@link #SKIPPED_BLACKBOARD_KEY}. No class here loads a mixin class, a
 * config class or a logging framework.
 * <p>
 * Decision order for one group (the design's section 4.3): an essential group is always applied; then the kill
 * switch {@code -Dceltium.patch.<group>=off}; then {@code off.<group>=true} in the file; then
 * {@code quarantine.<group>=<reason>} in the file; otherwise the group is applied. A switch aimed at an
 * essential group, and a key naming no group, are not silently swallowed: they are reported through
 * {@link #ignoredLines()} so a typo or a misunderstood switch is visible in the log.
 * <p>
 * Small decisions recorded here, because the file is the user's repair surface (they delete one line to retry
 * a quarantined group):
 * <ul>
 *   <li>The file is {@code Paths.get("config", ...)} relative to the working directory - the same convention
 *   as {@code SodiumGameOptions} ({@code config/embeddium-options.json}) and MixinBooter's own
 *   {@code config/mixinbooter.cfg}. {@code Launch.minecraftHome} is not used: it would split the product's
 *   persistent state across two directories and is null without {@code --gameDir}.</li>
 *   <li>{@code Properties.store} escapes a {@code :} inside a value as {@code \:}; {@code Properties.load}
 *   restores it, so a round trip is exact. The remedy line in every skip announcement names the whole
 *   {@code quarantine.<group>} line to delete, which is unaffected by the escaping inside its value.</li>
 *   <li>Two processes writing at once are not merged: the write is a whole-file replace through a sibling
 *   temporary and {@code ATOMIC_MOVE} (the {@code VulkanContext.savePipelineCache} shape), so a reader never
 *   sees a torn file, but the last writer wins. Simultaneous launches are outside the supported shape.</li>
 *   <li>A quarantine is never cleared automatically (the stage gate's rule, item 327): only deleting the line
 *   retries the group. {@code -D} switches never write the file.</li>
 * </ul>
 */
public final class PatchLedger {

    /** The ledger file's name, below {@code config/}. */
    public static final String FILE_NAME = "celtium-patches.cfg";

    /** Prefix of the per-group kill switches: {@code celtium.patch.<group>}. */
    public static final String SWITCH_PREFIX = "celtium.patch.";

    /** The property that relocates the ledger file: {@code celtium.patch.file}. */
    public static final String FILE_PROPERTY = "celtium.patch.file";

    /** The property that turns on the per-mixin registration census: {@code celtium.patch.census}. */
    public static final String CENSUS_PROPERTY = "celtium.patch.census";

    /** The blackboard key the plugin publishes {@link #skipped()} under, for a future screen to read. */
    public static final String SKIPPED_BLACKBOARD_KEY = "celtium.patches.skipped";

    /** Why a group is not applied. The first matching source in the decision order wins. */
    public enum SkipKind {
        /** The user passed {@code -Dceltium.patch.<group>=off} for this launch only. */
        KILL_SWITCH,
        /** The file carries {@code off.<group>=true}. */
        OFF_FILE,
        /** The file carries {@code quarantine.<group>=<reason>} from an earlier failed launch. */
        QUARANTINE
    }

    /** One skipped group: its kind and, for a quarantine, the recorded reason. */
    public static final class Skip {
        private final SkipKind kind;
        private final String reason;

        private Skip(SkipKind kind, String reason) {
            this.kind = kind;
            this.reason = reason;
        }

        /** {@return why this group is skipped}. */
        public SkipKind kind() {
            return this.kind;
        }

        /** {@return the recorded quarantine reason, or an empty string for the other kinds}. */
        public String reason() {
            return this.reason;
        }
    }

    private final Path file;
    private final Properties fileProperties;
    private final Map<String, String> switches;
    private final boolean census;
    private final boolean unreadable;
    private final String unreadableError;
    private final Map<String, Skip> skips = new LinkedHashMap<String, Skip>();
    private final List<String> ignored = new ArrayList<String>();

    private PatchLedger(Path file, Properties fileProperties, Map<String, String> switches, boolean census,
                        boolean unreadable, String unreadableError) {
        this.file = file;
        this.fileProperties = fileProperties;
        this.switches = switches;
        this.census = census;
        this.unreadable = unreadable;
        this.unreadableError = unreadableError;
        decide();
    }

    /**
     * {@return the ledger for this launch: the file (or its {@code -Dceltium.patch.file} replacement) read once,
     * with the kill switches and the census flag snapshotted from the system properties}
     * <p>
     * A missing file is a first launch, not an error: every group is on and nothing is reported. A file that
     * exists but cannot be read also leaves every group on, and says so in the table line.
     */
    public static PatchLedger load() {
        String relocated = System.getProperty(FILE_PROPERTY);
        Path file = relocated == null || relocated.trim().isEmpty()
                ? Paths.get("config", FILE_NAME)
                : Paths.get(relocated.trim());
        boolean census = Boolean.getBoolean(CENSUS_PROPERTY);
        Map<String, String> switches = new LinkedHashMap<String, String>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(SWITCH_PREFIX)) {
                switches.put(name, System.getProperty(name));
            }
        }
        return load(file, switches, census);
    }

    /** Reads {@code file} (a missing file is empty) and decides; the seam the tests use. */
    static PatchLedger load(Path file, Map<String, String> switches, boolean census) {
        Properties properties = new Properties();
        boolean unreadable = false;
        String error = "";
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException failure) {
                unreadable = true;
                error = describe(failure);
                properties = new Properties();
            }
        } else if (Files.exists(file)) {
            unreadable = true;
            error = "not a regular file";
        }
        return new PatchLedger(file, properties, switches, census, unreadable, error);
    }

    /** Decides from already-read state; the seam the decision tests use. */
    static PatchLedger of(Path file, Properties fileProperties, Map<String, String> switches, boolean census) {
        return new PatchLedger(file, fileProperties, switches, census, false, "");
    }

    private void decide() {
        for (String group : PatchGroups.groups()) {
            boolean killSwitch = isOffValue(this.switches.get(SWITCH_PREFIX + group));
            String offValue = this.fileProperties.getProperty("off." + group);
            boolean offFile = offValue != null && "true".equals(offValue.trim().toLowerCase(Locale.ROOT));
            String quarantine = this.fileProperties.getProperty("quarantine." + group);
            boolean quarantined = quarantine != null;
            if (PatchGroups.isEssential(group)) {
                if (killSwitch) {
                    this.ignored.add("CELTIUM_PATCH_IGNORED group=" + group
                            + " why=essential switch=-D" + SWITCH_PREFIX + group + "=off");
                }
                if (offFile) {
                    this.ignored.add("CELTIUM_PATCH_IGNORED group=" + group + " why=essential key=off." + group);
                }
                if (quarantined) {
                    this.ignored.add("CELTIUM_PATCH_IGNORED group=" + group
                            + " why=essential key=quarantine." + group);
                }
                continue;
            }
            if (killSwitch) {
                this.skips.put(group, new Skip(SkipKind.KILL_SWITCH, ""));
            } else if (offFile) {
                this.skips.put(group, new Skip(SkipKind.OFF_FILE, ""));
            } else if (quarantined) {
                this.skips.put(group, new Skip(SkipKind.QUARANTINE, oneLine(quarantine.trim())));
            }
        }
        for (String key : this.fileProperties.stringPropertyNames()) {
            String group = key.startsWith("off.") ? key.substring("off.".length())
                    : key.startsWith("quarantine.") ? key.substring("quarantine.".length()) : null;
            if (group != null && !PatchGroups.isKnown(group)) {
                this.ignored.add("CELTIUM_PATCH_IGNORED key=" + key + " why=unknown-group");
            }
        }
        for (String name : this.switches.keySet()) {
            String group = name.substring(SWITCH_PREFIX.length());
            if (!PatchGroups.isKnown(group) && !FILE_PROPERTY.equals(name) && !CENSUS_PROPERTY.equals(name)) {
                this.ignored.add("CELTIUM_PATCH_IGNORED key=" + name + " why=unknown-group");
            }
        }
        Collections.sort(this.ignored);
    }

    /** {@return true when the group is skipped by this ledger; essential groups never are}. */
    public boolean isSkipped(String groupId) {
        return this.skips.containsKey(groupId);
    }

    /** {@return the skip recorded for the group, or null when the group is applied}. */
    public Skip skip(String groupId) {
        return this.skips.get(groupId);
    }

    /** {@return the skipped group ids, in table order}. */
    public List<String> skippedGroups() {
        return Collections.unmodifiableList(new ArrayList<String>(this.skips.keySet()));
    }

    /**
     * {@return the skipped groups as group -&gt; short reason, in table order; this is the map the plugin
     * publishes on the blackboard for a future screen}
     */
    public Map<String, String> skipped() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Skip> entry : this.skips.entrySet()) {
            Skip skip = entry.getValue();
            out.put(entry.getKey(), skip.kind() == SkipKind.QUARANTINE
                    ? "quarantined: " + skip.reason()
                    : skip.kind() == SkipKind.KILL_SWITCH ? "kill-switch" : "off-file");
        }
        return Collections.unmodifiableMap(out);
    }

    /** {@return whether the registration census ({@code -Dceltium.patch.census=true}) is on}. */
    public boolean census() {
        return this.census;
    }

    /** {@return whether the ledger file existed but could not be read (every group is on, as on a first launch)}. */
    public boolean unreadable() {
        return this.unreadable;
    }

    /** {@return the file this ledger was read from (and {@link #recordQuarantine} writes to)}. */
    public Path file() {
        return this.file;
    }

    /**
     * {@return the announcement line for one skipped group, naming the remedy; the switch spelling is built
     * here, not by the caller, because the plugin that prints this line may not carry the property prefix}
     */
    public String skippedLine(String groupId) {
        Skip skip = this.skips.get(groupId);
        if (skip == null) {
            return "";
        }
        switch (skip.kind()) {
            case KILL_SWITCH:
                return "CELTIUM_PATCH_SKIPPED group=" + groupId + " why=kill-switch switch=-D"
                        + SWITCH_PREFIX + groupId + "=off";
            case OFF_FILE:
                return "CELTIUM_PATCH_SKIPPED group=" + groupId + " why=off-file key=off." + groupId
                        + " remedy=delete off." + groupId + " from config/" + FILE_NAME + " to retry";
            default:
                return "CELTIUM_PATCH_SKIPPED group=" + groupId + " why=quarantined reason=" + skip.reason()
                        + " remedy=delete quarantine." + groupId + " from config/" + FILE_NAME + " to retry";
        }
    }

    /** {@return the lines for switches that named an essential group or no group at all, sorted for stability}. */
    public List<String> ignoredLines() {
        return Collections.unmodifiableList(new ArrayList<String>(this.ignored));
    }

    /**
     * {@return the one-line table summary for a launch: group counts by decision and the mixin counts the gate
     * actually produced, so a leg can read "nothing was filtered" off a single line}
     */
    public String tableLine(int discovered, int registered) {
        int off = 0;
        int quarantined = 0;
        for (Skip skip : this.skips.values()) {
            if (skip.kind() == SkipKind.QUARANTINE) {
                quarantined++;
            } else {
                off++;
            }
        }
        int essential = 0;
        for (String group : PatchGroups.groups()) {
            if (PatchGroups.isEssential(group)) {
                essential++;
            }
        }
        StringBuilder out = new StringBuilder("CELTIUM_PATCH_TABLE groups=").append(PatchGroups.groups().size())
                .append(" essential=").append(essential)
                .append(" quarantinable=").append(PatchGroups.groups().size() - essential)
                .append(" off=").append(off)
                .append(" quarantined=").append(quarantined)
                .append(" mixins=").append(registered).append('/').append(discovered);
        if (this.unreadable) {
            out.append(" ledger=").append(this.file).append(" status=unreadable error=").append(this.unreadableError);
        }
        return out.toString();
    }

    /**
     * Records a group failure into the ledger file so the next launch skips the group. This is step 356-C's
     * write path, built here and called from nowhere yet.
     *
     * @return true when the line was written; false - never an exception - when the group may not be
     * quarantined (essential or unknown) or the file could not be written, in which case the next launch
     * will fail the same way and the caller says so
     */
    public boolean recordQuarantine(String groupId, String reason) {
        if (!PatchGroups.isKnown(groupId) || PatchGroups.isEssential(groupId)) {
            return false;
        }
        try {
            Properties properties = new Properties();
            if (Files.isRegularFile(this.file)) {
                try (InputStream in = Files.newInputStream(this.file)) {
                    properties.load(in);
                }
            }
            properties.setProperty("quarantine." + groupId, oneLine(reason));
            Path parent = this.file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                properties.store(out, "celtium patch ledger - delete a quarantine.<group> line to retry that group");
            }
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /**
     * {@return the one-line quarantine reason for a failure: phase, mixin and the failure's first line, with
     * line breaks folded away so the value stays one deletable line in the file}
     */
    public static String quarantineReason(String phase, String mixinSimpleName, Throwable error) {
        String detail = error == null ? "none" : error.getClass().getSimpleName() + ": " + oneLine(messageOf(error));
        String reason = oneLine(phase + " " + mixinSimpleName + ": " + detail);
        return reason.length() > 240 ? reason.substring(0, 240) : reason;
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null ? "" : message;
    }

    private static String describe(Throwable failure) {
        return oneLine(failure.getClass().getSimpleName() + ": " + messageOf(failure));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static boolean isOffValue(String value) {
        if (value == null) {
            return false;
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        return "off".equals(lowered) || "false".equals(lowered);
    }
}
