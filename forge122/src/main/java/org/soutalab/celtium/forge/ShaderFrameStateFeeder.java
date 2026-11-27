package org.soutalab.celtium.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;
import org.soutalab.celtium.vulkan.pack.PackFrameEnvironment;
import org.soutalab.celtium.vulkan.pack.PackWeatherFeed;

import java.nio.FloatBuffer;
import java.util.Optional;

/**
 * Fills a compiled pack's {@link ShaderFrameState} from the live Minecraft world each frame
 * and builds the {@link PackFrameEnvironment} the executor consumes: the world facts a pack
 * may read (time, weather, eye state, held items, sky/fog colours, camera), the gbuffer and
 * shadow matrices, and the culling frustums derived from them. Textures that live outside
 * the plan (block atlas, lightmap) are not resolved here - they belong to the backend that
 * owns them; the executor falls back cleanly until that seat is wired.
 */
public final class ShaderFrameStateFeeder {

    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer COLOR_BUFFER = BufferUtils.createFloatBuffer(4);
    private static final Matrix4f PREVIOUS_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PREVIOUS_PROJECTION = new Matrix4f();
    private static boolean havePrevious;
    private static long lastFrameNanos;
    private static float frameTimeCounter;
    private static int frameCounter;

    private ShaderFrameStateFeeder() {
    }

    public static void fill(Minecraft mc, ShaderFrameState state, float partialTicks) {
        World world = mc.world;
        Entity view = mc.getRenderViewEntity();
        if (world == null || view == null) {
            return;
        }
        long now = System.nanoTime();
        float frameTime = lastFrameNanos == 0L ? 1.0f / 60.0f
                : Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.5f);
        lastFrameNanos = now;
        frameTimeCounter += frameTime;
        frameCounter++;

        state.viewWidth = mc.displayWidth;
        state.viewHeight = mc.displayHeight;
        state.worldTime = world.getWorldTime();
        state.worldDay = (int) (state.worldTime / 24000L);
        state.moonPhase = world.provider.getMoonPhase(state.worldTime);
        float celestial = world.getCelestialAngle(partialTicks);
        state.sunAngle = celestial;
        state.shadowAngle = celestial;
        state.skyAngle = celestial;
        state.frameTime = frameTime;
        state.frameTimeCounter = frameTimeCounter;
        state.frameCounter = frameCounter;
        state.rainStrength = world.getRainStrength(partialTicks);
        state.thunderStrength = world.getThunderStrength(partialTicks);
        state.wetness = Math.max(state.wetness * 0.99f, state.rainStrength);

        double camX = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
        double camY = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
        double camZ = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
        state.previousCameraPosition.set(state.cameraPosition);
        state.cameraPosition.set(camX, camY, camZ);

        // The weather feed's per-frame vectors (weather contract): translation is
        // floor(view) - view per axis with y negated; the radius follows the vanilla
        // rain/snow render distance (Fast 5, Fancy 10).
        float invRadius = mc.gameSettings.fancyGraphics ? 0.1f : 0.2f;
        state.weatherParams0.set(
                (float) (Math.floor(camX) - camX), (float) -camY, (float) (Math.floor(camZ) - camZ),
                invRadius);
        state.weatherParams1.set(
                (float) (camX - Math.floor(camX)), (float) (camZ - Math.floor(camZ)),
                partialTicks, (float) (state.worldTime + partialTicks));
        state.weatherParams2.set(
                state.worldTime % 1024L, state.worldTime % 1024L,
                state.rainStrength, (float) (state.worldTime + partialTicks));
        state.weatherParams3.set(0.0f, 0.0f, 0.0f, 0.0f);

        int combinedLight = world.getCombinedLight(new BlockPos(view), 0);
        state.eyeBrightnessBlock = ((combinedLight >> 4) & 0xF) * 16;
        state.eyeBrightnessSky = ((combinedLight >> 20) & 0xF) * 16;
        state.eyeBrightnessSmoothBlock = state.eyeBrightnessBlock;
        state.eyeBrightnessSmoothSky = state.eyeBrightnessSky;
        state.isEyeInWater = view.isInsideOfMaterial(net.minecraft.block.material.Material.WATER) ? 1
                : view.isInsideOfMaterial(net.minecraft.block.material.Material.LAVA) ? 2 : 0;
        if (mc.player != null) {
            state.nightVision = mc.player.isPotionActive(MobEffects.NIGHT_VISION) ? 1.0f : 0.0f;
            state.blindness = mc.player.isPotionActive(MobEffects.BLINDNESS) ? 1.0f : 0.0f;
            ItemStack main = mc.player.getHeldItemMainhand();
            state.heldItemId = main.isEmpty() ? -1 : Item.getIdFromItem(main.getItem());
            state.heldItemId2 = -1;
            ItemStack off = mc.player.getHeldItemOffhand();
            if (!off.isEmpty()) {
                state.heldItemId2 = Item.getIdFromItem(off.getItem());
            }
        }

        Vec3d sky = world.getSkyColor(view, partialTicks);
        state.skyColor.set((float) sky.x, (float) sky.y, (float) sky.z);
        COLOR_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, COLOR_BUFFER);
        state.fogColor.set(COLOR_BUFFER.get(0), COLOR_BUFFER.get(1), COLOR_BUFFER.get(2));

        // Celestial positions in view space (the Iris convention): the sun travels a circle
        // in the XY plane as the celestial angle advances.
        double angle = celestial * Math.PI * 2.0;
        float sunX = (float) Math.cos(angle) * 100.0f;
        float sunY = (float) Math.sin(angle) * 100.0f;
        state.sunPosition.set(sunX, sunY, 0.0f);
        state.moonPosition.set(-sunX, -sunY, 0.0f);
        state.shadowLightPosition.set(sunY >= 0.0f ? state.sunPosition : state.moonPosition);
        state.upPosition.set(0.0f, 100.0f, 0.0f);

        // Matrices: the gbuffer pair is the live GL state of the world pass; the previous
        // pair is last frame's; the shadow pair is built from the sun direction and the
        // pack-independent default distance (the executor's environment refines the frustum
        // with the pack's own shadow distance).
        if (havePrevious) {
            state.gbufferPreviousModelView.set(PREVIOUS_MODEL_VIEW);
            state.gbufferPreviousProjection.set(PREVIOUS_PROJECTION);
        }
        readGlMatrix(GL11.GL_MODELVIEW_MATRIX, state.gbufferModelView);
        readGlMatrix(GL11.GL_PROJECTION_MATRIX, state.gbufferProjection);
        state.gbufferModelViewInverse.set(state.gbufferModelView).invert();
        state.gbufferProjectionInverse.set(state.gbufferProjection).invert();
        PREVIOUS_MODEL_VIEW.set(state.gbufferModelView);
        PREVIOUS_PROJECTION.set(state.gbufferProjection);
        havePrevious = true;

        float shadowDistance = 160.0f;
        Vector3f sunDirWorld = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0.0f);
        Matrix4f shadowView = new Matrix4f().lookAt(
                sunDirWorld.x * shadowDistance, sunDirWorld.y * shadowDistance, sunDirWorld.z * shadowDistance,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
        state.shadowModelView.set(shadowView);
        state.shadowModelViewInverse.set(shadowView).invert();
        Matrix4f shadowProjection = new Matrix4f().ortho(
                -shadowDistance, shadowDistance, -shadowDistance, shadowDistance,
                0.05f, shadowDistance * 2.0f);
        state.shadowProjection.set(shadowProjection);
        state.shadowProjectionInverse.set(shadowProjection).invert();
        state.farPlane = mc.gameSettings.renderDistanceChunks * 16.0f;
    }

    private static void readGlMatrix(int pname, Matrix4f out) {
        MATRIX_BUFFER.clear();
        GL11.glGetFloat(pname, MATRIX_BUFFER);
        out.set(MATRIX_BUFFER);
    }

    /** The executor's per-frame environment over the just-fed state. */
    public static PackFrameEnvironment buildEnvironment(Minecraft mc, ShaderFrameState state,
                                                        float partialTicks,
                                                        ActiveRenderTargets swapchain) {
        Entity view = mc.getRenderViewEntity();
        float yaw = view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks;
        float pitch = view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partialTicks;
        CameraPose mainCamera = new CameraPose(
                state.cameraPosition.x, state.cameraPosition.y, state.cameraPosition.z, yaw, pitch);
        Matrix4f combined = new Matrix4f(state.gbufferProjection).mul(state.gbufferModelView);
        FrustumPlanes mainFrustum = frustumFromMatrix(combined);

        float shadowDistance = 160.0f;
        double angle = state.sunAngle * Math.PI * 2.0;
        Vector3f sunDirWorld = new Vector3f(
                (float) Math.cos(angle), (float) Math.sin(angle), 0.0f);
        CameraPose shadowCamera = new CameraPose(
                state.cameraPosition.x + sunDirWorld.x * shadowDistance,
                state.cameraPosition.y + sunDirWorld.y * shadowDistance,
                state.cameraPosition.z + sunDirWorld.z * shadowDistance,
                (float) Math.toDegrees(Math.atan2(-sunDirWorld.x, sunDirWorld.z)),
                (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, -sunDirWorld.y)))));
        Matrix4f shadowCombined = new Matrix4f(state.shadowProjection).mul(state.shadowModelView);
        FrustumPlanes shadowFrustum = frustumFromMatrix(shadowCombined);

        PackWeatherFeed.WeatherState weather = new PackWeatherFeed.WeatherState(
                state.weatherParams0.x, state.weatherParams0.y, state.weatherParams0.z,
                state.weatherParams0.w,
                state.weatherParams1.x, state.weatherParams1.y,
                partialTicks, state.weatherParams1.w,
                state.worldTime % 1024L, state.worldTime % 1024L,
                state.rainStrength, state.weatherParams2.w, 0.0f);

        return new PackFrameEnvironment() {
            @Override
            public CameraPose mainCamera() {
                return mainCamera;
            }

            @Override
            public FrustumPlanes mainFrustum() {
                return mainFrustum;
            }

            @Override
            public CameraPose shadowCamera() {
                return shadowCamera;
            }

            @Override
            public FrustumPlanes shadowFrustum() {
                return shadowFrustum;
            }

            @Override
            public float shadowDistance() {
                return shadowDistance;
            }

            @Override
            public ActiveRenderTargets swapchainTargets() {
                return swapchain;
            }

            @Override
            public PackWeatherFeed.WeatherState weather() {
                return weather;
            }

            @Override
            public Lane2Windows lane2() {
                return Lane2Windows.NONE;
            }

            @Override
            public Optional<ResolvedTexture> texture(String name) {
                return Optional.empty();
            }
        };
    }

    /** Gribb-Hartmann frustum extraction from a combined projection*view matrix. */
    static FrustumPlanes frustumFromMatrix(Matrix4f m) {
        float[] planes = new float[FrustumPlanes.FLOAT_COUNT];
        // Rows of the combined matrix (JOML is column-major; get(row, col) reads logically).
        float[][] rows = new float[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rows[row][col] = m.get(row, col);
            }
        }
        writePlane(planes, FrustumPlanes.LEFT, rows[3], rows[0], 1.0f);
        writePlane(planes, FrustumPlanes.RIGHT, rows[3], rows[0], -1.0f);
        writePlane(planes, FrustumPlanes.BOTTOM, rows[3], rows[1], 1.0f);
        writePlane(planes, FrustumPlanes.TOP, rows[3], rows[1], -1.0f);
        writePlane(planes, FrustumPlanes.NEAR, rows[3], rows[2], 1.0f);
        writePlane(planes, FrustumPlanes.FAR, rows[3], rows[2], -1.0f);
        return FrustumPlanes.of(planes);
    }

    private static void writePlane(float[] out, int plane, float[] row3, float[] rowN, float sign) {
        float a = row3[0] + sign * rowN[0];
        float b = row3[1] + sign * rowN[1];
        float c = row3[2] + sign * rowN[2];
        float d = row3[3] + sign * rowN[3];
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length > 0.0f) {
            a /= length;
            b /= length;
            c /= length;
            d /= length;
        }
        int offset = plane * 4;
        out[offset] = a;
        out[offset + 1] = b;
        out[offset + 2] = c;
        out[offset + 3] = d;
    }
}
