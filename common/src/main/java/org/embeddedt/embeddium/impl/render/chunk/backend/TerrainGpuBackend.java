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

    /** Mirrors {@code GlStateManager.func_187428_a(...)}; factors are the enum ordinals. */
    default void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha) {
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
     * installs it with {@code GL11.glFrustum} - it never passes through {@code GlStateManager}, so a Mixin on that
     * class alone cannot see it.
     */
    default void perspective(float fovDegrees, float aspect, float near, float far) {
    }

    /**
     * Mirrors {@code GL11.glFrustum(left, right, bottom, top, near, far)}, the call the GLU perspective helper ends
     * in and the one a mod reaches for directly when it builds its own projection.
     */
    default void frustum(double left, double right, double bottom, double top, double near, double far) {
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
}
