package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-ST-9 (census S-14, plus S-9's line width / point size): the owner of
 * the legacy immediate mode's current values - the ones a glBegin/glEnd block, a capture
 * and the glGet answers all read.
 * <p>
 * LegacyImmediateCapture (941 lines) stays the capture machinery; this owner holds the
 * current-value half it used to keep privately: begin/end openness and mode, the vertex
 * format in force, current texture coordinates and colour, the values saved while a
 * display list compiles, and line width / point size (which the stub kept as bare fields
 * with only a snapshot copy elsewhere). Defaults are GL's: colour (1,1,1,1), width 1.
 * <p>
 * M-9 (superset proof S-9 remainder): this owner also holds GL's initial light-model
 * ambient, the fallback {@code GL_LIGHT_MODEL_AMBIENT} answer when no backend mirror
 * exists. It has no writer: a guest's light-model ambient write goes to the backend
 * mirror ({@code VulkanGlState.setLightAmbient}), so the value here stays at GL's
 * initial (0.2, 0.2, 0.2, 1) for the owner's whole life, exactly as the stub's field did.
 */
public final class GlsmImmediateState {
    private static final GlsmImmediateState CURRENT = new GlsmImmediateState();

    public static GlsmImmediateState current() { return CURRENT; }

    private boolean inside;
    private int mode;
    private int vertexFormat;
    private float texU, texV;
    private float red = 1.0f, green = 1.0f, blue = 1.0f, alpha = 1.0f;
    private float lineWidth = 1.0f;
    private float pointSize = 1.0f;
    /** GL's initial light-model ambient (M-9); never written, see the class javadoc. */
    private final float[] lightAmbientDefault = {0.2f, 0.2f, 0.2f, 1.0f};
    /** Values saved while a display list compiles (the compile must not disturb the live ones). */
    private float savedRed, savedGreen, savedBlue, savedAlpha;
    private boolean hasSaved;
    private long version;

    private GlsmImmediateState() {
    }

    public void begin(int mode, int vertexFormat) {
        this.inside = true; this.mode = mode; this.vertexFormat = vertexFormat;
        this.version++;
    }

    public void end() { this.inside = false; this.version++; }

    public boolean inside() { return this.inside; }
    public int mode() { return this.mode; }
    public int vertexFormat() { return this.vertexFormat; }

    public void texCoord(float u, float v) { this.texU = u; this.texV = v; }
    public float texU() { return this.texU; }
    public float texV() { return this.texV; }

    public void color(float red, float green, float blue, float alpha) {
        this.red = red; this.green = green; this.blue = blue; this.alpha = alpha;
        this.version++;
    }

    public float red() { return this.red; }
    public float green() { return this.green; }
    public float blue() { return this.blue; }
    public float alpha() { return this.alpha; }

    public void lineWidth(float width) { this.lineWidth = width; this.version++; }
    public float lineWidth() { return this.lineWidth; }

    public void pointSize(float size) { this.pointSize = size; this.version++; }
    public float pointSize() { return this.pointSize; }

    /**
     * {@return GL's initial light-model ambient}, four floats, as the fallback
     * {@code GL_LIGHT_MODEL_AMBIENT} answer when no backend mirror exists. The array
     * is the owner's own and is never mutated; callers read it, as they already read
     * the backend mirror's array.
     */
    public float[] lightAmbientDefault() { return this.lightAmbientDefault; }

    public void saveForCompile() {
        this.savedRed = this.red; this.savedGreen = this.green;
        this.savedBlue = this.blue; this.savedAlpha = this.alpha;
        this.hasSaved = true;
    }

    public void restoreFromCompile() {
        if (!this.hasSaved) return;
        this.red = this.savedRed; this.green = this.savedGreen;
        this.blue = this.savedBlue; this.alpha = this.savedAlpha;
        this.hasSaved = false;
        this.version++;
    }

    public long version() { return this.version; }
}
