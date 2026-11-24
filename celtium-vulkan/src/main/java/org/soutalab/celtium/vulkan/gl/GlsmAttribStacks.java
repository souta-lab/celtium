package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayDeque;

/**
 * GLSM stage 3, GAP-ST-6 (census S-13): the owner of the attribute stacks.
 * <p>
 * The snapshot type ({@link LegacyAttribSnapshot}, 235 lines) already exists; what the stub
 * owned was the push/pop itself - the server stack of snapshots and the client stack of
 * vertex-pointer tuples ({@code long[7]}: size, type, stride, offset, GL_VERTEX_ARRAY
 * enable, normalized, integer - the stub's exact layout, so the pop route can move without
 * a format change). Both stacks keep the stub's bound of 64: GL documents no limit, but an
 * unbalanced mod must not grow them forever. Pushes past the bound are dropped and counted.
 * <p>
 * The UNMODELLED groups (snapshot {@code UNMODELLED_GROUPS}: groups a push may select that
 * no snapshot field carries) are ledgered here by name-count: every push records which
 * unmodelled bits it asked for, so the merge phase can see whether any real caller selects
 * them before deciding their fate.
 */
public final class GlsmAttribStacks {
    public static final int MAX_DEPTH = 64;

    private static final GlsmAttribStacks CURRENT = new GlsmAttribStacks();

    public static GlsmAttribStacks current() { return CURRENT; }

    private final ArrayDeque<LegacyAttribSnapshot> server = new ArrayDeque<LegacyAttribSnapshot>();
    private final ArrayDeque<long[]> client = new ArrayDeque<long[]>();
    private int droppedPushes;
    private int unmodelledSelections;

    private GlsmAttribStacks() {
    }

    /** @return false when the bound was hit and the push was dropped (counted). */
    public boolean pushServer(LegacyAttribSnapshot snapshot) {
        if ((snapshot.mask & LegacyAttribSnapshot.UNMODELLED_GROUPS) != 0) this.unmodelledSelections++;
        if (this.server.size() >= MAX_DEPTH) {
            this.droppedPushes++;
            return false;
        }
        this.server.addLast(snapshot);
        return true;
    }

    /** @return the popped snapshot, or null on underflow (GL would raise STACK_UNDERFLOW). */
    public LegacyAttribSnapshot popServer() { return this.server.pollLast(); }

    public int serverDepth() { return this.server.size(); }

    public boolean pushClient(long[] tuple) {
        if (this.client.size() >= MAX_DEPTH) {
            this.droppedPushes++;
            return false;
        }
        this.client.addLast(tuple);
        return true;
    }

    public long[] popClient() { return this.client.pollLast(); }

    public int clientDepth() { return this.client.size(); }
    public int droppedPushes() { return this.droppedPushes; }

    /** Pushes whose mask selected at least one group no snapshot carries. */
    public int unmodelledSelections() { return this.unmodelledSelections; }
}
