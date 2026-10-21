package org.soutalab.celtium.vulkan;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Device-bound ownership of retained geometry. No per-frame vertex copy or GL buffer lookup. */
final class RetainedGeometryCache implements AutoCloseable {
    private final VulkanContext context;
    private final Map<NativeGeometryBuffer, Map<Integer, LeasedResource<VulkanDynamicMesh>>> entries =
            new IdentityHashMap<>();
    private long builds;
    private long acquires;
    private long reuses;
    private long uploadedBytes;
    private final long[] formatsBuilt = new long[VulkanImmediateFormats.COUNT];
    private boolean closed;

    RetainedGeometryCache(VulkanContext context) {
        this.context = context;
    }

    LeasedResource.Lease<VulkanDynamicMesh> acquire(NativeGeometryBuffer source, int mode, int[] indices) {
        if (this.closed) throw new IllegalStateException("retained geometry device cache is closed");
        Map<Integer, LeasedResource<VulkanDynamicMesh>> modes = this.entries.get(source);
        LeasedResource<VulkanDynamicMesh> resource = modes == null ? null : modes.get(mode);
        if (resource == null) {
            VulkanDynamicMesh mesh = new VulkanDynamicMesh(this.context);
            try {
                if (!mesh.copyVertices(source.address(), source.stride(), source.vertexCount(), indices)) {
                    throw new IllegalArgumentException("retained geometry has no drawable vertices or indices");
                }
            } catch (RuntimeException | Error error) {
                mesh.discard();
                throw error;
            }
            resource = new LeasedResource<>(mesh, VulkanDynamicMesh::delete);
            if (modes == null) {
                modes = new HashMap<>();
                this.entries.put(source, modes);
                source.onInvalidate(this, () -> invalidate(source));
            }
            modes.put(mode, resource);
            this.builds++;
            this.formatsBuilt[source.formatId()]++;
            this.uploadedBytes += (long) source.stride() * source.vertexCount() + (long) indices.length * 4L;
        } else {
            this.reuses++;
        }
        this.acquires++;
        return resource.acquire();
    }

    private void invalidate(NativeGeometryBuffer source) {
        Map<Integer, LeasedResource<VulkanDynamicMesh>> modes = this.entries.remove(source);
        source.detach(this);
        if (modes != null) {
            for (LeasedResource<VulkanDynamicMesh> resource : modes.values()) {
                // Old generations may still be queued, recorded, or in flight. Only their owner reference ends here.
                resource.close();
            }
        }
    }

    String summary() {
        return "retainedBuilds=" + this.builds + " retainedAcquires=" + this.acquires
                + " retainedReuses=" + this.reuses + " retainedUploadBytes=" + this.uploadedBytes
                + " retainedSources=" + this.entries.size()
                + " retainedFormatsBuilt=" + java.util.Arrays.toString(this.formatsBuilt);
    }

    @Override
    public void close() {
        this.closed = true;
        for (NativeGeometryBuffer source : this.entries.keySet().toArray(new NativeGeometryBuffer[0])) {
            invalidate(source);
        }
    }
}
