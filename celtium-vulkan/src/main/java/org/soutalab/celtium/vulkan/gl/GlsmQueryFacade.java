package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-Q-1 (census Q-1..Q-7): the query facade GlStateQueries (985 lines,
 * stub-direct reads) is re-pointed at at merge time.
 * <p>
 * Two halves. The constant half is the (c)-specification the superset proof fixes: the
 * answers celtium advertises today become the specification, carried here as one table -
 * vendor "Celtium", version "3.0", empty extensions, GL_NO_ERROR, framebuffer complete,
 * ALREADY_SIGNALED, and the 16384 maximum texture size the splash route also answers
 * (census I-13). The recorded half reads the stage-3 owners (enables, viewport, names,
 * buffers, attachments) instead of the stub's private fields, so a glGet answer and the
 * state a draw consumed can no longer come from different records.
 * <p>
 * The driver-derived half (Q-3, GL_RENDERER) is not state: the facade exposes the slot
 * the backend fills from VulkanContext.driverSummary, defaulting to "unavailable"
 * exactly as today.
 */
public final class GlsmQueryFacade {
    // ---- (c) constant table: today's answers, fixed as specification ----
    public static final String VENDOR = "Celtium";
    public static final String VERSION = "3.0";
    public static final String EXTENSIONS = "";
    public static final String UNAVAILABLE = "unavailable";
    public static final int NO_ERROR = 0;
    public static final int FRAMEBUFFER_COMPLETE = 0x8CD5;
    public static final int ALREADY_SIGNALED = 0x911A;
    public static final int MAX_TEXTURE_SIZE = 16384;
    /** The empty info log the non-program objects answer (Q-5's (c) half). */
    public static final String EMPTY_INFO_LOG = "";

    private static final GlsmQueryFacade CURRENT = new GlsmQueryFacade();

    public static GlsmQueryFacade current() { return CURRENT; }

    private volatile String renderer = UNAVAILABLE;

    private GlsmQueryFacade() {
    }

    /** Q-3: the backend publishes the driver summary here; not guest-visible state. */
    public void renderer(String summary) { this.renderer = summary == null ? UNAVAILABLE : summary; }

    public String renderer() { return this.renderer; }

    // ---- recorded answers, read from the stage-3 owners ----
    public boolean isEnabled(int capability) { return GlsmEnableState.current().enabled(capability); }

    /** GL_VIEWPORT as {x, y, width, height}. */
    public void viewport(int[] out) {
        GlsmViewportState viewport = GlsmViewportState.current();
        out[0] = viewport.x(); out[1] = viewport.y();
        out[2] = viewport.width(); out[3] = viewport.height();
    }

    public boolean isName(int name) { return GlsmNameLedger.current().isLive(name); }

    public int boundBuffer(int target) { return GlsmBufferState.current().bound(target); }

    public int currentProgram() { return GlsmShaderLedger.current().currentProgram(); }

    public int boundVertexArray() { return GlsmClientArrayState.current().boundVertexArray(); }

    public int colorAttachmentTexture(int framebuffer) {
        return GlsmFramebufferAttachments.current().colorTexture(framebuffer);
    }
}
