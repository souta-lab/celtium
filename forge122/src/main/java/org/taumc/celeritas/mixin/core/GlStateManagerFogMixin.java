package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

/**
 * Mirrors the fog state the game sets through {@code GlStateManager} into the Vulkan backend.
 * <p>
 * This is the fog half of the same mirror {@code GlStateManagerMixin} performs for colour, blend, depth and the
 * matrices; it is a separate class because that file is owned by another worker this round, and because the seven
 * calls below are one subject. The vanilla immediate path has no fog at all without them, which is what makes the sky
 * dome and the horizon plane - two flat grids - read as flat coloured polygons instead of as sky.
 * <p>
 * Where the game sets it, read from the decompiled sources:
 * <ul>
 *   <li>{@code EntityRenderer.setupFog(int, float)} (EntityRenderer.java:1952) is the whole of the world's fog: the
 *       mode at 1972/1992/1997/2017/2023, the density at 1960/1993/2003/2007/2012/2018, the start and end at
 *       1976/1981/2027/2032/2043 and 1977/1982/2028/2033/2044, and {@code enableFog()} at 2050. It is called with
 *       {@code -1} before the sky (1343) and with {@code 0} before the terrain, the clouds, the particles and the
 *       translucent layer (1356, 1365, 1438, 1454).</li>
 *   <li>{@code EntityRenderer.setupFogColor(boolean)} (2054) pushes the colour with
 *       {@code GlStateManager.glFog(GL_FOG_COLOR, ...)} at 2058 (black) and 2062 (the real colour), and the colour
 *       itself is in {@code fogColorRed/Green/Blue}, accumulated by {@code updateFogColor} (1779).</li>
 *   <li>{@code RenderGlobal.renderSky} enables fog around the dome only - {@code enableFog()} at RenderGlobal.java:1337,
 *       {@code disableFog()} at 1354, before the sunrise fan and the sun/moon quads - which is why the state has to be
 *       captured per batch rather than read at flush time: the dome and the sun are drawn under different fog.</li>
 *   <li>{@code EntityRenderer} disables fog after the translucent layer (1476) and around the hand (1508).</li>
 * </ul>
 * The names are SRG because these are vanilla methods: {@code func_179127_m} {@code enableFog}, {@code func_179106_n}
 * {@code disableFog}, {@code func_179093_d(int)} the private {@code setFog} whose body is the {@code glFogi}, the three
 * {@code setFog*} setters, {@code func_187402_b(int, FloatBuffer)} {@code glFog} and {@code func_187412_c(int, int)}
 * {@code glFogi} - both checked with {@code scripts/mc-sources.sh --srg}, not guessed. (An earlier note in this
 * project's brief named {@code func_179096_D} as the fog enable; that name is {@code loadIdentity}, which
 * {@code GlStateManagerMixin} already mirrors, and the real enable is {@code func_179127_m}.)
 * <p>
 * {@code require = 0} on every hook, as everywhere in this package: a build whose names differ must degrade to "not
 * mirrored", never to a client that will not start. Each hook is also exception-guarded, because it runs inside
 * vanilla rendering.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerFogMixin {

    /** {@code GL_FOG_COLOR}: the pname {@code setupFogColor} passes to {@code glFog}. */
    private static final int GL_FOG_COLOR = 2918;
    /** {@code GL_FOG_DISTANCE_MODE_NV} and {@code GL_EYE_RADIAL_NV}: what {@code setupFog} sets when the driver has it. */
    private static final int GL_FOG_DISTANCE_MODE_NV = 34138;
    private static final int GL_EYE_RADIAL_NV = 34139;

    @Inject(method = "func_179127_m", at = @At("HEAD"), require = 0)
    private static void celtiumFog$enableFog(CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogEnabled(true));
    }

    @Inject(method = "func_179106_n", at = @At("HEAD"), require = 0)
    private static void celtiumFog$disableFog(CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogEnabled(false));
    }

    /**
     * The mode, hooked on the private {@code setFog(int)} rather than on the public {@code setFog(FogMode)} overload:
     * the public one calls this one, so hooking here sees every path - the enum call vanilla makes and any direct
     * {@code glFogi(GL_FOG_MODE, ...)} a mod would issue - and it is the method whose body is the GL call the mirror is
     * standing in for. The game guards it with {@code if (param != fogState.mode)}, so a value that did not change does
     * not arrive here; that is the same thing GL sees.
     */
    @Inject(method = "func_179093_d", at = @At("HEAD"), require = 0)
    private static void celtiumFog$setFogMode(int glMode, CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogMode(glMode));
    }

    @Inject(method = "func_179095_a", at = @At("HEAD"), require = 0)
    private static void celtiumFog$setFogDensity(float density, CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogDensity(density));
    }

    @Inject(method = "func_179102_b", at = @At("HEAD"), require = 0)
    private static void celtiumFog$setFogStart(float start, CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogStart(start));
    }

    @Inject(method = "func_179153_c", at = @At("HEAD"), require = 0)
    private static void celtiumFog$setFogEnd(float end, CallbackInfo info) {
        celtiumFog$mirror(backend -> backend.setFogEnd(end));
    }

    /**
     * {@code glFog(GL_FOG_COLOR, buffer)}: the colour is read out of the caller's buffer at its current position,
     * because that is exactly what the GL call would have read. Another pname is ignored - this mirror is the fog
     * colour, not a general {@code glFog} capture.
     */
    @Inject(method = "func_187402_b", at = @At("HEAD"), require = 0)
    private static void celtiumFog$glFog(int pname, FloatBuffer params, CallbackInfo info) {
        if (pname != GL_FOG_COLOR || params == null || params.remaining() < 3) {
            return;
        }
        int position = params.position();
        float red = params.get(position);
        float green = params.get(position + 1);
        float blue = params.get(position + 2);
        celtiumFog$mirror(backend -> backend.setFogColor(red, green, blue));
    }

    /**
     * {@code glFogi(GL_FOG_DISTANCE_MODE_NV, GL_EYE_RADIAL_NV)}: the game's own radial-fog request. Recorded rather
     * than acted on here, and only for that one pname/param pair; the distance mode the shader uses is the backend's.
     */
    @Inject(method = "func_187412_c", at = @At("HEAD"), require = 0)
    private static void celtiumFog$glFogi(int pname, int param, CallbackInfo info) {
        if (pname != GL_FOG_DISTANCE_MODE_NV) {
            return;
        }
        boolean radial = param == GL_EYE_RADIAL_NV;
        celtiumFog$mirror(backend -> backend.setFogRadial(radial));
    }

    private static void celtiumFog$mirror(Consumer<TerrainGpuBackend> action) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            action.accept(backend);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
