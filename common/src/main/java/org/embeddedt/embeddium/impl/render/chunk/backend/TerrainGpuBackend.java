package org.embeddedt.embeddium.impl.render.chunk.backend;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;

import java.util.Collection;

/** Terrain GPU operations; meshing, graph updates and sorting stay in the section manager. */
public interface TerrainGpuBackend {
    String getName();

    void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                CameraTransform occlusionCamera, CameraTransform camera, boolean profile);

    // ---------------------------------------------------------------- immediate-mode drawing (Forge API swap)

    /**
     * Mirrors {@code GlStateManager.func_179147_l()} / {@code func_179146_i()}.
     * <p>
     * These methods exist so the Minecraft/Forge drawing API can be re-implemented on Vulkan without changing any
     * public Minecraft signature: a Mixin on {@code GlStateManager} forwards the state calls it captures, and the
     * backend decides lazily which pipeline that state selects. The defaults do nothing, so an implementation that
     * does not draw immediate geometry is unaffected.
     */
    default void setBlendEnabled(boolean enabled) {
    }

    /** Mirrors {@code GlStateManager.func_187428_a(...)} / {@code func_179120_a(...)}; factors are GL's constants. */
    default void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) {
    }

    /**
     * Mirrors {@code GlStateManager.func_187398_d(int)}: the blend equation, as GL's own constant
     * ({@code GL_FUNC_ADD} = 0x8006, {@code GL_FUNC_SUBTRACT} = 0x800A, and so on). BH-07 - it bakes into the same
     * colour-blend attachment as the factors, so a backend that bakes one has to be told the other.
     */
    default void blendEquation(int rgbEquation, int alphaEquation) {
    }

    /**
     * Mirrors {@code GlStateManager.func_179135_a(boolean,boolean,boolean,boolean)}: which colour channels a write may
     * touch, as the {@code VK_COLOR_COMPONENT_*} nibble (R=1, G=2, B=4, A=8). BH-10 - the Fancy cloud's depth-only
     * pass clears all four while the visible pass writes them, and the two must not share a pipeline.
     */
    default void colorMask(int mask) {
    }

    /** Mirrors {@code GlStateManager.func_179132_a(boolean)}. */
    default void setDepthMask(boolean mask) {
    }

    /** Mirrors {@code GlStateManager.func_179097_i()} / {@code func_179126_j()}. */
    default void setDepthTest(boolean enabled) {
    }

    /**
     * Mirrors {@code GlStateManager.func_179098_w()} / {@code func_179090_x()}: whether texture sampling is on at all.
     * Vanilla draws the sky with it off, using vertex colours only.
     */
    default void setTexture2dEnabled(boolean enabled) {
    }

    /**
     * Tells the backend that the contents of a mirrored GL texture changed, so the next use re-reads it.
     * Mirroring a texture once is right for a static one and wrong for an animated one: Minecraft re-uploads animated
     * sprites through {@code TextureUtil.func_110989_a}.
     */
    default void invalidateTexture(int glTextureId) {
    }

    /**
     * Invalidates whichever texture the game has bound.
     * <p>
     * Minecraft's animation upload - {@code TextureUtil.func_147955_a} - carries no texture id because it writes into
     * the bound texture, so the bound name is the only thing that identifies it.
     */
    default void invalidateBoundTexture() {
    }

    /** Mirrors {@code GlStateManager.func_179144_i(int)}, the GL name of the texture the state has bound. */
    default void bindTexture(int glTextureId) {
    }

    /** Mirrors {@code GlStateManager.func_179131_c(float, float, float, float)}. */
    default void setColor(float red, float green, float blue, float alpha) {
    }

    /** Mirrors {@code GlStateManager.func_179094_E()} / {@code func_179121_F()}. */
    default void pushMatrix() {
    }

    /** Mirrors {@code GlStateManager.func_179128_n(int)}; the mode is the OpenGL constant. */
    default void matrixMode(int glMode) {
    }

    /** Mirrors {@code GlStateManager.func_179096_D()} (load identity). */
    default void loadIdentity() {
    }

    /** Mirrors {@code GlStateManager.func_179130_a(...)} (ortho). */
    default void ortho(double left, double right, double bottom, double top, double near, double far) {
    }

    /** Mirrors {@code GlStateManager.func_179137_b(double, double, double)} (translate). */
    default void translate(double x, double y, double z) {
    }

    /** Mirrors {@code GlStateManager.func_179114_b(float, float, float, float)} (rotate). */
    default void rotate(float angle, float x, float y, float z) {
    }

    /** Mirrors {@code GlStateManager.func_179152_a(float, float, float)} (scale). */
    default void scale(float x, float y, float z) {
    }

    /** Mirrors {@code GlStateManager.setActiveTexture(int)}; the argument is the unit index, not the GL enum. */
    default void activeTextureUnit(int unit) {
    }

    /** Mirrors {@code GlStateManager.func_179143_c(int)}; the argument is the GL depth-function constant. */
    default void depthFunc(int func) {
    }

    /** Mirrors {@code GlStateManager.func_179110_a(FloatBuffer)} (multiply matrix). */
    default void multiplyMatrix(java.nio.FloatBuffer buffer) {
    }

    /**
     * Mirrors {@code Project.gluPerspective(fov, aspect, near, far)}, which builds the world's projection and
     * installs it with {@code GL11.glMultMatrixf} of a freshly built frustum - it never passes through
     * {@code GlStateManager}, so a Mixin on that class alone cannot see it, and the stack it multiplies onto is
     * whatever the game's own mirrored {@code loadIdentity} left there.
     */
    default void perspective(float fovDegrees, float aspect, float near, float far) {
    }

    /**
     * Mirrors {@code GL11.glFrustum(left, right, bottom, top, near, far)}, the call the GLU perspective helper ends
     * in and the one a mod reaches for directly when it builds its own projection.
     */
    default void frustum(double left, double right, double bottom, double top, double near, double far) {
    }

    /**
     * Mirrors one whole {@code Project.gluPerspective(fovy, aspect, zNear, zFar)} call into the projection stack.
     * <p>
     * This is {@link #perspective} plus the one thing that call site knows and the frustum alone does not: what the
     * projection stack holds when the frustum is multiplied in. Every {@code gluPerspective} call in
     * {@code EntityRenderer} is preceded by the game's own mirrored
     * {@code matrixMode(GL_PROJECTION); loadIdentity();}, so GL multiplies the frustum onto the identity - while the
     * mirror, at that same instant, holds the pass projection that {@code applyDerivedProjection} has just written
     * over it (it runs from the field-of-view argument of this very call). Multiplying there applies the pass
     * projection twice, which is the measured shape of {@code f82bf4612}: the culling reads the mirror, and the
     * visible sections fell from 23 to 8. The implementation resets the mirrored stack to what GL holds before it
     * multiplies, so the mirror ends holding exactly the projection the call installs - at the camera's own call too,
     * where that value is the one the pass already draws with, so the culling reads the same frustum it read before.
     *
     * @return whether the call reached the mirror; a backend that does not mirror matrices answers {@code false}
     */
    default boolean mirrorGluPerspective(float fovDegrees, float aspect, float near, float far) {
        return false;
    }

    default void popMatrix() {
    }

    /**
     * Offers a finished immediate-mode batch to this backend.
     *
     * @param vertexFormat  Minecraft's {@code VertexFormat} instance for the batch
     * @param vertexStride  bytes per vertex of that format
     * @param formatId      a structural identifier of the layout (0 unknown, 1 position+colour, 2 position+texture+
     *                      colour), decided by comparing the element list rather than the class
     * @param vertexAddress native address of the first vertex
     * @param vertexCount   number of vertices
     * @param drawMode      Minecraft/OpenGL draw mode
     * @return {@code true} when the batch was consumed; {@code false} means the caller must draw it itself
     */
    /**
     * Sets the world projection from Minecraft's own numbers - field of view, aspect, near and far - instead of from a
     * mirrored GL call. The backend computes {@code gluPerspective} itself; see {@code EntityRendererMixin}.
     */
    default void applyDerivedProjection(float fovDegrees, float aspect, float near, float far) {
    }

    /** Seeds the mirrored matrices once, when the world camera has just been applied. See the Vulkan backend. */
    default void seedMirrorFromCameraSetup() {
    }

    /** The world pass has begun; matrices are valid for drawing from here. */
    default void beginWorldPass() {
    }

    /** The world pass has ended; batches after this are not drawn from the world snapshot. */
    default void endWorldPass() {
    }

    /** Captures the projection and camera transform for this frame, at the camera boundary. */
    default void captureWorldSnapshot() {
    }

    /**
     * The render target a batch belongs to; identity only, as a pass discriminator (audit S10).
     *
     * @param isMain whether this is the game's main framebuffer, the only target the shared image can honour
     */
    default void bindFramebuffer(long targetIdentity, boolean isMain) {
    }

    /**
     * The colour texture a bound framebuffer draws into, and its size.
     * <p>
     * A framebuffer's own name says which target a batch belongs to but not what its pixels are: with no OpenGL
     * context there is nothing to ask, and a pass that reads another pass's framebuffer (the entity outline reads the
     * silhouette framebuffer) can only be routed to the right image if the texture name that stands for it is known.
     * This is that name, reported where the framebuffer is bound.
     *
     * @param framebufferIdentity the same identity {@link #bindFramebuffer} was given
     * @param colorTexture        the GL texture name of the colour attachment, or 0 when there is none
     */
    default void bindFramebufferColorTexture(long framebufferIdentity, int colorTexture, int width, int height) {
    }

    /** The viewport in effect, which the group key also needs (audit S10). */
    default void viewport(int x, int y, int width, int height) {
    }

    /** The blend factors Minecraft set, captured because the mirror did not observe them (audit S07). */
    default void blendFunc(int sourceFactor, int destinationFactor) {
    }

    /** Called before a GL clear, so held batches are drawn before the target is wiped (audit S10). */
    default void flushBeforeClear(int mask) {
    }

    /**
     * Announces a texture upload together with the image it is uploading.
     * <p>
     * The hook that watches the game's texture utility already had the image in hand; passing it on is what lets a renderer
     * build its own copy from the same data instead of reading the pixels back out of GL afterwards.
     */
    default void announceTextureUpload(int glTextureId, java.awt.image.BufferedImage image) {
    }

    /** Announces a dynamic texture's write, which arrives as a flat pixel array. */
    /**
     * Announces a dynamic texture write together with the size the game already knows, so the backend does not have to
     * ask OpenGL for it ({@code glGetTexLevelParameteri}). The pixel array alone says nothing about the shape, and the
     * caller - Minecraft's own update path - has it.
     */
    default void announceDynamicTexture(int[] pixels, int width, int height) {
        announceDynamicTexture(pixels);
    }

    default void announceDynamicTexture(int[] pixels) {
    }

    /** Announces a whole-texture animated write with the pixels it is writing. */
    default void announceAnimatedUpload(int[][] data, int width, int height, int x, int y) {
    }

    /** {@return the reason the backend most recently refused a batch}, for diagnostics at the call site. */
    default String lastRefusal() {
        return "unknown";
    }

    default boolean submitImmediateBatch(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                         int vertexCount, int drawMode) {
        return false;
    }

    /** Consume/copy native output data before the caller deletes those outputs. */
    void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger);

    void updateRegions();

    void updateTimings();

    Object2LongMap<TerrainRenderPass> snapshotTimings();

    void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination);

    String getTransferQueueDebugString();

    void delete();

    /**
     * Mirrors {@code GlStateManager.func_179092_a(int, float)}: the alpha comparison and the reference value it is
     * compared against. Cutout content (leaves, plants, sharp-edged particles) relies on the test, so a pipeline that
     * ignores it draws those quads opaque.
     */
    default void setAlphaFunc(int glFunc, float reference) {
    }

    /**
     * Mirrors {@code GlStateManager.enableAlpha()}/{@code disableAlpha()} ({@code func_179141_d}/{@code func_179118_c}).
     * <p>
     * The enable is a state of its own and not a property of the function: the game enables the test for cutout content
     * and disables it again for the GUI, the font and anything drawn with a gradient, and a backend that only recorded
     * the comparison would keep testing after the game turned it off. Both this and {@link #setAlphaFunc} are needed
     * for the alpha contract to be complete - the pair shipped with the two hooks in {@code GlStateManagerMixin}
     * pointing at {@code enableAlpha}/{@code disableAlpha} under the lighting handlers' names, so the test never
     * arrived and the mirror's lighting flag was driven by the font and gradient alpha calls instead.
     */
    default void setAlphaTestEnabled(boolean enabled) {
    }

    /** {@code GlStateManager.enableLighting}/{@code disableLighting}: the fixed-function lighting switch itself. */
    default void setLightingEnabled(boolean enabled) {
    }

    /** {@code GlStateManager.enableLight}/{@code disableLight}: per-light enable, index 0 for GL_LIGHT0 and so on. */
    default void setLightEnabled(int index, boolean enabled) {
    }

    /**
     * {@code GlStateManager.glLight} with {@code GL_POSITION}: the raw direction the caller passed - the mirror is
     * responsible for folding the current modelview into it the way the driver does.
     */
    default void setLightPosition(int index, float x, float y, float z) {
    }

    /** {@code GlStateManager.glLight} with {@code GL_DIFFUSE}. */
    default void setLightDiffuse(int index, float red, float green, float blue) {
    }

    /** {@code GlStateManager.glLightModel} with {@code GL_LIGHT_MODEL_AMBIENT}. */
    default void setLightAmbient(float red, float green, float blue) {
    }

    /** {@code GlStateManager.shadeModel}: flat versus smooth, which fixed-function lighting honours per primitive. */
    default void setShadeModel(int glMode) {
    }

    /**
     * The world light level of the draw that is about to happen, as the light-map coordinate pair the game sets with
     * {@code OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y)} (SRG {@code func_77475_a}).
     * <p>
     * It is set per draw - {@code RenderLiving.java:64} for every living entity, the TESR dispatcher, the held item
     * and the layers - and it is the only carrier of the world light level for a batch whose vertices hold none (the
     * ITEM layout's normal carries the fixed-function lighting, not the light level). The renderer samples the
     * mirrored light map with it, which is what makes an entity dark at night.
     */
    default void setLightmapCoords(float x, float y) {
    }

    // ------------------------------------------------------------------ fixed-function fog

    /**
     * Mirrors {@code GlStateManager.enableFog()} / {@code disableFog()} ({@code func_179127_m} / {@code func_179106_n}).
     * <p>
     * The immediate path drew no fog at all before this family existed, and the sky dome is where that shows: the dome
     * is two <em>flat</em> grids ({@code RenderGlobal.renderSky(BufferBuilder, float, boolean)} builds them at
     * {@code y = ±16}), and what makes a flat grid read as a sky is the fog ramp across it - looking up you see the
     * near part of the plane (16 units away), looking at the horizon the far part (hundreds). Without the blend the
     * plane is one flat colour, which is exactly the "flat polygons" the vanilla-completion brief names.
     */
    default void setFogEnabled(boolean enabled) {
    }

    /**
     * Mirrors {@code GlStateManager.setFog(FogMode)}, i.e. the {@code glFogi(GL_FOG_MODE, ...)} the game issues:
     * {@code 9729} LINEAR, {@code 2048} EXP, {@code 2049} EXP2. The GL constant is passed through rather than an
     * ordinal, so a caller can compare it against {@code GL11} directly.
     */
    default void setFogMode(int glMode) {
    }

    /** Mirrors {@code GlStateManager.setFogDensity(float)} ({@code func_179095_a}); read by EXP and EXP2. */
    default void setFogDensity(float density) {
    }

    /** Mirrors {@code GlStateManager.setFogStart(float)} ({@code func_179102_b}); read by LINEAR. */
    default void setFogStart(float start) {
    }

    /** Mirrors {@code GlStateManager.setFogEnd(float)} ({@code func_179153_c}); read by LINEAR. */
    default void setFogEnd(float end) {
    }

    /**
     * Mirrors {@code GlStateManager.glFog(GL_FOG_COLOR, buffer)} ({@code func_187402_b} with pname {@code 2918}).
     * <p>
     * This is how the fog colour reaches GL: 1.12.2 keeps it in {@code EntityRenderer.fogColorRed/Green/Blue} and
     * pushes it through this call in {@code setupFogColor} before every {@code setupFog}. The alpha of the GL fog
     * colour is 1.0 in vanilla and the GL blend uses the RGB only.
     */
    default void setFogColor(float red, float green, float blue) {
    }

    /**
     * Mirrors {@code GlStateManager.glFogi(GL_FOG_DISTANCE_MODE_NV, GL_EYE_RADIAL_NV)} ({@code func_187412_c} with
     * pname {@code 34138}).
     * <p>
     * {@code EntityRenderer.setupFog} asks for radial fog when {@code GL_NV_fog_distance} is present, which makes the
     * fog coordinate the distance from the eye rather than the eye-space depth. The two differ everywhere off the view
     * axis, so a renderer that computes the coordinate itself has to be told which one the game asked for. The GL-less
     * stub answers the extension query with an empty string, so under GL-less this stays false while the same game code
     * on a driver that has the extension sets it - which is why the backend's own distance mode can be pinned, and why
     * this mirror records what the game asked for either way.
     */
    default void setFogRadial(boolean radial) {
    }

    /**
     * {@return whether this backend would really apply the fixed-function fog to an immediate batch}.
     * <p>
     * The question exists because a caller has to decide whether a fogged draw it would otherwise refuse is now drawn
     * correctly. It has to be a capability and not a flag: a mixin whose target names differ applies nothing
     * ({@code require = 0}), and a renderer that answered "fog works" on the strength of its own configuration would
     * then let a batch through unfogged - which is the wrong picture the refusal existed to prevent. So an
     * implementation answers "yes" only once the game's own state has actually arrived.
     */
    default boolean immediateFogAvailable() {
        return false;
    }
}
