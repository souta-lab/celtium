package org.soutalab.celtium.forge.terrain;

import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;

/**
 * The zero-based section mesher (design V040-FINAL-PLAN section 2.1): turns one
 * {@link WorldSectionSnapshot} into {@link PackedAxisQuad}s (full-cube faces), {@link GeneralQuad}s
 * (baked-model geometry and fluidlogged surfaces) and the pack-only {@code mc_Entity} auxiliary
 * column, bucketed by layer.
 * <p>
 * Exposed-face extraction is word-parallel (the "18&sup3; {@code long} SWAR" of the plan): the
 * snapshot is first reduced to per-direction occlusion bitboards and per-layer cell bitboards,
 * and each direction's exposed-face mask for the whole 5,832-cell snapshot falls out of one
 * shifted AND-NOT over 92 words - no per-cell neighbour walks. Two correction passes then handle
 * what bitboards cannot express: same-state merging (glass/water against themselves) and the
 * D-57 leaf predicates, both iterating only the set bits of their (small) candidate boards.
 * <p>
 * Greedy meshing merges each (direction, layer) face plane into rectangles of equal merge key
 * (texture, atlas cell, face light, tint, and - pack ON - {@code mc_Entity}). While a pack is in
 * use, faces of sway/fluid states skip greedy merging entirely and are emitted as single quads,
 * because the pack animates those vertices per original quad.
 * <p>
 * {@link GeneralQuad} flags as produced here: bit 0 = tint present, bits 8..31 = the tint
 * colour {@code 0xRRGGBB}. General-quad UVs are the 80 B contract's per-corner Q1.15
 * sprite-local UVs (Neon UV ruling NEON-UV-RULING-2026-11-28): baked-model quads carry their
 * real sub-rectangle / rotated UVs from the face table, and the synthetic fluid faces below
 * carry the canonical full-sprite assignment in corner order.
 * <p>
 * Per-corner smooth light (ledger R-107): alongside every quad the mesher emits two ints of
 * corner light - corner {@code c} at word {@code c >> 1}, bits {@code (c & 1) * 16}, holding
 * the sky sum (bits 0..5), the block sum (bits 6..11) and the AO step (bits 12..15). The sums
 * are the four vanilla sample values of {@code BlockModelRenderer}'s unweighted (full-cube)
 * branch added without the {@code >> 2}: the shader divides by 60 to recover the averaged
 * lightmap coordinate, and by 15 for the AO multiplier (the average of the four sampled
 * {@code getAmbientOcclusionLightValue}s, quantised to steps of 1/15 - exact for the vanilla
 * value set {0.2, 0.4, 0.6, 0.8, 1.0}). The 16 B / 80 B quad records are untouched: the column
 * rides the slab's auxiliary space (see {@link MeshBuffers#cornerLightColumn()}), and the pack
 * path, which reads the per-quad light out of the records as before, never sees it.
 * <p>
 * One instance is one worker's mesher: it holds scratch bitboards and allocates nothing per
 * section once warm. Not thread-safe; the build scheduler gives each platform thread its own.
 */
public final class SectionMesher {
    private static final int WORDS = (WorldSectionSnapshot.VOLUME + 63) / 64;
    /** The rendered surface height of a resting full fluid cell (vanilla water/lava). */
    private static final float FLUID_SURFACE = 0.875f;

    private final long[][] occl = new long[TerrainDirs.COUNT][WORDS];
    private final long[][] layerFull = new long[TerrainLayer.COUNT][WORDS];
    private final long[] leaves = new long[WORDS];
    private final long[] mergeable = new long[WORDS];
    private final long[] noGreedy = new long[WORDS];
    private final long[] shifted = new long[WORDS];
    private final long[] mask = new long[WORDS];
    private final long[] singles = new long[WORDS];

    private final long[] gridKeyA = new long[256];
    private final long[] gridKeyB = new long[256];
    private final boolean[] gridFilled = new boolean[256];
    private final boolean[] gridUsed = new boolean[256];

    private final BlockFaceData.ModelQuadView quadView = new BlockFaceData.ModelQuadView();
    private final float[] generalPositions = new float[12];
    /** Scratch for the quad currently being emitted: its four packed corner-light fields. */
    private final int[] cornerFields = new int[4];
    /** Scratch cell coordinates for the corner-light sampling of an axis rectangle. */
    private final int[] cornerCell = new int[3];

    /** Meshes one snapshot into {@code out} (reset first). */
    public void mesh(WorldSectionSnapshot snapshot, BlockFaceData data, MesherOptions options, MeshBuffers out) {
        out.reset(options.requirements().needsAuxiliaryColumn());
        buildBoards(snapshot, data);
        applySolidify(snapshot, data, options);
        emitAxisFaces(snapshot, data, options, out);
        emitGeneralQuads(snapshot, data, out);
        emitFluids(snapshot, data, out);
    }

    // ---------------------------------------------------------------------------------------------
    // Bitboard construction and corrections
    // ---------------------------------------------------------------------------------------------

    private void buildBoards(WorldSectionSnapshot snapshot, BlockFaceData data) {
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            java.util.Arrays.fill(occl[dir], 0L);
        }
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            java.util.Arrays.fill(layerFull[layer], 0L);
        }
        java.util.Arrays.fill(leaves, 0L);
        java.util.Arrays.fill(mergeable, 0L);
        java.util.Arrays.fill(noGreedy, 0L);

        int[] states = snapshot.states();
        for (int i = 0; i < WorldSectionSnapshot.VOLUME; i++) {
            int state = states[i];
            if (state == BlockFaceData.AIR || data.isAir(state)) {
                continue;
            }
            long bit = 1L << (i & 63);
            int word = i >>> 6;
            for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                if (data.occludes(state, dir)) {
                    occl[dir][word] |= bit;
                }
            }
            if (data.isFullCube(state)) {
                layerFull[data.layer(state)][word] |= bit;
            }
            if (data.isLeaves(state)) {
                leaves[word] |= bit;
            }
            if (data.cullsSameState(state)) {
                mergeable[word] |= bit;
            }
            if (data.noGreedyWhenPack(state)) {
                noGreedy[word] |= bit;
            }
        }
    }

    /**
     * The D-57 material swap, as board surgery: a surrounded leaf cell (SOLID modes, pack OFF)
     * leaves its natural layer board and joins the SOLID board, so its faces emit as solid.
     */
    private void applySolidify(WorldSectionSnapshot snapshot, BlockFaceData data, MesherOptions options) {
        if (options.leafMode() != NativeLeafCulling.Mode.SOLID
                && options.leafMode() != NativeLeafCulling.Mode.SOLID_AGGRESSIVE) {
            return;
        }
        if (options.packInUse()) {
            return;
        }
        for (int y = 1; y <= WorldSectionSnapshot.INNER; y++) {
            for (int z = 1; z <= WorldSectionSnapshot.INNER; z++) {
                for (int x = 1; x <= WorldSectionSnapshot.INNER; x++) {
                    int i = WorldSectionSnapshot.index(x, y, z);
                    if ((leaves[i >>> 6] & (1L << (i & 63))) == 0L) {
                        continue;
                    }
                    int state = snapshot.stateAt(i);
                    if (!data.isFullCube(state)) {
                        continue;
                    }
                    if (!NativeLeafCulling.isSurrounded(snapshot, data, x, y, z, options.leafMode())) {
                        continue;
                    }
                    int word = i >>> 6;
                    long bit = 1L << (i & 63);
                    layerFull[data.layer(state)][word] &= ~bit;
                    layerFull[TerrainLayer.SOLID.ordinal()][word] |= bit;
                    // The swap is a material change, not a layer relabel: the cell stops being
                    // leaves for suppression (its faces must emit - that is the swap's point) and
                    // starts occluding like the solid cube it now is.
                    leaves[word] &= ~bit;
                    for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                        occl[dir][word] |= bit;
                    }
                }
            }
        }
    }

    /** dst bit i := src bit (i + shift). Bits past the snapshot read as 0. */
    private static void shiftBoard(long[] src, int shift, long[] dst) {
        int wordShift = Math.floorDiv(shift, 64);
        int bitShift = Math.floorMod(shift, 64);
        for (int w = 0; w < WORDS; w++) {
            int s = w + wordShift;
            long value = 0L;
            if (s >= 0 && s < WORDS) {
                value = src[s] >>> bitShift;
                if (bitShift != 0 && s + 1 < WORDS) {
                    value |= src[s + 1] << (64 - bitShift);
                }
            }
            dst[w] = value;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Axis faces: exposure masks, corrections, greedy meshing
    // ---------------------------------------------------------------------------------------------

    private void emitAxisFaces(WorldSectionSnapshot snapshot, BlockFaceData data,
                               MesherOptions options, MeshBuffers out) {
        int[] states = snapshot.states();
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            int opp = TerrainDirs.opposite(dir);
            shiftBoard(occl[opp], WorldSectionSnapshot.offset(dir), shifted);
            for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
                for (int w = 0; w < WORDS; w++) {
                    mask[w] = layerFull[layer][w] & ~shifted[w];
                }
                correctEscapedFaces(data, states, dir);
                correctSameState(snapshot, states, dir);
                correctLeafSuppression(snapshot, data, options, dir);
                if (options.packInUse()) {
                    for (int w = 0; w < WORDS; w++) {
                        singles[w] = mask[w] & noGreedy[w];
                        mask[w] &= ~noGreedy[w];
                    }
                    emitSingles(snapshot, data, options, out, dir, layer);
                }
                if (options.greedyEnabled()) {
                    greedyLayer(snapshot, data, options, out, dir, layer);
                } else {
                    // K2 diagnostic arm (Gate G2-31): no merging at all - the surviving
                    // mask (after the pack's noGreedy separation above) goes out face by
                    // face, so the face set is the greedy arm's, only unmerged.
                    for (int w = 0; w < WORDS; w++) {
                        singles[w] = mask[w];
                    }
                    emitSingles(snapshot, data, options, out, dir, layer);
                }
            }
        }
    }

    /**
     * UV-escape suppression (ruling section 5.3): a full-cube cell whose face on this direction
     * escaped into the general pool (partial-rectangle or rotated/flipped UVs) loses its axis
     * face here; the general path emits the face's real quads instead.
     */
    private void correctEscapedFaces(BlockFaceData data, int[] states, int dir) {
        for (int w = 0; w < WORDS; w++) {
            long bits = mask[w];
            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int i = (w << 6) + bit;
                if (i < WorldSectionSnapshot.VOLUME && data.faceEscapesToGeneral(states[i], dir)) {
                    mask[w] &= ~(1L << bit);
                }
            }
        }
    }

    /** Same-state merging: a mergeable cell's face dies when the neighbour is the same state. */
    private void correctSameState(WorldSectionSnapshot snapshot, int[] states, int dir) {
        int offset = WorldSectionSnapshot.offset(dir);
        for (int w = 0; w < WORDS; w++) {
            long candidates = mask[w] & mergeable[w];
            while (candidates != 0L) {
                int bit = Long.numberOfTrailingZeros(candidates);
                candidates &= candidates - 1;
                int i = (w << 6) + bit;
                if (i < WorldSectionSnapshot.VOLUME && states[i + offset] == states[i]) {
                    mask[w] &= ~(1L << bit);
                }
            }
        }
    }

    /** D-57 face suppression over the leaf cells present in the current mask. */
    private void correctLeafSuppression(WorldSectionSnapshot snapshot, BlockFaceData data,
                                        MesherOptions options, int dir) {
        if (options.leafMode() == NativeLeafCulling.Mode.NONE) {
            return;
        }
        for (int w = 0; w < WORDS; w++) {
            long candidates = mask[w] & leaves[w];
            while (candidates != 0L) {
                int bit = Long.numberOfTrailingZeros(candidates);
                candidates &= candidates - 1;
                int i = (w << 6) + bit;
                if (i >= WorldSectionSnapshot.VOLUME) {
                    continue;
                }
                int x = i % WorldSectionSnapshot.SIZE;
                int z = (i / WorldSectionSnapshot.SIZE) % WorldSectionSnapshot.SIZE;
                int y = i / (WorldSectionSnapshot.SIZE * WorldSectionSnapshot.SIZE);
                if (NativeLeafCulling.shouldSuppressFace(snapshot, data, x, y, z, dir, options.leafMode())) {
                    mask[w] &= ~(1L << bit);
                }
            }
        }
    }

    private void emitSingles(WorldSectionSnapshot snapshot, BlockFaceData data,
                             MesherOptions options, MeshBuffers out, int dir, int layer) {
        for (int w = 0; w < WORDS; w++) {
            long bits = singles[w];
            while (bits != 0L) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int i = (w << 6) + bit;
                if (i >= WorldSectionSnapshot.VOLUME) {
                    continue;
                }
                int x = i % WorldSectionSnapshot.SIZE;
                int z = (i / WorldSectionSnapshot.SIZE) % WorldSectionSnapshot.SIZE;
                int y = i / (WorldSectionSnapshot.SIZE * WorldSectionSnapshot.SIZE);
                if (!WorldSectionSnapshot.isInner(x, y, z)) {
                    continue;
                }
                emitQuad(snapshot, data, options, out, dir, layer,
                        x - 1, y - 1, z - 1, 1, 1);
            }
        }
    }

    private void greedyLayer(WorldSectionSnapshot snapshot, BlockFaceData data,
                             MesherOptions options, MeshBuffers out, int dir, int layer) {
        int axis = TerrainDirs.AXIS[dir];
        int uAxis = TerrainDirs.uAxis(dir);
        int vAxis = TerrainDirs.vAxis(dir);
        for (int a = 0; a < WorldSectionSnapshot.INNER; a++) {
            buildGrid(snapshot, data, options, dir, axis, uAxis, vAxis, a);
            for (int v = 0; v < WorldSectionSnapshot.INNER; v++) {
                for (int u = 0; u < WorldSectionSnapshot.INNER; u++) {
                    int g = v * 16 + u;
                    if (!gridFilled[g] || gridUsed[g]) {
                        continue;
                    }
                    long keyA = gridKeyA[g];
                    long keyB = gridKeyB[g];
                    int width = 1;
                    while (u + width < WorldSectionSnapshot.INNER) {
                        int n = v * 16 + u + width;
                        if (!gridFilled[n] || gridUsed[n] || gridKeyA[n] != keyA || gridKeyB[n] != keyB) {
                            break;
                        }
                        width++;
                    }
                    int height = 1;
                    grow:
                    while (v + height < WorldSectionSnapshot.INNER) {
                        for (int du = 0; du < width; du++) {
                            int n = (v + height) * 16 + u + du;
                            if (!gridFilled[n] || gridUsed[n] || gridKeyA[n] != keyA || gridKeyB[n] != keyB) {
                                break grow;
                            }
                        }
                        height++;
                    }
                    for (int dv = 0; dv < height; dv++) {
                        for (int du = 0; du < width; du++) {
                            gridUsed[(v + dv) * 16 + u + du] = true;
                        }
                    }
                    int[] cell = new int[3];
                    cell[axis] = a;
                    cell[uAxis] = u;
                    cell[vAxis] = v;
                    emitQuad(snapshot, data, options, out, dir, layer,
                            cell[0], cell[1], cell[2], width, height);
                }
            }
        }
    }

    /** Fills the 16&times;16 merge-key grid of one (dir, slice) from the current face mask. */
    private void buildGrid(WorldSectionSnapshot snapshot, BlockFaceData data, MesherOptions options,
                           int dir, int axis, int uAxis, int vAxis, int slice) {
        java.util.Arrays.fill(gridFilled, false);
        java.util.Arrays.fill(gridUsed, false);
        int[] cell = new int[3];
        cell[axis] = slice + 1;
        for (int v = 0; v < WorldSectionSnapshot.INNER; v++) {
            for (int u = 0; u < WorldSectionSnapshot.INNER; u++) {
                cell[uAxis] = u + 1;
                cell[vAxis] = v + 1;
                int i = WorldSectionSnapshot.index(cell[0], cell[1], cell[2]);
                if ((mask[i >>> 6] & (1L << (i & 63))) == 0L) {
                    continue;
                }
                int g = v * 16 + u;
                gridFilled[g] = true;
                int state = snapshot.stateAt(i);
                int texture = data.faceTexture(state, dir);
                int cellKey = data.faceCell(state, dir);
                int sky = snapshot.faceSkyLight(i, dir);
                int block = snapshot.faceBlockLight(i, dir);
                int tint = data.faceUsesTint(state, dir) ? snapshot.tintAt(i) : WorldSectionSnapshot.NO_TINT;
                long keyA = (texture & 0xFFFFFL)
                        | ((long) (cellKey & 0xFFFF) << 20)
                        | ((long) sky << 36)
                        | ((long) block << 40)
                        | ((tint != WorldSectionSnapshot.NO_TINT ? 1L : 0L) << 44);
                if (options.requirements().mcEntity()) {
                    keyA |= (long) (data.mcEntityId(state) & 0xFFFF) << 45;
                }
                gridKeyA[g] = keyA;
                gridKeyB[g] = tint == WorldSectionSnapshot.NO_TINT ? 0L : (tint & 0xFFFFFFL);
            }
        }
    }

    /**
     * Emits one axis quad. {@code cellX/cellY/cellZ} are the rectangle's minimum cell in
     * section-local coordinates; {@code extentU/extentV} its size along the face's U/V axes.
     */
    private void emitQuad(WorldSectionSnapshot snapshot, BlockFaceData data, MesherOptions options,
                          MeshBuffers out, int dir, int layer,
                          int cellX, int cellY, int cellZ, int extentU, int extentV) {
        int axis = TerrainDirs.AXIS[dir];
        int uAxis = TerrainDirs.uAxis(dir);
        int vAxis = TerrainDirs.vAxis(dir);
        int[] cell = {cellX, cellY, cellZ};
        int snapIndex = WorldSectionSnapshot.index(cellX + 1, cellY + 1, cellZ + 1);
        int state = snapshot.stateAt(snapIndex);

        int[] corner = new int[3];
        corner[axis] = TerrainDirs.NEGATIVE[dir] ? cell[axis] : cell[axis] + 1;
        corner[uAxis] = cell[uAxis];
        corner[vAxis] = cell[vAxis];

        int tint = data.faceUsesTint(state, dir) ? snapshot.tintAt(snapIndex) : WorldSectionSnapshot.NO_TINT;
        int flags = tint != WorldSectionSnapshot.NO_TINT ? PackedAxisQuad.FLAG_TINT_PRESENT : 0;
        int cellKey = data.faceCell(state, dir);
        PackedAxisQuad quad = PackedAxisQuad.of(
                corner[0], corner[1], corner[2],
                axis, TerrainDirs.NEGATIVE[dir],
                extentU, extentV,
                data.faceTexture(state, dir),
                cellKey & 0xFF, (cellKey >>> 8) & 0xFF,
                snapshot.faceSkyLight(snapIndex, dir), snapshot.faceBlockLight(snapIndex, dir),
                TerrainDirs.SHADE[dir],
                tint != WorldSectionSnapshot.NO_TINT ? (tint >>> 16) & 0xFF : 0,
                tint != WorldSectionSnapshot.NO_TINT ? (tint >>> 8) & 0xFF : 0,
                tint != WorldSectionSnapshot.NO_TINT ? tint & 0xFF : 0,
                flags);
        short aux = options.requirements().mcEntity() ? data.mcEntityId(state) : 0;
        axisCornerFields(snapshot, data, dir, cellX, cellY, cellZ, extentU, extentV);
        out.addAxisQuad(layer, quad, aux,
                cornerFields[0] | (cornerFields[1] << 16),
                cornerFields[2] | (cornerFields[3] << 16));
    }

    // ---------------------------------------------------------------------------------------------
    // Per-corner smooth light (R-107): vanilla BlockModelRenderer's unweighted branch
    // ---------------------------------------------------------------------------------------------

    /**
     * Vanilla {@code EnumNeighborInfo.corners[0]} per face direction, as {@link TerrainDirs}
     * indices: the "A" tangent reference. DOWN {WEST, ...}, UP {EAST, ...}, NORTH {UP, ...},
     * SOUTH {WEST, ...}, WEST {UP, ...}, EAST {DOWN, ...} (decompiled 1.12.2
     * {@code BlockModelRenderer.EnumNeighborInfo}, read from the MCP sources jar).
     */
    private static final int[] VANILLA_CORNER_A = {
            TerrainDirs.WEST, TerrainDirs.EAST, TerrainDirs.UP,
            TerrainDirs.WEST, TerrainDirs.UP, TerrainDirs.DOWN};

    /** Vanilla {@code EnumNeighborInfo.corners[2]} per face direction: the "B" tangent reference. */
    private static final int[] VANILLA_CORNER_B = {
            TerrainDirs.NORTH, TerrainDirs.NORTH, TerrainDirs.EAST,
            TerrainDirs.DOWN, TerrainDirs.NORTH, TerrainDirs.NORTH};

    private static int dirComponent(int dir, int axis) {
        return axis == 0 ? TerrainDirs.DX[dir] : axis == 1 ? TerrainDirs.DY[dir] : TerrainDirs.DZ[dir];
    }

    /**
     * One light sample, packed as {@code sky | block << 8}: the snapshot's stored light at the
     * cell (the scanner's arrays already carry vanilla's neighbour-brightness rule), with the
     * block channel floored at the face block's own emission - {@code World.getCombinedLight}
     * semantics.
     */
    private static int sampleLight(WorldSectionSnapshot snapshot, int index, int emission) {
        int sky = snapshot.skyLightAt(index);
        int block = Math.max(snapshot.blockLightAt(index), emission);
        return sky | (block << 8);
    }

    /**
     * Translucency of the cell at snapshot coordinates {@code (x, y, z)} for the diagonal
     * sample's gate. Coordinates outside the captured 18&sup3; read as translucent: the gate
     * cells one step beyond the apron are the only out-of-range reads the corner computation
     * makes, and vanilla (which reads the real world there) takes the diagonal sample unless
     * both beyond cells are opaque - the translucent reading keeps a section-edge corner from
     * darkening on missing evidence.
     */
    private static boolean isTranslucentAt(WorldSectionSnapshot snapshot, BlockFaceData data,
                                           int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= WorldSectionSnapshot.SIZE
                || y >= WorldSectionSnapshot.SIZE || z >= WorldSectionSnapshot.SIZE) {
            return true;
        }
        return data.isTranslucent(snapshot.stateAt(WorldSectionSnapshot.index(x, y, z)));
    }

    /**
     * The packed corner-light field of one face corner, vanilla's unweighted branch
     * ({@code BlockModelRenderer.updateVertexBrightness} with shape bits 0 set / 1 clear - the
     * shape every full-cube face takes): the corner averages the face cell F (the cell the
     * face points into), the two edge cells F + A-side and F + B-side, and the quadrant
     * diagonal, with a sample whose packed light is 0 replaced by F's sample
     * ({@code getAoBrightness}). The diagonal is taken only when the cell just beyond at
     * least one edge cell (one more step along the face direction) is translucent; otherwise
     * the A-side edge sample stands in for it - for both the light and the AO value, as in
     * vanilla. {@code (fx, fy, fz)} are F's snapshot coordinates; {@code signA}/{@code signB}
     * say which side of F the corner sits on along vanilla's A/B tangent axes (+1 toward
     * {@code corners[0]}/{@code corners[2]}, -1 toward their opposites, 0 centred).
     */
    private int cornerLightField(WorldSectionSnapshot snapshot, BlockFaceData data,
                                 int fx, int fy, int fz, int dir, int signA, int signB,
                                 int emission) {
        int a0 = VANILLA_CORNER_A[dir];
        int b0 = VANILLA_CORNER_B[dir];
        int sax = signA > 0 ? TerrainDirs.DX[a0] : signA < 0 ? -TerrainDirs.DX[a0] : 0;
        int say = signA > 0 ? TerrainDirs.DY[a0] : signA < 0 ? -TerrainDirs.DY[a0] : 0;
        int saz = signA > 0 ? TerrainDirs.DZ[a0] : signA < 0 ? -TerrainDirs.DZ[a0] : 0;
        int sbx = signB > 0 ? TerrainDirs.DX[b0] : signB < 0 ? -TerrainDirs.DX[b0] : 0;
        int sby = signB > 0 ? TerrainDirs.DY[b0] : signB < 0 ? -TerrainDirs.DY[b0] : 0;
        int sbz = signB > 0 ? TerrainDirs.DZ[b0] : signB < 0 ? -TerrainDirs.DZ[b0] : 0;
        int fIndex = WorldSectionSnapshot.index(fx, fy, fz);
        int eaIndex = WorldSectionSnapshot.index(fx + sax, fy + say, fz + saz);
        int ebIndex = WorldSectionSnapshot.index(fx + sbx, fy + sby, fz + sbz);
        int diagIndex = WorldSectionSnapshot.index(fx + sax + sbx, fy + say + sby, fz + saz + sbz);
        boolean diagUsed = isTranslucentAt(snapshot, data,
                fx + sax + TerrainDirs.DX[dir], fy + say + TerrainDirs.DY[dir],
                fz + saz + TerrainDirs.DZ[dir])
                || isTranslucentAt(snapshot, data,
                fx + sbx + TerrainDirs.DX[dir], fy + sby + TerrainDirs.DY[dir],
                fz + sbz + TerrainDirs.DZ[dir]);
        int center = sampleLight(snapshot, fIndex, emission);
        int ea = sampleLight(snapshot, eaIndex, emission);
        int eb = sampleLight(snapshot, ebIndex, emission);
        int dg = diagUsed ? sampleLight(snapshot, diagIndex, emission) : ea;
        if (ea == 0) {
            ea = center;
        }
        if (eb == 0) {
            eb = center;
        }
        if (dg == 0) {
            dg = center;
        }
        int skySum = (center & 0xFF) + (ea & 0xFF) + (eb & 0xFF) + (dg & 0xFF);
        int blockSum = (center >> 8) + (ea >> 8) + (eb >> 8) + (dg >> 8);
        float aoEa = data.aoLightValue(snapshot.stateAt(eaIndex));
        float aoSum = data.aoLightValue(snapshot.stateAt(fIndex)) + aoEa
                + data.aoLightValue(snapshot.stateAt(ebIndex))
                + (diagUsed ? data.aoLightValue(snapshot.stateAt(diagIndex)) : aoEa);
        int ao = Math.max(0, Math.min(15, Math.round(aoSum * 0.25f * 15.0f)));
        return skySum | (blockSum << 6) | (ao << 12);
    }

    /**
     * The corner fields of an axis rectangle, in slot order {@code cu + 2 * cv} (cu along the
     * face's +U axis, cv along +V, from the rectangle's minimum corner) - the order the axis
     * vertex shader's post-mirror corner indexes. Each slot samples at the rectangle's own
     * corner cell: merged rectangles therefore carry the true corner values of their four
     * outer corners and the GPU interpolates between them, where vanilla (which never merges)
     * would interpolate per unit face.
     */
    private void axisCornerFields(WorldSectionSnapshot snapshot, BlockFaceData data, int dir,
                                  int cellX, int cellY, int cellZ, int extentU, int extentV) {
        int uAxis = TerrainDirs.uAxis(dir);
        int vAxis = TerrainDirs.vAxis(dir);
        int a0 = VANILLA_CORNER_A[dir];
        int b0 = VANILLA_CORNER_B[dir];
        int axisA = TerrainDirs.AXIS[a0];
        int axisB = TerrainDirs.AXIS[b0];
        int compA = dirComponent(a0, axisA);
        int compB = dirComponent(b0, axisB);
        int[] cc = cornerCell;
        cc[0] = cellX;
        cc[1] = cellY;
        cc[2] = cellZ;
        for (int cv = 0; cv <= 1; cv++) {
            for (int cu = 0; cu <= 1; cu++) {
                cc[uAxis] = (uAxis == 0 ? cellX : uAxis == 1 ? cellY : cellZ) + (cu == 1 ? extentU - 1 : 0);
                cc[vAxis] = (vAxis == 0 ? cellX : vAxis == 1 ? cellY : cellZ) + (cv == 1 ? extentV - 1 : 0);
                int emission = data.lightValue(snapshot.stateAt(
                        WorldSectionSnapshot.index(cc[0] + 1, cc[1] + 1, cc[2] + 1)));
                int signU = cu == 1 ? 1 : -1;
                int signV = cv == 1 ? 1 : -1;
                int signA = (axisA == uAxis ? signU : signV) * compA;
                int signB = (axisB == uAxis ? signU : signV) * compB;
                cornerFields[cu + 2 * cv] = cornerLightField(snapshot, data,
                        cc[0] + 1 + TerrainDirs.DX[dir], cc[1] + 1 + TerrainDirs.DY[dir],
                        cc[2] + 1 + TerrainDirs.DZ[dir], dir, signA, signB, emission);
            }
        }
    }

    /**
     * The corner fields of a general quad, in the quad's own corner order (the order the
     * general vertex shader's {@code CORNER_INDEX} walks). {@code faceIndex} is the snapshot
     * index of the face cell F (the same cell the quad's flat light is read from);
     * {@code positions} are the quad's section-local corner positions and
     * {@code (originX, originY, originZ)} its block's section-local minimum corner, so a
     * corner's quadrant signs fall out of its position relative to the block centre. A corner
     * centred on a tangent axis (a mid-face vertex of a partial quad) takes that axis' edge
     * sample at F itself. Vanilla computes partial quads through its weighted branch; the
     * geometric corner form here is the same four-sample average evaluated at the corner's
     * actual quadrant, which is exact for the flush full-face quads models overwhelmingly
     * emit and a strict improvement over one flat value for the rest.
     */
    private void generalCornerFields(WorldSectionSnapshot snapshot, BlockFaceData data,
                                     int faceIndex, int dir, int emission, float[] positions,
                                     int originX, int originY, int originZ) {
        if (dir < 0) {
            int center = sampleLight(snapshot, faceIndex, emission);
            int ao = Math.max(0, Math.min(15, Math.round(
                    data.aoLightValue(snapshot.stateAt(faceIndex)) * 15.0f)));
            int flat = ((center & 0xFF) * 4) | (((center >> 8) * 4) << 6) | (ao << 12);
            cornerFields[0] = cornerFields[1] = cornerFields[2] = cornerFields[3] = flat;
            return;
        }
        int fx = faceIndex % WorldSectionSnapshot.SIZE;
        int fz = (faceIndex / WorldSectionSnapshot.SIZE) % WorldSectionSnapshot.SIZE;
        int fy = faceIndex / (WorldSectionSnapshot.SIZE * WorldSectionSnapshot.SIZE);
        int a0 = VANILLA_CORNER_A[dir];
        int b0 = VANILLA_CORNER_B[dir];
        int axisA = TerrainDirs.AXIS[a0];
        int axisB = TerrainDirs.AXIS[b0];
        int compA = dirComponent(a0, axisA);
        int compB = dirComponent(b0, axisB);
        for (int c = 0; c < 4; c++) {
            float da = (positions[c * 3 + axisA] - originAlong(axisA, originX, originY, originZ)
                    - 0.5f) * compA;
            float db = (positions[c * 3 + axisB] - originAlong(axisB, originX, originY, originZ)
                    - 0.5f) * compB;
            int signA = da > 1.0e-4f ? 1 : da < -1.0e-4f ? -1 : 0;
            int signB = db > 1.0e-4f ? 1 : db < -1.0e-4f ? -1 : 0;
            cornerFields[c] = cornerLightField(snapshot, data, fx, fy, fz, dir,
                    signA, signB, emission);
        }
    }

    private static int originAlong(int axis, int originX, int originY, int originZ) {
        return axis == 0 ? originX : axis == 1 ? originY : originZ;
    }

    // ---------------------------------------------------------------------------------------------
    // General quads: baked-model geometry and fluidlogged surfaces
    // ---------------------------------------------------------------------------------------------

    private void emitGeneralQuads(WorldSectionSnapshot snapshot, BlockFaceData data, MeshBuffers out) {
        for (int y = 1; y <= WorldSectionSnapshot.INNER; y++) {
            for (int z = 1; z <= WorldSectionSnapshot.INNER; z++) {
                for (int x = 1; x <= WorldSectionSnapshot.INNER; x++) {
                    int i = WorldSectionSnapshot.index(x, y, z);
                    int state = snapshot.stateAt(i);
                    if (state == BlockFaceData.AIR || data.isAir(state)) {
                        continue;
                    }
                    int count = data.generalQuadCount(state);
                    if (count == 0) {
                        continue;
                    }
                    int layer = data.layer(state);
                    int tint = snapshot.tintAt(i);
                    for (int q = 0; q < count; q++) {
                        data.readGeneralQuad(state, q, quadView);
                        int faceDir = dominantDir(quadView.normalPacked);
                        int lightIndex = faceDir < 0 ? i : i + WorldSectionSnapshot.offset(faceDir);
                        int sky = snapshot.skyLightAt(lightIndex);
                        int block = snapshot.blockLightAt(lightIndex);
                        int lightWord = (sky * 17) | ((block * 17) << 8);
                        int flags = 0;
                        // A liquid's quads take the resolved tint even without a model
                        // tint index: vanilla's fluid renderer applies the block colour
                        // handlers to fluids unconditionally, and a liquid's model quads
                        // never carry a tint index for usesTint to see.
                        if ((quadView.usesTint || data.isLiquid(state))
                                && tint != WorldSectionSnapshot.NO_TINT) {
                            flags = 1 | ((tint & 0xFFFFFF) << 8);
                        }
                        for (int c = 0; c < 4; c++) {
                            generalPositions[c * 3] = quadView.positions[c * 3] + (x - 1);
                            generalPositions[c * 3 + 1] = quadView.positions[c * 3 + 1] + (y - 1);
                            generalPositions[c * 3 + 2] = quadView.positions[c * 3 + 2] + (z - 1);
                        }
                        generalCornerFields(snapshot, data, lightIndex, faceDir,
                                data.lightValue(state), generalPositions, x - 1, y - 1, z - 1);
                        out.addGeneralQuad(layer, generalPositions, quadView.textureIndex,
                                lightWord, quadView.normalPacked, flags, quadView.uvPacked,
                                cornerFields[0] | (cornerFields[1] << 16),
                                cornerFields[2] | (cornerFields[3] << 16));
                    }
                }
            }
        }
    }

    /**
     * The face direction a general quad's light is read along: the dominant axis of its
     * normal, or -1 when the normal is (near-)zero and the quad takes its own cell's light.
     * The light cell is the neighbour in this direction - the cell the quad's face points
     * into.
     */
    private static int dominantDir(int normalPacked) {
        float nx = GeneralQuad.unpackNormalComponent(normalPacked, 0);
        float ny = GeneralQuad.unpackNormalComponent(normalPacked, 10);
        float nz = GeneralQuad.unpackNormalComponent(normalPacked, 20);
        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float az = Math.abs(nz);
        if (ax >= ay && ax >= az) {
            if (ax < 0.01f) {
                return -1;
            }
            return nx > 0 ? TerrainDirs.EAST : TerrainDirs.WEST;
        } else if (ay >= az) {
            return ny > 0 ? TerrainDirs.UP : TerrainDirs.DOWN;
        }
        return nz > 0 ? TerrainDirs.SOUTH : TerrainDirs.NORTH;
    }

    /**
     * Fluidlogged surfaces: a cell whose logged fluid differs from its block emits the fluid's
     * faces as general quads (the surface sits at {@link #FLUID_SURFACE}, which the packed axis
     * form cannot express). A face is dropped when the neighbouring block occludes it or the
     * neighbouring cell logs the same fluid; the top face also drops when the cell above logs
     * the same fluid. Side faces span from the cell floor to the surface, or the full height
     * when the same fluid continues above.
     */
    private void emitFluids(WorldSectionSnapshot snapshot, BlockFaceData data, MeshBuffers out) {
        int[] fluids = snapshot.fluids();
        boolean any = false;
        for (int f : fluids) {
            if (f != 0) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        for (int y = 1; y <= WorldSectionSnapshot.INNER; y++) {
            for (int z = 1; z <= WorldSectionSnapshot.INNER; z++) {
                for (int x = 1; x <= WorldSectionSnapshot.INNER; x++) {
                    int i = WorldSectionSnapshot.index(x, y, z);
                    int fluid = fluids[i];
                    if (fluid == 0 || data.isAir(fluid)) {
                        continue;
                    }
                    int layer = data.layer(fluid);
                    int tint = snapshot.tintAt(i);
                    int flags = tint != WorldSectionSnapshot.NO_TINT ? 1 | ((tint & 0xFFFFFF) << 8) : 0;
                    boolean sameAbove = fluids[i + WorldSectionSnapshot.offset(TerrainDirs.UP)] == fluid;
                    float top = sameAbove ? 1.0f : FLUID_SURFACE;
                    for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                        int ni = i + WorldSectionSnapshot.offset(dir);
                        int neighborBlock = snapshot.stateAt(ni);
                        if (!data.isAir(neighborBlock) && data.occludes(neighborBlock, TerrainDirs.opposite(dir))) {
                            continue;
                        }
                        if (fluids[ni] == fluid) {
                            continue;
                        }
                        int sky = snapshot.skyLightAt(ni);
                        int block = snapshot.blockLightAt(ni);
                        int lightWord = (sky * 17) | ((block * 17) << 8);
                        writeFluidFace(x - 1, y - 1, z - 1, dir, top);
                        generalCornerFields(snapshot, data, ni, dir, data.lightValue(fluid),
                                generalPositions, x - 1, y - 1, z - 1);
                        out.addGeneralQuad(layer, generalPositions, data.faceTexture(fluid, dir),
                                lightWord, GeneralQuad.packNormal(
                                        TerrainDirs.DX[dir], TerrainDirs.DY[dir], TerrainDirs.DZ[dir]), flags,
                                GeneralQuad.FULL_SPRITE_UV_PACKED,
                                cornerFields[0] | (cornerFields[1] << 16),
                                cornerFields[2] | (cornerFields[3] << 16));
                    }
                }
            }
        }
    }

    /** Writes one fluid face's corners (section-local), wound so (c1-c0)&times;(c3-c0) is the outward normal. */
    private void writeFluidFace(int x, int y, int z, int dir, float top) {
        float x0 = x;
        float x1 = x + 1.0f;
        float y0 = y;
        float y1 = y + top;
        float z0 = z;
        float z1 = z + 1.0f;
        float[] p = generalPositions;
        switch (dir) {
            case TerrainDirs.UP -> {
                float h = y + FLUID_SURFACE;
                setCorner(p, 0, x0, h, z0);
                setCorner(p, 1, x0, h, z1);
                setCorner(p, 2, x1, h, z1);
                setCorner(p, 3, x1, h, z0);
            }
            case TerrainDirs.DOWN -> {
                setCorner(p, 0, x0, y0, z0);
                setCorner(p, 1, x1, y0, z0);
                setCorner(p, 2, x1, y0, z1);
                setCorner(p, 3, x0, y0, z1);
            }
            case TerrainDirs.NORTH -> {
                setCorner(p, 0, x1, y0, z0);
                setCorner(p, 1, x0, y0, z0);
                setCorner(p, 2, x0, y1, z0);
                setCorner(p, 3, x1, y1, z0);
            }
            case TerrainDirs.SOUTH -> {
                setCorner(p, 0, x0, y0, z1);
                setCorner(p, 1, x1, y0, z1);
                setCorner(p, 2, x1, y1, z1);
                setCorner(p, 3, x0, y1, z1);
            }
            case TerrainDirs.WEST -> {
                setCorner(p, 0, x0, y0, z0);
                setCorner(p, 1, x0, y0, z1);
                setCorner(p, 2, x0, y1, z1);
                setCorner(p, 3, x0, y1, z0);
            }
            case TerrainDirs.EAST -> {
                setCorner(p, 0, x1, y0, z1);
                setCorner(p, 1, x1, y0, z0);
                setCorner(p, 2, x1, y1, z0);
                setCorner(p, 3, x1, y1, z1);
            }
            default -> throw new IllegalArgumentException("dir " + dir);
        }
    }

    private static void setCorner(float[] p, int corner, float x, float y, float z) {
        p[corner * 3] = x;
        p[corner * 3 + 1] = y;
        p[corner * 3 + 2] = z;
    }
}
