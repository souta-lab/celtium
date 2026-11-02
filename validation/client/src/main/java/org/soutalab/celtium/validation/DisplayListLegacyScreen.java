package org.soutalab.celtium.validation;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GLAllocation;

import org.lwjgl.opengl.GL11;

/**
 * A screen that compiles {@code glBegin}/{@code glEnd} geometry into a display list and draws it with {@code glCallList},
 * which is the shape GregTech's tool overlays are drawn in.
 *
 * <p>Why this exists. The renderer used to refuse a {@code glBegin} run that arrived inside a {@code glNewList} compile
 * ("reason=a display list is being compiled"), so the list was stored empty and every {@code glCallList} of it drew
 * nothing. That is not a hypothetical path in this pack: {@code gregtech/common/ToolEventHandlers.drawRotationMarker}
 * compiles its rotation marker exactly this way - read from the class file, {@code glNewList(4864=GL_COMPILE)},
 * {@code glBegin(GL_LINE_LOOP)}, 121 {@code glVertex3d}, {@code glEnd}, {@code glEndList} - and calls
 * {@code glCallList} every frame while a wrench is held. The reported symptom is "some custom rendering is invisible";
 * this scene makes that one code path measurable without a wrench, a block or a player.
 *
 * <p>What the picture has to show, and why each part is drawn the way it is:
 * <ul>
 *   <li><b>The geometry appears at all.</b> Two lists are compiled on the first frame: a filled {@code GL_QUADS} square
 *       (robust ink to count) and a {@code GL_LINE_LOOP} triangle, the primitive GregTech uses (the renderer's line
 *       pipeline is a separate path, so a square alone would not answer it).</li>
 *   <li><b>It is drawn where {@code glCallList} puts it, not where it was compiled.</b> Both lists are compiled at the
 *       origin with no transform, and each frame draws them under a {@code glPushMatrix}/{@code glTranslatef}. A renderer
 *       that drew the run at compile time would put the geometry at the screen's top-left corner regardless of the
 *       translation, and one that baked the matrix in would not move it between frames.</li>
 *   <li><b>It moves between two positions on consecutive frames.</b> The square alternates between an upper and a lower
 *       slot, so two consecutive dumps have to disagree; a stale copy of the previous frame's list draw would show both
 *       slots carrying ink.</li>
 *   <li><b>An attribute the list never set comes from the caller, not from the compile.</b> This is the half the
 *       first version of this scene got wrong and made a fixture out of: a display list compiles the
 *       <em>commands</em>, so a {@code glVertex} written inside it takes the colour current at {@code glCallList}
 *       unless the list carries a {@code glColor} of its own. Two lists are drawn in opposite slots every frame, both
 *       under {@code glColor4f(red)}: the <b>outer</b> list was compiled after a green was set <em>outside</em> it, so
 *       GL draws it <b>red</b>, and the <b>inner</b> list sets green <em>inside</em> the compile, so GL draws it
 *       <b>green</b>. A renderer that bakes the compile-time colour puts green in both slots, which the checker counts
 *       as a failure and names. (That is what Celtium did until now; the previous scene asserted the baked answer as
 *       if it were GL's, and its "0 red pixels" reading was the defect, not the control.)</li>
 *   <li><b>A same-frame control tells a list failure from an immediate-path failure.</b> A third quad is drawn directly
 *       with {@code glBegin}/{@code glEnd}, in yellow, under the same matrix and the same untextured state. If the
 *       yellow quad is in the picture and the compiled square is not, the list replay is at fault rather than the
 *       immediate path, the transform or the colour pipeline - and both are in one frame, so no cross-run comparison is
 *       needed to say so.</li>
 * </ul>
 *
 * <p>Two things about this scene were measured on earlier runs of it and are built in deliberately. <b>The quads are
 * wound the way vanilla's {@code Gui.drawRect} winds them.</b> The first version wound the other way, and the picture
 * showed the line loop with no square at all (leg {@code r26b}: 340 cyan pixels in the loop's band, 0 in either slot and
 * 0 in the direct control) - a back face under the overlay's y-down ortho, culled by GL, which is correct behaviour and
 * was the scene's mistake. <b>Texture is disabled around the geometry.</b> A list compiled with {@code GL_TEXTURE_2D}
 * enabled packs the runs as {@code POSITION_TEX_COLOR}; with no {@code glTexCoord} of its own the run then carries
 * whatever coordinate was current and samples whatever texture the GUI last bound, so "did the list replay" would be
 * entangled with which texel that is. Untextured, the vertex colour is the picture and the probe asks only its own
 * question.
 *
 * <p>Fixture-only: nothing here is part of the renderer. It is opened by
 * {@code -Dceltium.validation.screen=displaylist} and requests its own presented dumps through
 * {@link #setDumpRequester(DumpRequester)}, so the frames it draws are the frames saved.
 */
public final class DisplayListLegacyScreen extends GuiScreen {

    /** A callback into the fixture's presented-image dump request. */
    public interface DumpRequester {
        void requestPresentedDump(String stage);
    }

    /** Which draw count the first dump is requested at: past the screen's first frames, still early in the leg. */
    private static final int FIRST_DUMP_FRAME = 40;
    /** Two alternating positions need at least two consecutive frames to cover both. */
    private static final int DUMP_FRAMES = 2;
    /** The compiled square's half extent, in GUI units, and the line loop's radius. */
    private static final int SQUARE_HALF = 30;
    private static final int TRIANGLE_RADIUS = 24;
    /** The green set outside the outer list, which GL ignores in favour of the call-time colour. */
    private static final float OUTSIDE_RED = 0.0f;
    private static final float OUTSIDE_GREEN = 1.0f;
    private static final float OUTSIDE_BLUE = 0.0f;
    /** The green the inner list sets itself, which the replay has to reproduce. */
    private static final float INSIDE_RED = 0.0f;
    private static final float INSIDE_GREEN = 1.0f;
    private static final float INSIDE_BLUE = 0.0f;
    /** The colour both lists are called under. GL draws the outer list in it and the inner list is unaffected. */
    private static final float CALL_RED = 1.0f;
    private static final float CALL_GREEN = 0.0f;
    private static final float CALL_BLUE = 0.0f;
    /** The line loop's colour, set inside its own list so the cyan survives the red call. */
    private static final float LOOP_RED = 0.0f;
    private static final float LOOP_GREEN = 1.0f;
    private static final float LOOP_BLUE = 1.0f;

    private DumpRequester dumps;
    private int frames;
    private int requested;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    /** The three display lists, 0 until the first frame compiles them. */
    private int outerList;
    private int innerList;
    private int triangleList;
    /** How many times each list was compiled, so a scene that silently recompiled is visible. */
    private int compiles;
    private final StringBuilder dumpStages = new StringBuilder();

    public void setDumpRequester(DumpRequester requester) {
        this.dumps = requester;
    }

    /** {@return the draw count so far}, so the report can say the screen was painted rather than opened. */
    public int framesDrawn() {
        return this.frames;
    }

    public int compiles() {
        return this.compiles;
    }

    /**
     * {@return the resolved layout in GUI units}, as
     * {@code gui=WxH square=H slots=A,B triangle=(x,y,r) direct=(x,y,h) even/odd=(outer slot,inner slot)}.
     * <p>
     * Reported rather than assumed: the checker turns these into picture pixels with the picture's own scale, and fails
     * when the ink it finds is not at these coordinates. An unreported constant is how a gate and the picture drift
     * apart without anyone noticing. {@code even} and {@code odd} name which slot each list is drawn in on a frame of
     * that parity, so the checker never has to guess which square it is looking at.
     */
    public String describeLayout() {
        int[] panel = celtium$panel();
        return "gui=" + this.width + "x" + this.height
                + " panel=" + panel[0] + "," + panel[1] + "," + panel[2] + "," + panel[3]
                + " square=" + SQUARE_HALF
                + " slots=" + celtium$slotA(panel) + "," + celtium$slotB(panel)
                + " triangle=" + celtium$triangleX(panel) + "," + (panel[1] + panel[3]) / 2 + ","
                + TRIANGLE_RADIUS
                + " direct=" + celtium$directX(panel) + "," + (panel[1] + panel[3]) / 2 + "," + DIRECT_HALF
                + " even=" + celtium$slotA(panel) + "," + celtium$slotB(panel)
                + " odd=" + celtium$slotB(panel) + "," + celtium$slotA(panel)
                + " callColour=" + (int) (CALL_RED * 255) + "," + (int) (CALL_GREEN * 255) + ","
                + (int) (CALL_BLUE * 255)
                + " insideColour=" + (int) (INSIDE_RED * 255) + "," + (int) (INSIDE_GREEN * 255) + ","
                + (int) (INSIDE_BLUE * 255)
                + " frames=" + this.frames + " compiles=" + this.compiles + " dumps=" + this.requested
                + " stages=" + this.dumpStages;
    }

    /** The screen's panel: the whole screen, so nothing outside the scene can be mistaken for its geometry. */
    private int[] celtium$panel() {
        return new int[] {0, 0, Math.max(1, this.width), Math.max(1, this.height)};
    }

    /** The square's centre in the upper slot: a quarter of the way down, clear of the corner markers. */
    private static int celtium$slotA(int[] panel) {
        return panel[1] + (panel[3] - panel[1]) / 4;
    }

    /** The square's centre in the lower slot: three quarters of the way down. */
    private static int celtium$slotB(int[] panel) {
        return panel[1] + 3 * (panel[3] - panel[1]) / 4;
    }

    /** The display-list square's horizontal centre: the middle of the screen. */
    private static int celtium$centreX(int[] panel) {
        return (panel[0] + panel[2]) / 2;
    }

    /** The direct-immediate control's horizontal centre: a fifth of the way across, away from the lists. */
    private static int celtium$directX(int[] panel) {
        return panel[0] + (panel[2] - panel[0]) / 5;
    }

    /** The line loop's horizontal centre: four fifths of the way across, the other side of the square. */
    private static int celtium$triangleX(int[] panel) {
        return panel[0] + 4 * (panel[2] - panel[0]) / 5;
    }

    /** The direct immediate quad's half extent, in GUI units. */
    private static final int DIRECT_HALF = 20;
    /** The direct immediate quad's colour: yellow, which neither the list square nor the line loop uses. */
    private static final float DIRECT_RED = 1.0f;
    private static final float DIRECT_GREEN = 1.0f;

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Compiles the three lists once, at the origin, and only one of them gets its colour from outside the compile.
     * <p>
     * <b>The outer list</b> is compiled after green is set outside it, so the list carries no colour command at all: GL
     * draws its vertices in the colour current at {@code glCallList}, which is red. <b>The inner list</b> sets green
     * inside its compile, so that command is replayed first and the square is green whatever the caller set. Both are
     * called under the same red, in opposite slots of the same frame, so one picture separates the two rules.
     * <p>
     * The line loop sets cyan inside its own list for the same reason: it is drawn after the red call, and a list with
     * no colour of its own would otherwise be red too.
     * <p>
     * The compile is under {@code GL_COMPILE} and not {@code GL_COMPILE_AND_EXECUTE}: the point of the scene is that the
     * geometry is <em>not</em> drawn here, so a renderer that drew it now would paint the squares at the screen's
     * origin in this frame and the checker would see them there.
     */
    private void celtium$compileLists() {
        this.compiles++;
        GL11.glColor4f(OUTSIDE_RED, OUTSIDE_GREEN, OUTSIDE_BLUE, 1.0f);
        this.outerList = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(this.outerList, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        // Wound the way vanilla's Gui.drawRect winds a quad, which is the winding a GUI face is front-facing under
        // the overlay's y-down ortho. The other order is back-facing and is culled - measured on leg r26b, where a quad
        // in that order left no ink while the line loop in the same frame drew (lines are not culled), and the mistake
        // was the scene's, not the renderer's: GL culling back faces is the correct behaviour.
        GL11.glVertex2i(-SQUARE_HALF, -SQUARE_HALF);
        GL11.glVertex2i(-SQUARE_HALF, SQUARE_HALF);
        GL11.glVertex2i(SQUARE_HALF, SQUARE_HALF);
        GL11.glVertex2i(SQUARE_HALF, -SQUARE_HALF);
        GL11.glEnd();
        GL11.glEndList();

        this.innerList = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(this.innerList, GL11.GL_COMPILE);
        GL11.glColor4f(INSIDE_RED, INSIDE_GREEN, INSIDE_BLUE, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(-SQUARE_HALF, -SQUARE_HALF);
        GL11.glVertex2i(-SQUARE_HALF, SQUARE_HALF);
        GL11.glVertex2i(SQUARE_HALF, SQUARE_HALF);
        GL11.glVertex2i(SQUARE_HALF, -SQUARE_HALF);
        GL11.glEnd();
        GL11.glEndList();

        this.triangleList = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(this.triangleList, GL11.GL_COMPILE);
        GL11.glColor4f(LOOP_RED, LOOP_GREEN, LOOP_BLUE, 1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int corner = 0; corner < 3; corner++) {
            double angle = Math.PI / 2.0 + corner * 2.0 * Math.PI / 3.0;
            GL11.glVertex2d(Math.cos(angle) * TRIANGLE_RADIUS, Math.sin(angle) * TRIANGLE_RADIUS);
        }
        GL11.glEnd();
        GL11.glEndList();
        System.out.println("[Celtium] CELTIUM_DISPLAYLIST_COMPILED outer=" + this.outerList + " inner="
                + this.innerList + " triangle=" + this.triangleList
                + " (GL_COMPILE: nothing is drawn here; the outer list's green was set outside it and is not part of"
                + " the list, the inner list's green is)");
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int[] panel = celtium$panel();
        // A dark gradient, not a flat fill. The scene's own frame is the one the leg's picture gate measures, and a
        // two-colour frame reads as a blank picture to that gate's smoke floor (measured on the first run of this
        // scene: `presentedDistinctColours=3 is not above the smoke floor of 16`, which is the gate doing its job on a
        // frame that was mostly empty). A graded backdrop is what a real screen has and it lets the probe's own picture
        // clear the floor without inventing ink - the checker counts the geometry's colours specifically, never "any
        // non-black pixel".
        drawGradientRect(panel[0], panel[1], panel[2], panel[3], 0xFF101020, 0xFF202848);
        // Corner markers, two colours, so a mispositioned or absent panel is a reported failure rather than "no ink".
        drawRect(panel[0], panel[1], panel[0] + 4, panel[1] + 4, 0xFF00FF00);
        drawRect(panel[2] - 4, panel[3] - 4, panel[2], panel[3], 0xFF00FFFF);
        // Texture off around everything this screen draws itself. The state is what selects the vertex layout at
        // glBegin: with GL_TEXTURE_2D enabled the run is packed as POSITION_TEX_COLOR and the sampled texel multiplies
        // the colour - and a list compiled with no glTexCoord captures the current coordinate, which here is whatever
        // the GUI last left it at. The first run of this scene measured exactly that: the lists replayed (106 replay
        // submissions, 0 refusals) and left no ink at all, because the sampled texel was transparent. Untextured, the
        // vertex colour is the picture and the probe asks only its own question.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        try {
            if (this.outerList == 0) {
                celtium$compileLists();
            }
            int frame = this.frames++;
            this.laidOutWidth = this.width;
            this.laidOutHeight = this.height;
            if (this.dumps != null && frame >= FIRST_DUMP_FRAME && this.requested < DUMP_FRAMES) {
                this.requested++;
                this.dumpStages.append(this.dumpStages.length() == 0 ? "" : " ")
                        .append("displaylist-f").append(frame).append(frame % 2 == 0 ? "-a" : "-b");
                this.dumps.requestPresentedDump("displaylist-f" + frame + (frame % 2 == 0 ? "-a" : "-b"));
            }
            // The direct-immediate control: the same primitive, the same colour pipeline, the same matrix - drawn
            // without a display list. If this quad is in the picture and the compiled square is not, the fault is the
            // list replay rather than the immediate path or the transform, and the two are in one frame.
            GL11.glPushMatrix();
            GL11.glTranslatef(celtium$directX(panel), (panel[1] + panel[3]) / 2, 0.0f);
            GL11.glColor4f(DIRECT_RED, DIRECT_GREEN, 0.0f, 1.0f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2i(-DIRECT_HALF, -DIRECT_HALF);
            GL11.glVertex2i(-DIRECT_HALF, DIRECT_HALF);
            GL11.glVertex2i(DIRECT_HALF, DIRECT_HALF);
            GL11.glVertex2i(DIRECT_HALF, -DIRECT_HALF);
            GL11.glEnd();
            GL11.glPopMatrix();
            // Both squares are compiled at the origin and drawn under a translation that alternates every frame, in
            // opposite slots of the same frame. The call sets red for both.
            boolean even = frame % 2 == 0;
            int outerSlot = even ? celtium$slotA(panel) : celtium$slotB(panel);
            int innerSlot = even ? celtium$slotB(panel) : celtium$slotA(panel);
            // The outer list has no colour command of its own, so GL draws it in the red the call sets - a renderer that
            // kept the compile-time green draws the wrong colour here, and the checker counts exactly that.
            GL11.glPushMatrix();
            GL11.glTranslatef(celtium$centreX(panel), outerSlot, 0.0f);
            GL11.glColor4f(CALL_RED, CALL_GREEN, CALL_BLUE, 1.0f);
            GL11.glCallList(this.outerList);
            GL11.glPopMatrix();
            // The inner list sets green inside its compile, so the replayed command wins over the caller's red.
            GL11.glPushMatrix();
            GL11.glTranslatef(celtium$centreX(panel), innerSlot, 0.0f);
            GL11.glColor4f(CALL_RED, CALL_GREEN, CALL_BLUE, 1.0f);
            GL11.glCallList(this.innerList);
            GL11.glPopMatrix();
            // The line loop, GregTech's primitive, on the other side of the screen under its own translation. Its cyan
            // is set inside the list, like the inner square's green.
            GL11.glPushMatrix();
            GL11.glTranslatef(celtium$triangleX(panel), (panel[1] + panel[3]) / 2, 0.0f);
            GL11.glCallList(this.triangleList);
            GL11.glPopMatrix();
        } finally {
            // Restored in a finally: the rest of the frame is the game's GUI, which draws text and icons with this
            // state on, and a scene that left it off would break every screen after it.
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** {@return the GUI size this screen was last laid out at, or -1} - what the dumps were scaled from. */
    public int laidOutWidth() {
        return this.laidOutWidth;
    }

    public int laidOutHeight() {
        return this.laidOutHeight;
    }
}
