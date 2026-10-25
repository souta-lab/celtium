package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;

/**
 * Vanilla framebuffer composition as a native operation. The source is an owned image alias, not a GL texture copy.
 * The quad is retained in clip space, with its own material, projection, fog/depth/cull policy and viewport; no
 * Tessellator, texture matrix or inherited world shader state participates. Accepted generations are leased until
 * their submission completes, just like retained model geometry.
 */
final class NativeFramebufferCompositor {
    private static final float[] IDENTITY = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    private final VulkanTerrainGpuBackend b;
    private LeasedResource<VulkanDynamicMesh> quad;
    private float uMax, vMax;
    private long queued, selfCopiesSkipped;

    NativeFramebufferCompositor(VulkanTerrainGpuBackend backend) { this.b = backend; }

    void submit(long sourceIdentity, boolean sourceIsMain, int texture, int sourceWidth, int sourceHeight,
            int textureWidth, int textureHeight, int width, int height, boolean opaque) {
        if (width <= 0 || height <= 0) return;
        if (sourceIsMain && (b.targetRegistry.currentIsMain() || b.targetRegistry.currentIdentity() == 0L)) {
            // The SDL/Vulkan present path already owns delivery of the main image. Copying it onto itself is feedback.
            this.selfCopiesSkipped++;
            return;
        }
        if (texture <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("framebuffer composite has no native source extent/name");
        }
        b.closeCurrentImmediateGroup();
        // Registering the source is not a framebuffer bind: the destination must remain the current render target.
        b.publishedTextures.bindColorTexture(sourceIdentity, texture, textureWidth, textureHeight);
        float u = (float) sourceWidth / textureWidth;
        float v = (float) sourceHeight / textureHeight;
        if (this.quad == null || this.uMax != u || this.vMax != v) {
            VulkanDynamicMesh mesh = new VulkanDynamicMesh(b.context);
            try {
                mesh.begin(VulkanDynamicMesh.Layout.positionTexColor());
                vertex(mesh, -1, -1, 0, 0);
                vertex(mesh, 1, -1, u, 0);
                vertex(mesh, 1, 1, u, v);
                vertex(mesh, -1, 1, 0, v);
                for (int index : new int[] {0,1,2,0,2,3}) mesh.index(index);
            } catch (RuntimeException | Error error) {
                mesh.delete();
                throw error;
            }
            if (this.quad != null) this.quad.close();
            this.quad = new LeasedResource<>(mesh, VulkanDynamicMesh::delete);
            this.uMax = u;
            this.vMax = v;
        }
        int[] state = b.celtium$captureState().clone();
        state[3] = 0; // depth test
        state[4] = 0; // depth write
        state[5] = 0; // a full-surface operation has no face-culling dependency
        state[6] = 0; // alpha reference
        state[8] = 0; // lighting
        state[11] = 7; // vanilla framebufferRenderExt writes RGB, retaining destination alpha
        state[14] = 0; // alpha test disabled; sampled alpha controls blend only
        if (opaque) {
            state[0] = 0;
            state[1] = state[2] = state[9] = state[10] = 0;
            state[12] = state[13] = VK_BLEND_OP_ADD;
        }
        long key = b.celtium$stateKey(VulkanImmediateFormats.POSITION_TEX_COLOR, state);
        PreparedImmediate material = b.prepareImmediate(VulkanImmediateFormats.POSITION_TEX_COLOR, texture, key, state);
        if (material == null) throw new IllegalStateException("framebuffer has no initialized native material: " + texture);
        ImmediateGroup group = ImmediateGroup.obtain(null, IDENTITY, IDENTITY,
                VulkanImmediateFormats.POSITION_TEX_COLOR, texture, key, state, b.targetRegistry.currentIdentity());
        LeasedResource.Lease<VulkanDynamicMesh> lease = this.quad.acquire();
        group.mesh = lease.value();
        group.geometryLease = lease;
        group.prepared = material;
        group.textureImage = b.celtium$registry().get(texture);
        group.textureMutations = b.textureRegistry.mutations();
        group.viewportValues = new int[] {0, 0, width, height};
        group.scissorEnabled = b.rasterState.scissorTest();
        group.scissorBox = b.rasterState.scissorBox();
        group.stamp = GroupStamp.obtain(b.frameId, (int) b.targetRegistry.generation(), true, b.worldEpoch);
        group.batches = 1;
        b.immediateGroups.add(group);
        b.pendingImmediateIndices += 6;
        b.immediateStatistics.noteImmediateGroupsClosed();
        this.queued++;
    }

    private static void vertex(VulkanDynamicMesh mesh, float x, float y, float u, float v) {
        mesh.nextVertex(); mesh.putPosition(x, y, 0); mesh.putUv(u, v); mesh.putColor(255,255,255,255);
    }

    /** The caller has completed GPU work; groups still own their independent leases until discarded/completed. */
    void release() {
        if (this.quad != null) { this.quad.close(); this.quad = null; }
    }
    String describe() { return "nativeFramebufferComposites=" + this.queued + " nativeSelfCopiesSkipped=" + this.selfCopiesSkipped; }
}
