package org.embeddedt.embeddium.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.iterator.ReversibleObjectArrayIterator;

import java.util.Set;

public class SortedRenderLists implements ChunkRenderListIterable {
    private static final SortedRenderLists EMPTY = new SortedRenderLists(new ObjectArrayList<>());

    private final ObjectArrayList<ChunkRenderList> lists;
    private final ReferenceOpenHashSet<TerrainRenderPass> passes;
    private final boolean hasSortedPass;

    SortedRenderLists(ObjectArrayList<ChunkRenderList> lists) {
        this.lists = lists;
        this.passes = getAllPassesInLists(lists);
        boolean sorted = false;
        for (TerrainRenderPass pass : this.passes) {
            if (pass.isSorted()) {
                sorted = true;
                break;
            }
        }
        this.hasSortedPass = sorted;
    }

    private static ReferenceOpenHashSet<TerrainRenderPass> getAllPassesInLists(ObjectArrayList<ChunkRenderList> lists) {
        ReferenceOpenHashSet<TerrainRenderPass> usedPasses = new ReferenceOpenHashSet<>();

        for (var list : lists) {
            usedPasses.addAll(list.getRegion().getPasses());
        }

        return usedPasses;
    }

    @Override
    public int getNumRegions() {
        return this.lists.size();
    }

    @Override
    public ChunkRenderList getRegion(int index) {
        return this.lists.get(index);
    }

    @Override
    public ReversibleObjectArrayIterator<ChunkRenderList> iterator(boolean reverse) {
        return new ReversibleObjectArrayIterator<>(this.lists, reverse);
    }

    @Override
    public boolean hasPass(TerrainRenderPass pass) {
        return this.passes.contains(pass);
    }

    public Set<TerrainRenderPass> getPasses() {
        return this.passes;
    }

    public boolean hasSortedPass() {
        return this.hasSortedPass;
    }

    public static SortedRenderLists empty() {
        return EMPTY;
    }
}
