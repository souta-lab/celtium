package org.taumc.celeritas.mixin.core;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.shader.ShaderUniform;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.soutalab.celtium.vulkan.VulkanPostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the vanilla post-process chain to the native executor instead of drawing it through GL.
 * <p>
 * {@code ShaderGroup.render} is a list of fullscreen passes over the group's framebuffers. When the group's main
 * framebuffer is the game's own (every reachable chain except the entity-outline group, whose main framebuffer is
 * the outline buffer GL still fills), each pass is collected with its live uniform values and the whole chain runs
 * natively inside a GL-to-Vulkan handoff, ordered exactly where the GL chain ran - after the world, before the HUD.
 * Anything the executor declines still renders through GL unchanged.
 * <p>
 * A group whose passes read {@code minecraft:main} and whose main is not the game's is the outline case: the input of
 * that chain is the framebuffer the game bound while it produced it (the entity-outline buffer, filled with the
 * silhouette), so the target's identity, the GL colour texture behind it and its size are reported to the backend
 * before the passes are collected. That is what lets the renderer keep the silhouette under the identity it was
 * already told about - rather than drawing it onto the scene - and hand that identity's image to the chain instead
 * of re-seeding the chain from a GL read of the texture, which a GL-less path cannot answer.
 */
@Mixin(ShaderGroup.class)
public abstract class ShaderGroupMixin {

    @Shadow
    private List<Shader> listShaders;

    @Shadow
    private float time;

    @Shadow
    private float lastStamp;

    @Shadow
    private net.minecraft.client.shader.Framebuffer mainFramebuffer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$renderChainNatively(float partialTicks, CallbackInfo info) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean mainIsGame = this.mainFramebuffer == minecraft.getFramebuffer();
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (!(current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend)
                || !backend.celtium$postChainAvailable()) {
            return;
        }
        if (!mainIsGame && (this.mainFramebuffer == null || this.mainFramebuffer.framebufferTexture <= 0)) {
            // An external main without a texture cannot be seeded or written back.
            return;
        }

        // The group's input, when its main is not the game's: the framebuffer the game bound while it produced that
        // input - the entity-outline buffer, whose silhouette was drawn under exactly this identity and whose id the
        // bind hook already reported for this same object. The GL name is what the game binds again to composite the
        // finished outline, and the texture size is the size an image standing for it must have. Reported before the
        // passes are collected, because it says where the input lives even when the chain itself cannot be described
        // natively and stays on GL.
        int inputIdentity = 0;
        int inputTexture = 0;
        int inputW = 0;
        int inputH = 0;
        if (!mainIsGame) {
            net.minecraft.client.shader.Framebuffer input = this.mainFramebuffer;
            inputIdentity = System.identityHashCode(input);
            inputTexture = input.framebufferTexture;
            inputW = input.framebufferTextureWidth;
            inputH = input.framebufferTextureHeight;
            // Hand the input framebuffer over, so the chain samples the image the renderer kept under this identity
            // instead of re-seeding from glGetTexImage, which the GL-less path cannot answer. The pass fields below
            // carry the same three facts to celtium$postChainRun, which is what the seeded path reads them from.
            backend.bindFramebufferColorTexture(inputIdentity, inputTexture, inputW, inputH);
        }

        // The time accumulation is the group's own; the mixin cannot call the original, so it keeps the contract.
        if (partialTicks < this.lastStamp) {
            this.time += 1.0F - this.lastStamp;
            this.time += partialTicks;
        } else {
            this.time += partialTicks - this.lastStamp;
        }
        for (this.lastStamp = partialTicks; this.time > 20.0F; this.time -= 20.0F) {
            ;
        }
        float passTime = this.time / 20.0F;

        List<VulkanPostChain.Pass> passes = new ArrayList<>(this.listShaders.size());
        for (Shader shader : this.listShaders) {
            VulkanPostChain.Pass pass = collect(shader, passTime, minecraft, this.mainFramebuffer);
            if (pass == null) {
                // A pass that cannot be described natively keeps the whole group on GL - partial chains are not a
                // mode the GL and native outputs can be compared on.
                return;
            }
            passes.add(pass);
        }
        if (passes.isEmpty()) {
            return;
        }
        if (!mainIsGame) {
            // The group's main is a plain GL framebuffer (the entity-outline buffer): the seeded path seeds it
            // from its texture and writes the output back, so carry the texture the executor must use. These are the
            // very values reported above, on the pass the executor reads them from - identity, GL colour texture and
            // input size for the target the chain's 'minecraft:main' resolves to.
            VulkanPostChain.Pass first = passes.get(0);
            first.groupMainTextureId = inputTexture;
            first.groupMainFbId = inputIdentity;
            first.groupMainW = inputW;
            first.groupMainH = inputH;
        } else {
            // The handoff validates the bound framebuffer; under adoption the game framebuffer's binding is the
            // shared one, which is exactly what a mid-frame acquire needs to see.
            minecraft.getFramebuffer().bindFramebuffer(true);
        }
        if (backend.celtium$postChainRun(passes)) {
            info.cancel();
        }
    }

    private static VulkanPostChain.Pass collect(Shader shader, float passTime, Minecraft minecraft,
            net.minecraft.client.shader.Framebuffer groupMain) {
        try {
            ShaderManager manager = shader.getShaderManager();
            net.minecraft.client.shader.Framebuffer in = shader.framebufferIn;
            net.minecraft.client.shader.Framebuffer out = shader.framebufferOut;
            // The dynamic uniforms Shader.render would set. ProjMat was installed at createBindFramebuffers, and
            // the chain's own uniform values came with the pass JSON - both already sit in the uniform objects.
            manager.getShaderUniformOrDefault("ProjMat").set(((ShaderAccessor) shader).celtium$projectionMatrix());
            manager.getShaderUniformOrDefault("InSize").set(
                    (float) in.framebufferTextureWidth, (float) in.framebufferTextureHeight);
            manager.getShaderUniformOrDefault("OutSize").set(
                    (float) out.framebufferTextureWidth, (float) out.framebufferTextureHeight);
            manager.getShaderUniformOrDefault("Time").set(passTime);
            manager.getShaderUniformOrDefault("ScreenSize").set(
                    (float) minecraft.displayWidth, (float) minecraft.displayHeight);
            List<Object> auxFramebuffers = ((ShaderAccessor) shader).celtium$auxFramebuffers();
            for (int i = 0; i < auxFramebuffers.size(); ++i) {
                Object aux = auxFramebuffers.get(i);
                if (aux instanceof net.minecraft.client.shader.Framebuffer) {
                    net.minecraft.client.shader.Framebuffer fbo = (net.minecraft.client.shader.Framebuffer) aux;
                    manager.getShaderUniformOrDefault("AuxSize" + i).set(
                            (float) fbo.framebufferTextureWidth, (float) fbo.framebufferTextureHeight);
                }
            }

            VulkanPostChain.Pass pass = new VulkanPostChain.Pass();
            pass.program = programName(manager);
            // 'main' in a chain means the group's own main framebuffer; how the executor resolves it - shared
            // image for the game's, seeded chain target for an external one - is the run's decision.
            pass.inIsMain = in == groupMain;
            pass.outIsMain = out == groupMain;
            // Identity keys, not GL framebuffer ids: the executor only matches them, and identity keeps each
            // framebuffer distinct even where the GL object name would be zero (a context-less path assigns no
            // real ids at all).
            pass.inFbId = System.identityHashCode(in);
            pass.inW = in.framebufferTextureWidth;
            pass.inH = in.framebufferTextureHeight;
            pass.outFbId = System.identityHashCode(out);
            pass.outW = out.framebufferTextureWidth;
            pass.outH = out.framebufferTextureHeight;
            if (!auxFramebuffers.isEmpty()) {
                pass.auxFbIds = new int[auxFramebuffers.size()];
                pass.auxW = new int[auxFramebuffers.size()];
                pass.auxH = new int[auxFramebuffers.size()];
                for (int i = 0; i < auxFramebuffers.size(); ++i) {
                    Object aux = auxFramebuffers.get(i);
                    if (!(aux instanceof net.minecraft.client.shader.Framebuffer)) {
                        return null;
                    }
                    net.minecraft.client.shader.Framebuffer fbo = (net.minecraft.client.shader.Framebuffer) aux;
                    // 0 marks the group's main framebuffer, resolved the same way intarget is.
                    pass.auxFbIds[i] = fbo == groupMain ? 0 : System.identityHashCode(fbo);
                    pass.auxW[i] = fbo.framebufferTextureWidth;
                    pass.auxH[i] = fbo.framebufferTextureHeight;
                }
            }
            int[] outBytes = new int[1];
            float[] block = pack(manager, outBytes);
            if (block == null || outBytes[0] <= 0 || outBytes[0] > block.length * 4) {
                return null;
            }
            pass.push = block;
            pass.pushBytes = outBytes[0];
            return pass;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    /** Whether a declared uniform name is part of the block's fixed head, packed at its known offset. */
    private static boolean isHeadUniform(String name) {
        return "ProjMat".equals(name) || "InSize".equals(name) || "OutSize".equals(name)
                || "Time".equals(name);
    }

    /**
     * {@return the program's push block in the normalized schema the ported shaders declare} - ProjMat at 0,
     * InSize at 64, OutSize at 72, Time at 80, then every remaining declared uniform in JSON order, aligned like
     * GLSL does (matrices and vec3+ at 16 bytes, vec2 at 8, scalars at 4).
     */
    private static float[] pack(ShaderManager manager, int[] outBytes) {
        float[] block = new float[64];
        if (!writeNamed(block, 0, manager, "ProjMat") || !writeNamed(block, 64, manager, "InSize")
                || !writeNamed(block, 72, manager, "OutSize") || !writeNamed(block, 80, manager, "Time")) {
            return null;
        }
        int offset = 88;
        List<ShaderUniform> uniforms = ((ShaderManagerAccessor) manager).celtium$shaderUniforms();
        for (ShaderUniform uniform : uniforms) {
            if (isHeadUniform(uniform.getShaderName())) {
                continue;
            }
            ShaderUniformAccessor accessor = (ShaderUniformAccessor) uniform;
            int type = accessor.celtium$uniformType();
            int count = accessor.celtium$uniformCount();
            int align = type >= 8 ? 16 : (count >= 3 ? 16 : (count == 2 ? 8 : 4));
            offset = (offset + align - 1) & ~(align - 1);
            if (offset + count * 4 > block.length * 4) {
                return null;
            }
            if (!writeUniform(block, offset, uniform)) {
                return null;
            }
            offset += count * 4;
        }
        outBytes[0] = offset;
        return block;
    }

    /** Writes a named uniform's value at a byte offset; an absent or unset uniform packs as zeros. */
    private static boolean writeNamed(float[] block, int byteOffset, ShaderManager manager, String name) {
        ShaderUniform uniform = manager.getShaderUniform(name);
        if (uniform == null) {
            return true;
        }
        return writeUniform(block, byteOffset, uniform);
    }

    private static boolean writeUniform(float[] block, int byteOffset, ShaderUniform uniform) {
        ShaderUniformAccessor accessor = (ShaderUniformAccessor) uniform;
        int type = accessor.celtium$uniformType();
        int count = accessor.celtium$uniformCount();
        if (type < 4) {
            IntBuffer buffer = accessor.celtium$uniformIntBuffer();
            if (buffer == null) {
                return false;
            }
            for (int i = 0; i < count; ++i) {
                block[byteOffset / 4 + i] = Float.intBitsToFloat(buffer.get(i));
            }
        } else {
            FloatBuffer buffer = accessor.celtium$uniformFloatBuffer();
            if (buffer == null) {
                return false;
            }
            for (int i = 0; i < count; ++i) {
                block[byteOffset / 4 + i] = buffer.get(i);
            }
        }
        return true;
    }

    /** {@return the program id reduced to the shader name}, for example {@code shaders/program/invert.json -> invert}. */
    private static String programName(ShaderManager manager) {
        String name = ((ShaderManagerAccessor) manager).celtium$programFilename();
        if (name == null) {
            return "";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name;
    }
}
