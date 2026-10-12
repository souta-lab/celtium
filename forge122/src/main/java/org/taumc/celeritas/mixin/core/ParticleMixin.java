package org.taumc.celeritas.mixin.core;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two per-particle-per-frame costs removed, after Flerovium's fast-math/light-cache items:
 *
 * <ol>
 * <li><b>Camera basis reuse.</b> Vanilla allocates four {@code Vec3d} per particle per frame and
 * recomputes the same camera-basis terms for every particle; the rotation arguments are constant for
 * a whole frame of particles, and {@code particleScale} factors out linearly, so the twelve basis
 * coefficients are computed once per distinct argument set (once per frame in practice) and the
 * angle==0 case emits vertices with no Vec3d allocation and no basis math at all. Rotating particles
 * fall through to the original method unchanged.</li>
 * <li><b>Light lookup caching.</b> {@code getBrightnessForRender} allocates a {@code BlockPos} and
 * runs {@code World#getCombinedLight} per particle per frame. Light changes on the world tick, not
 * per frame, and particle clusters sit in the same block for many frames; the result is cached per
 * packed block position and reset on the world-tick boundary.</li>
 * </ol>
 *
 * Injected on {@link Particle} itself: subclasses that override these methods keep their own paths,
 * so the fast path applies exactly where vanilla's own math would have run. Kill switch
 * {@code -Dceltium.particleCache=false}; light-cache off separately via
 * {@code -Dceltium.particleLightCache=false}.
 */
@Mixin(Particle.class)
public abstract class ParticleMixin {

    @Shadow public float particleScale;
    @Shadow protected float particleAngle;
    @Shadow protected float prevParticleAngle;
    @Shadow public double prevPosX, prevPosY, prevPosZ;
    @Shadow public double posX, posY, posZ;
    @Shadow protected int particleTextureIndexX;
    @Shadow protected int particleTextureIndexY;
    @Shadow protected net.minecraft.client.renderer.texture.TextureAtlasSprite particleTexture;
    @Shadow protected float particleRed, particleGreen, particleBlue, particleAlpha;
    @Shadow public static double interpPosX, interpPosY, interpPosZ;
    @Shadow protected World world;

    private static final boolean CELTIUM_PARTICLE =
            !"false".equals(System.getProperty("celtium.particleCache", "true"));
    private static final boolean CELTIUM_PARTICLE_LIGHT =
            !"false".equals(System.getProperty("celtium.particleLightCache", "true"));

    /** The twelve basis coefficients, without the per-particle scale: offsets = coeff * (0.1 * particleScale). */
    private static final float[] celtium$basis = new float[12];
    /** The arguments the cached basis was computed for; a changed value means a new frame/pass. */
    private static float celtium$basisX = Float.NaN, celtium$basisZ, celtium$basisYZ, celtium$basisXY, celtium$basisXZ;

    /** The tick the light cache belongs to; BlockPos-packed key -> combined light. */
    private static long celtium$lightTick = Long.MIN_VALUE;
    private static final java.util.LinkedHashMap<Long, Integer> celtium$lightCache =
            new java.util.LinkedHashMap<Long, Integer>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, Integer> eldest) {
                    return size() > 1024;
                }
            };

    @Inject(method = "func_189214_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$lightCache(float partialTicks, CallbackInfoReturnable<Integer> info) {
        if (!CELTIUM_PARTICLE_LIGHT || this.world == null) {
            return;
        }
        long tick = this.world.getTotalWorldTime();
        if (tick != celtium$lightTick) {
            celtium$lightTick = tick;
            celtium$lightCache.clear();
        }
        // The BlockPos the original allocates is only ever a key on the lookup path, so the key is
        // packed by hand (same layout as BlockPos.toLong: 25 bits z, 12 bits y, 25 bits x) and the
        // object is materialised only on a cache miss, where the world call needs it anyway.
        long x = net.minecraft.util.math.MathHelper.floor(this.posX);
        long y = net.minecraft.util.math.MathHelper.floor(this.posY);
        long z = net.minecraft.util.math.MathHelper.floor(this.posZ);
        long key = (x & 0x3ffffffL) << 37 | (y & 0xfffL) << 25 | (z & 0x3ffffffL);
        Integer cached = celtium$lightCache.get(Long.valueOf(key));
        if (cached != null) {
            info.setReturnValue(cached.intValue());
            return;
        }
        BlockPos pos = new BlockPos(this.posX, this.posY, this.posZ);
        int value = this.world.isBlockLoaded(pos) ? this.world.getCombinedLight(pos, 0) : 0;
        celtium$lightCache.put(Long.valueOf(key), Integer.valueOf(value));
        info.setReturnValue(value);
    }

    @Inject(method = "func_180434_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$renderParticleFast(BufferBuilder buffer, Entity entityIn, float partialTicks,
            float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ,
            CallbackInfo info) {
        if (!CELTIUM_PARTICLE || this.particleAngle != 0.0F) {
            return; // Rotating particles take the original Vec3d path.
        }
        if (rotationX != celtium$basisX || rotationZ != celtium$basisZ || rotationYZ != celtium$basisYZ
                || rotationXY != celtium$basisXY || rotationXZ != celtium$basisXZ) {
            celtium$basisX = rotationX;
            celtium$basisZ = rotationZ;
            celtium$basisYZ = rotationYZ;
            celtium$basisXY = rotationXY;
            celtium$basisXZ = rotationXZ;
            float[] b = celtium$basis;
            b[0] = -rotationX - rotationXY; b[1] = -rotationZ; b[2] = -rotationYZ - rotationXZ;
            b[3] = -rotationX + rotationXY; b[4] = rotationZ;  b[5] = -rotationYZ + rotationXZ;
            b[6] = rotationX + rotationXY;  b[7] = rotationZ;  b[8] = rotationYZ + rotationXZ;
            b[9] = rotationX - rotationXY;  b[10] = -rotationZ; b[11] = rotationYZ - rotationXZ;
        }
        // --- the vanilla method body, minus the Vec3d array and the angle!=0 branch ---
        float f = this.particleTextureIndexX / 16.0F;
        float f1 = f + 0.0624375F;
        float f2 = this.particleTextureIndexY / 16.0F;
        float f3 = f2 + 0.0624375F;
        float f4 = 0.1F * this.particleScale;
        if (this.particleTexture != null) {
            f = this.particleTexture.getMinU();
            f1 = this.particleTexture.getMaxU();
            f2 = this.particleTexture.getMinV();
            f3 = this.particleTexture.getMaxV();
        }
        float f5 = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
        float f6 = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
        float f7 = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);
        int i = celtium$brightness((Particle) (Object) this, partialTicks);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        float[] b = celtium$basis;
        buffer.pos(f5 + b[0] * f4, f6 + b[1] * f4, f7 + b[2] * f4).tex(f1, f3)
                .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
                .lightmap(j, k).endVertex();
        buffer.pos(f5 + b[3] * f4, f6 + b[4] * f4, f7 + b[5] * f4).tex(f1, f2)
                .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
                .lightmap(j, k).endVertex();
        buffer.pos(f5 + b[6] * f4, f6 + b[7] * f4, f7 + b[8] * f4).tex(f, f2)
                .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
                .lightmap(j, k).endVertex();
        buffer.pos(f5 + b[9] * f4, f6 + b[10] * f4, f7 + b[11] * f4).tex(f, f3)
                .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
                .lightmap(j, k).endVertex();
        info.cancel();
    }

    /** The same call the original makes - routed through the mixin so the light cache applies to our fast path too. */
    private static int celtium$brightness(Particle self, float partialTicks) {
        return self.getBrightnessForRender(partialTicks);
    }
}
