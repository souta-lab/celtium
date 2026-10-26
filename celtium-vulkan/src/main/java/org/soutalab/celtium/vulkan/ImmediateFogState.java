package org.soutalab.celtium.vulkan;

/**
 * Fixed-function fog for the immediate path, mirrored from {@code GlStateManager} by {@code GlStateManagerFogMixin}.
 * <p>
 * The immediate path had no fog of any kind, so every batch vanilla blends into the horizon - the sky dome, the
 * horizon plane, the water surface, the clouds - arrived as its own flat colour. The state is captured per batch
 * ({@link #snapshot()}, taken where the other per-group state is taken) rather than read at flush time, because
 * {@code RenderGlobal.renderSky} turns fog on for the dome and off again for the sun and the moon inside the same pass.
 * The backend writes the captured values into the push block at 128..207; this class owns the state and its selector.
 */
final class ImmediateFogState {
    /**
     * The kill switch, so the fog can be measured as one flag apart on one jar: with this false the family is mirrored
     * and the push block is still written, but its selector says "no fog" - exactly the picture the path produced
     * before the family existed.
     */
    static final boolean IMMEDIATE_FOG =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.immediateFog", "true"));
    /**
     * Which distance the fog coordinate uses: {@code radial} (the distance from the eye), {@code planar} (the
     * eye-space depth) or {@code auto} (whichever the game asked for with {@code GL_NV_fog_distance}).
     * <p>
     * The default is radial rather than auto because the GL-less leg cannot answer the extension query - the stub
     * returns an empty extension string, so {@code setupFog} never sets {@code GL_EYE_RADIAL_NV} there while the same
     * game code does on a driver that has the extension. Radial is also what the terrain path already uses for its own
     * fog (sodium's {@code getFragDistance} is the camera distance) and that path is accepted against the GL bridge.
     */
    static final String DISTANCE =
            System.getProperty("celtium.vulkan.immediateFogDistance", "radial").trim().toLowerCase();
    /** {@link #DISTANCE} resolved once: the selector ran two String equals per fog-enabled group. */
    private static final boolean DISTANCE_PLANAR = "planar".equals(DISTANCE);
    private static final boolean DISTANCE_AUTO = "auto".equals(DISTANCE);
    /** Bounded, like the other per-run diagnostics: the fog state changes a handful of times per frame. */
    private static final int LOG_LIMIT = Integer.getInteger("celtium.vulkan.immediateFogLog", 8).intValue();

    boolean enabled;
    /** The GL fog mode: 9729 LINEAR, 2048 EXP, 2049 EXP2 - the values {@code GlStateManager.FogState} carries. */
    int glMode = 2048;
    float density = 1.0f;
    float start;
    float end = 1.0f;
    float red;
    float green;
    float blue;
    /** Set when the game asks for {@code GL_EYE_RADIAL_NV}; the GL-less stub's empty extension list keeps it false. */
    boolean radialRequested;
    /**
     * Set the first time any fog state arrives from {@code GlStateManager}. This is what {@link #available()} answers
     * with: a mixin that did not apply leaves it false, and a caller that would refuse a fogged draw on the grounds that
     * the path has no fog must keep refusing in that case rather than draw the raw colour.
     */
    boolean mirrored;
    /** Groups drawn with fog on: the number the fog line reports beside the state. */
    int groups;

    private int logs;
    private int logChecks;
    /** The last line {@link #log} printed, so a change is logged rather than every frame's repeat. */
    private String lastLogged = "";
    private int periodicLines;
    private long periodicNanos;
    /** Scratch for {@link #movedFrom}: eight floats reused instead of allocated ~280 times a frame. */
    private final float[] compareScratch = new float[8];

    /** {@return whether the game's own fog state has reached this mirror at least once, with the feature on}. */
    boolean available() {
        return IMMEDIATE_FOG && this.mirrored;
    }

    /**
     * The shader's fog selector: {@code mode + 8 * shape}, both small integers so the float is exact.
     * <p>
     * The mode numbering is the terrain block's ({@code 0 none, 1 exp2, 2 exp, 3 linear}) so the two fog paths in this
     * renderer can be read side by side, and the shape numbering is {@code FogService}'s ({@code 0 spherical}, the
     * radial distance, and {@code 2 planar}). {@code 1} (cylindrical) is never produced here: the fixed pipeline has no
     * such mode, it is sodium's.
     */
    int selector() {
        if (!IMMEDIATE_FOG || !this.enabled) {
            return 0;
        }
        int mode;
        if (this.glMode == 9729) {
            mode = 3; // GL_LINEAR
        } else if (this.glMode == 2049) {
            mode = 1; // GL_EXP2
        } else {
            mode = 2; // GL_EXP, which is also GlStateManager.FogState's default
        }
        boolean planar;
        if (DISTANCE_PLANAR) {
            planar = true;
        } else if (DISTANCE_AUTO) {
            planar = !this.radialRequested;
        } else {
            planar = false; // radial
        }
        return mode + (planar ? 16 : 0); // shape 2 = planar, and 2 << 3 = 16
    }

    /**
     * The per-batch fog payload: eight floats {@code {selector, density, start, end, r, g, b, weight}}, or {@code null}
     * when fog is off for this batch.
     */
    float[] snapshot() {
        int selector = selector();
        if (selector == 0) {
            return null;
        }
        return new float[] {
                selector, this.density, this.start, this.end, this.red, this.green, this.blue,
                // The GL fog blend uses the fog colour's RGB only. The weight is 1.0 in vanilla and is kept as its own
                // field so a mod's non-opaque fog colour cannot silently change the blend here.
                1.0f,
        };
    }

    /** {@return whether the live state differs from a group's captured {@link #snapshot()}}, without allocating. */
    boolean movedFrom(float[] captured) {
        int selector = selector();
        if (selector == 0 || captured == null) {
            return (selector == 0) != (captured == null);
        }
        float[] now = this.compareScratch;
        now[0] = selector;
        now[1] = this.density;
        now[2] = this.start;
        now[3] = this.end;
        now[4] = this.red;
        now[5] = this.green;
        now[6] = this.blue;
        now[7] = 1.0f;
        return !java.util.Arrays.equals(now, captured);
    }

    /**
     * Whether a fog line may still be written. Asked by the setters before they build their argument: the record only
     * advances when the line changes, so in a scene whose fog never changes it never reached its limit and every fog
     * call (several a frame) built two strings for nothing. The checks themselves are bounded too.
     */
    boolean logOpen() {
        return IMMEDIATE_FOG && this.logs < LOG_LIMIT && this.logChecks++ < 4096;
    }

    /** A bounded record of what the game set, so a run with no fog in it can be told from a run with black fog. */
    void log(String what, long frameId) {
        if (!IMMEDIATE_FOG || this.logs >= LOG_LIMIT) {
            return;
        }
        String line = what + " enabled=" + this.enabled + " glMode=" + this.glMode
                + " density=" + this.density + " start=" + this.start + " end=" + this.end
                + " colour=" + this.red + "," + this.green + "," + this.blue
                + " radialRequested=" + this.radialRequested + " distance=" + DISTANCE
                + " selector=" + selector();
        if (line.equals(this.lastLogged)) {
            return;
        }
        this.lastLogged = line;
        this.logs++;
        System.out.println("[Celtium] CELTIUM_IMMEDIATE_FOG frame=" + frameId + " " + line);
    }

    /**
     * The state the world frames draw with, every five seconds and at most 24 lines a run: the bounded record above
     * only sees the first frames (loading screens), and a world whose distant terrain fades to black instead of to the
     * sky needs the later state.
     */
    void tracePeriodically(long frameId) {
        long now = System.nanoTime();
        if (frameId > 0 && now - this.periodicNanos > 5_000_000_000L && this.periodicLines < 24) {
            this.periodicNanos = now;
            this.periodicLines++;
            System.out.println("[Celtium] CELTIUM_FOG_STATE frame=" + frameId + " enabled=" + this.enabled
                    + " glMode=" + this.glMode + " start=" + this.start + " end=" + this.end
                    + " density=" + this.density + " colour=" + this.red + "," + this.green + "," + this.blue);
        }
    }
}
