package org.soutalab.celtium.vulkan.graph;

/**
 * The standard vanilla frame as a compiled graph (design section 2.2A): uploads, GPU terrain
 * culling, one forward pass that draws <em>directly into the swapchain image</em>, Hi-Z build
 * for the next frame, present. This is the replacement for the offscreen-composite route; the
 * swapchain image is an imported resource whose handle the
 * executor binds per frame, so the same compiled graph serves every swapchain recreation.
 *
 * <p>Barrier shape, for the record: the design names three inter-pass synchronisation points
 * (upload&rarr;cull, cull&rarr;main, main&rarr;Hi-Z). The compiled graph emits 7 barriers in
 * total - those points plus the first-use layout transitions (Hi-Z, depth) and the present
 * transition, which the design folds into the render pass's {@code finalLayout}. The executor
 * counts them the same way the compiler does; nothing here claims the smaller number.
 */
public final class VanillaDirectGraph {

    private final CompiledGraph graph;
    private final GraphResource swapchainColor;
    private final GraphResource depth;
    private final GraphResource hiz;
    private final GraphResource indirectDraws;
    private final GraphResource sectionDesc;
    private final GraphResource staging;
    private final GraphResource terrainSlab;

    private VanillaDirectGraph(CompiledGraph graph, GraphResource swapchainColor,
                               GraphResource depth, GraphResource hiz,
                               GraphResource indirectDraws, GraphResource sectionDesc,
                               GraphResource staging, GraphResource terrainSlab) {
        this.graph = graph;
        this.swapchainColor = swapchainColor;
        this.depth = depth;
        this.hiz = hiz;
        this.indirectDraws = indirectDraws;
        this.sectionDesc = sectionDesc;
        this.staging = staging;
        this.terrainSlab = terrainSlab;
    }

    /**
     * @param swapchainInitialLayout the layout the swapchain image starts frames in
     *                               ({@link ImageLayout#PRESENT_SRC} in steady state,
     *                               {@link ImageLayout#UNDEFINED} for the first frame)
     */
    public static VanillaDirectGraph build(int width, int height, int swapchainFormat,
                                           int depthFormat, int hizFormat,
                                           ImageLayout swapchainInitialLayout) {
        GraphResource swapchain = GraphResource.importedImage("swapchainColor", width, height,
                swapchainFormat).withInitialLayout(swapchainInitialLayout);
        GraphResource depth = GraphResource.transientImage("depth", width, height, depthFormat);
        GraphResource hiz = GraphResource.transientImage("hiz", width / 2, height / 2, hizFormat);
        GraphResource indirect = GraphResource.transientBuffer("indirectDraws", 1 << 20);
        GraphResource sections = GraphResource.importedBuffer("sectionDesc", 0);
        GraphResource staging = GraphResource.importedBuffer("stagingRing", 0);
        GraphResource slab = GraphResource.importedBuffer("terrainSlab", 0);

        FrameGraphBuilder builder = new FrameGraphBuilder();
        builder.addPass(PassNode.builder("UploadBatch", PassNode.Kind.TRANSFER)
                .access(staging, AccessType.TRANSFER_SRC)
                .access(slab, AccessType.TRANSFER_DST)
                .build());
        builder.addPass(PassNode.builder("TerrainCull", PassNode.Kind.COMPUTE)
                .access(sections, AccessType.COMPUTE_READ)
                .access(hiz, AccessType.SHADER_READ)
                .access(indirect, AccessType.COMPUTE_WRITE)
                .build());
        builder.addPass(PassNode.builder("MainForwardPass", PassNode.Kind.GRAPHICS)
                .access(indirect, AccessType.INDIRECT_READ)
                .access(swapchain, AccessType.COLOR_ATTACHMENT_WRITE)
                .access(depth, AccessType.DEPTH_ATTACHMENT_WRITE)
                .build());
        builder.addPass(PassNode.builder("HiZBuild", PassNode.Kind.COMPUTE)
                .access(depth, AccessType.DEPTH_READ)
                .access(hiz, AccessType.COMPUTE_WRITE)
                .build());
        builder.addPass(PassNode.builder("Present", PassNode.Kind.PRESENT)
                .access(swapchain, AccessType.PRESENT)
                .build());
        return new VanillaDirectGraph(builder.build(), swapchain, depth, hiz, indirect,
                sections, staging, slab);
    }

    public CompiledGraph graph() {
        return this.graph;
    }

    public GraphResource swapchainColor() {
        return this.swapchainColor;
    }

    public GraphResource depth() {
        return this.depth;
    }

    public GraphResource hiz() {
        return this.hiz;
    }

    public GraphResource indirectDraws() {
        return this.indirectDraws;
    }

    public GraphResource sectionDesc() {
        return this.sectionDesc;
    }

    public GraphResource staging() {
        return this.staging;
    }

    public GraphResource terrainSlab() {
        return this.terrainSlab;
    }
}
