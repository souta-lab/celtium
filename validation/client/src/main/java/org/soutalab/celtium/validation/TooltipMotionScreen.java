package org.soutalab.celtium.validation;

import net.minecraft.client.gui.GuiScreen;

import java.util.Arrays;
import java.util.List;

/**
 * A screen that moves a vanilla tooltip between two fixed positions on <em>consecutive frames</em>.
 *
 * <p>Why this exists. The reported tooltip afterimage is a motion-only question: at rest the game's own picture and the
 * presented one agree pixel for pixel (leg {@code shot-test6}: zero differing pixels), and no frame reaches its
 * boundary without a clear ({@code framesNoClear=0}), so neither a mixed present nor an uncleared image can produce the
 * ghost. What is left is glyphs that were written for one frame being drawn into the next one at the position they had
 * then - the retained font run, whose frame-boundary drop was committed on 2026-11-01 and has never been measured in
 * motion. A still tooltip cannot answer that question: it has only one position to be wrong at.
 *
 * <p>How it answers it. The tooltip alternates between two positions that do not overlap, one per frame, on an opaque
 * black panel. Every frame therefore shows exactly <em>one</em> tooltip box if nothing leaks, and two if the previous
 * frame's glyphs (or its box) were drawn again. The panel is black by construction, so "ink" is any pixel above the
 * black threshold and the measurement needs no reference picture: the ink's bounding box has to be one tooltip tall,
 * not the distance between the two positions. That is why the picture is decisive even though no Celeritas arm can
 * dump a frame (its dump hook is this renderer's own).
 *
 * <p>Independent of the GUI scale: the layout is a fraction of {@code width}/{@code height}, and
 * {@link #describeLayout()} reports the resolved numbers, so the checker multiplies by the picture's own scale instead
 * of assuming one. Green and cyan markers sit at the panel's own corners, so a mispositioned or absent panel is a
 * reported failure rather than "no ink, therefore no ghost".
 *
 * <p>Fixture-only: nothing here is part of the renderer. It is opened by
 * {@code -Dceltium.validation.screen=tooltipmotion}, the same named-screen switch the language, resource-pack, main
 * menu and creative scenes use, and it requests its own presented dumps through
 * {@link #setDumpRequester(DumpRequester)} so a leg does not have to know which frame parity it caught.
 *
 * <p><b>Two arms.</b> The same drawing commands run twice: straight onto the main target, and through a framebuffer
 * this screen creates with raw {@code glGenFramebuffers}/{@code glFramebufferTexture2D} - the way a mod builds its own
 * render target, and the case the retained-glyph-run drop was written for. The framebuffer arm draws the panel, its
 * markers and the tooltip into the attachment, then composites the panel's rectangle back onto the main target
 * unblended and one texel to one pixel, so the two arms' pictures are the same pixels unless the renderer lost or
 * misplaced something on the way through the mod target. Two consecutive frames per arm, so each arm shows both
 * positions and the second frame of an arm is the one where a run retained for the first frame's target would be
 * offered again at the first frame's position.
 */
public final class TooltipMotionScreen extends GuiScreen {

    /** A callback into the fixture's presented-image dump request, so the frames this screen draws are the frames saved. */
    public interface DumpRequester {
        void requestPresentedDump(String stage);
    }

    /**
     * The lines drawn in the tooltip. Three short ASCII lines: the box is then about 44 GUI units tall, which is
     * small enough that the two positions' boxes are separated by a band that must stay empty.
     */
    private static final List<String> LINES = Arrays.asList(
            "celtium tooltip motion probe",
            "line two draws its own glyphs",
            "line three is here for height");

    /** Which draw count the first dump is requested at: past the screen's first frames, still early in the leg. */
    private static final int FIRST_DUMP_FRAME = 40;
    /**
     * How many consecutive frames are dumped. Two arms times two alternating positions: frames 40-41 draw straight
     * onto the main target, frames 42-43 draw the same commands through the mod framebuffer, and each pair covers both
     * tooltip positions.
     */
    private static final int DUMP_FRAMES = 4;

    private DumpRequester dumps;
    private int frames;
    private int requested;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;
    /** The mod framebuffer the second arm draws through, its colour attachment, and the size they were made at. */
    private int fbo = -1;
    private int fboTexture = -1;
    private int fboWidth = -1;
    private int fboHeight = -1;
    private String fboStatus = "not created";
    private int fboFrames;

    /** {@return the draw count so far}, so the report can say the screen was painted rather than opened. */
    public int framesDrawn() {
        return this.frames;
    }

    public void setDumpRequester(DumpRequester requester) {
        this.dumps = requester;
    }

    /**
     * {@return the resolved layout, in GUI units, as {@code width x height panel=(l,t,r,b) slots=A,B textX=x}}.
     *
     * <p>Reported rather than assumed: the checker turns these into picture pixels with the picture's own scale, and
     * fails when the markers it finds are not at these coordinates. An unreported constant is how a gate and the
     * picture drift apart without anyone noticing.
     */
    public String describeLayout() {
        int[] panel = celtium$panel();
        return "gui=" + this.width + "x" + this.height
                + " panel=" + panel[0] + "," + panel[1] + "," + panel[2] + "," + panel[3]
                + " slots=" + celtium$slotA(panel) + "," + celtium$slotB(panel)
                + " textX=" + celtium$textX(panel)
                + " frames=" + this.frames + " dumps=" + this.requested
                + " fbo=" + this.fboStatus + " fboFrames=" + this.fboFrames;
    }

    /** The panel's GUI rectangle: a sixth of the screen in from each edge, so it is fully on screen at any scale. */
    private int[] celtium$panel() {
        int width = Math.max(1, this.width);
        int height = Math.max(1, this.height);
        int left = width / 6;
        int top = height / 6;
        int right = width - width / 6;
        int bottom = height - height / 6;
        return new int[] {left, top, right, bottom};
    }

    /**
     * The upper tooltip origin: an eighth of the panel's height below its top edge.
     * <p>Vanilla draws the box sixteen GUI units <em>above</em> the origin it is given, so an origin too close to the
     * panel's top edge would put the box's border outside the black panel and over the world - measured on leg
     * {@code r25g-tooltip}, where the upper box's top border sat at y=102 with the panel starting at 120 and the sky
     * above it was then counted as the other band's ink. The box has to be wholly inside the panel for "black panel,
     * therefore ink" to mean anything.
     */
    private static int celtium$slotA(int[] panel) {
        return panel[1] + Math.max(20, (panel[3] - panel[1]) / 8);
    }

    /**
     * The lower tooltip origin: a quarter of the panel's height above its bottom edge.
     * <p>The box ends twenty-eight units below the origin, so this keeps its bottom border inside the panel as well.
     */
    private static int celtium$slotB(int[] panel) {
        return panel[3] - Math.max(56, (panel[3] - panel[1]) / 4);
    }

    /** The tooltip's own mouse X: the panel's left edge plus a twentieth of the screen. */
    private static int celtium$textX(int[] panel) {
        return panel[0] + Math.max(8, (panel[2] - panel[0]) / 20);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] panel = celtium$panel();
        int frame = this.frames++;
        this.laidOutWidth = this.width;
        this.laidOutHeight = this.height;
        // Which arm this frame is: the same drawing commands, once straight onto the main target and once through a
        // framebuffer this screen creates with raw GL - the way a mod builds its own render target. Two consecutive
        // frames per arm, so each arm shows both tooltip positions and the second frame of an arm is the one where a
        // run retained for the first frame's target would be offered again at the first frame's position.
        boolean fboArm = (frame / 2) % 2 == 1;
        boolean throughFbo = fboArm && celtium$ensureFbo(this.mc.displayWidth, this.mc.displayHeight);
        if (throughFbo) {
            this.fboFrames++;
            org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, this.fbo);
            // Opaque black, so the composite can be drawn without blending and the picture is the same pixels the main
            // arm draws. A transparent clear would double-blend the tooltip's own alpha borders and make the two arms
            // differ for a reason that is not the renderer's.
            net.minecraft.client.renderer.GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            net.minecraft.client.renderer.GlStateManager.clear(
                    org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT);
        }
        if (this.dumps != null && frame >= FIRST_DUMP_FRAME && this.requested < DUMP_FRAMES) {
            this.requested++;
            // The stage carries the frame, the parity and the arm, so a picture says which position it should hold and
            // which target drew it without needing the report to be read first - and both parities are in the set
            // because the dumps are consecutive.
            this.dumps.requestPresentedDump("tooltipmotion-f" + frame + (frame % 2 == 0 ? "-a" : "-b")
                    + (throughFbo ? "-fbo" : "-main"));
        }
        celtium$drawPanelAndTooltip(panel, frame);
        if (throughFbo) {
            org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
            celtium$composite(panel);
        }
    }

    /**
     * The frame's own drawing, unchanged between the two arms: the panel, its markers and one vanilla tooltip.
     *
     * <p>Both arms run exactly this, so a difference between their pictures is the target and nothing else.
     */
    private void celtium$drawPanelAndTooltip(int[] panel, int frame) {
        // The panel: opaque black, the one background on which "any non-black pixel is ink" is true. Drawn first and
        // every frame, so a residue can only come from this frame's own drawing into a frame image that kept the last.
        drawRect(panel[0], panel[1], panel[2], panel[3], 0xFF000000);
        // Corner markers, two different colours: the checker finds the panel by them and fails if they are not where
        // describeLayout says. They are 4 GUI units, inside the panel's corners and away from both tooltip boxes.
        drawRect(panel[0], panel[1], panel[0] + 4, panel[1] + 4, 0xFF00FF00);
        drawRect(panel[2] - 4, panel[3] - 4, panel[2], panel[3], 0xFF00FFFF);
        // One tooltip, at one of two positions, alternating every frame. Both positions use the vanilla path
        // (GuiScreen.drawHoveringText -> Forge's GuiUtils), so the box, the border, the shadow and the glyph runs are
        // exactly the ones the reported defect was seen on.
        this.drawHoveringText(LINES, celtium$textX(panel), frame % 2 == 0 ? celtium$slotA(panel) : celtium$slotB(panel));
    }

    /**
     * Creates the mod framebuffer the second arm draws through, or reports why it could not.
     *
     * <p>Raw GL, not the game's {@code Framebuffer}: {@code glGenFramebuffers} + {@code glFramebufferTexture2D} is how a
     * mod's own render target is built, and it is the case the retained-glyph-run drop was written for. The attachment
     * is the window's own size, so the GUI projection and viewport are unchanged and the two arms draw at the same
     * pixels.
     */
    private boolean celtium$ensureFbo(int width, int height) {
        if (this.fbo != -1 && this.fboWidth == width && this.fboHeight == height) {
            return "complete".equals(this.fboStatus);
        }
        if (this.fbo != -1) {
            org.lwjgl.opengl.GL30C.glDeleteFramebuffers(this.fbo);
            org.lwjgl.opengl.GL11C.glDeleteTextures(this.fboTexture);
            this.fbo = -1;
            this.fboTexture = -1;
        }
        int texture = org.lwjgl.opengl.GL11C.glGenTextures();
        org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, texture);
        org.lwjgl.opengl.GL11C.glTexImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0,
                org.lwjgl.opengl.GL11C.GL_RGBA8, width, height, 0, org.lwjgl.opengl.GL11C.GL_RGBA,
                org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        int framebuffer = org.lwjgl.opengl.GL30C.glGenFramebuffers();
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, framebuffer);
        org.lwjgl.opengl.GL30C.glFramebufferTexture2D(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER,
                org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, texture, 0);
        int status = org.lwjgl.opengl.GL30C.glCheckFramebufferStatus(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER);
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
        this.fbo = framebuffer;
        this.fboTexture = texture;
        this.fboWidth = width;
        this.fboHeight = height;
        this.fboStatus = status == org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE
                ? "complete" : "incomplete 0x" + Integer.toHexString(status);
        return "complete".equals(this.fboStatus);
    }

    /**
     * Draws the framebuffer's panel region back onto the main target, unblended and one texel to one pixel.
     *
     * <p>Only the panel's rectangle is composited, so the rest of the frame keeps the world the game drew. The
     * framebuffer's origin is the bottom-left and the GUI's is the top-left, so the vertical texture coordinates are
     * flipped; the horizontal ones are the panel's own fraction of the GUI width, which is the same fraction of the
     * framebuffer because the attachment is the window's size.
     */
    private void celtium$composite(int[] panel) {
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.bindTexture(this.fboTexture);
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        float guiWidth = Math.max(1, this.width);
        float guiHeight = Math.max(1, this.height);
        float u0 = panel[0] / guiWidth;
        float u1 = panel[2] / guiWidth;
        float vTop = 1.0F - panel[1] / guiHeight;
        float vBottom = 1.0F - panel[3] / guiHeight;
        net.minecraft.client.renderer.BufferBuilder buffer =
                net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();
        buffer.begin(org.lwjgl.opengl.GL11C.GL_QUADS,
                net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        buffer.pos(panel[0], panel[3], 0.0D).tex(u0, vBottom).endVertex();
        buffer.pos(panel[2], panel[3], 0.0D).tex(u1, vBottom).endVertex();
        buffer.pos(panel[2], panel[1], 0.0D).tex(u1, vTop).endVertex();
        buffer.pos(panel[0], panel[1], 0.0D).tex(u0, vTop).endVertex();
        net.minecraft.client.renderer.Tessellator.getInstance().draw();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
    }

    /** {@return the mod framebuffer's creation status} - {@code complete} when the second arm really drew through it. */
    public String fboStatus() {
        return this.fboStatus;
    }

    /** {@return how many frames were drawn through the mod framebuffer}. */
    public int fboFrames() {
        return this.fboFrames;
    }

    /** {@return the GUI size this screen was last laid out at, or -1} - what the dumps were scaled from. */
    public int laidOutWidth() {
        return this.laidOutWidth;
    }

    public int laidOutHeight() {
        return this.laidOutHeight;
    }
}
