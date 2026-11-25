package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex;

public class ChunkMeshFormats {
    public static final ChunkVertexType COMPACT = new CompactChunkVertex();
    public static final ChunkVertexType VANILLA_LIKE = new VanillaLikeChunkVertex();
    /** New-architecture Tier-A terrain format: 16-byte vertices, SectionDesc-driven draws. */
    public static final ChunkVertexType TERRAIN_A16 = new TerrainVertex16();
}
