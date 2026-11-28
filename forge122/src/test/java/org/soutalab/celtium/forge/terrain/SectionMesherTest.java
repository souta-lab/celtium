package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zero-based mesher's behaviour tables, against a hand-built {@link BlockFaceData}:
 * exposure culling, greedy merging and its boundaries, same-state merging, the D-57 leaf
 * decisions, the pack auxiliary column, pack-ON greedy exclusion, general quads and fluids.
 */
class SectionMesherTest {

    // State ids in the stub table.
    private static final int STONE = 1;
    private static final int STONE2 = 2;
    private static final int GLASS = 3;
    private static final int LEAVES = 4;
    private static final int TINT = 5;
    private static final int MODEL = 6;
    private static final int SWAY = 7;
    private static final int FLUID = 8;

    /** A configurable stub table. Defaults describe an opaque stone cube. */
    static final class Stub implements BlockFaceData {
        static final class Row {
            boolean fullCube = true;
            boolean occludes = true;
            boolean cullsSame = false;
            int layer = TerrainLayer.SOLID.ordinal();
            int texture = 10;
            boolean tintUp = false;
            boolean leaves = false;
            boolean sideSolid = true;
            boolean noGreedy = false;
            short mcEntity = 0;
            float[][] quads = new float[0][];
            int quadTexture = 0;
            boolean[] generalFaces = new boolean[6];
            int[] quadUvs = null;
            float ao = 1.0f;
            boolean translucent = false;
            int emission = 0;
        }

        final Row[] rows = new Row[16];

        Stub() {
            for (int i = 0; i < rows.length; i++) {
                rows[i] = new Row();
            }
            // State 0 is air: it occludes nothing and is solid on no side (the real table's
            // air row, without which every leaf looks "surrounded" by air).
            rows[0].fullCube = false;
            rows[0].occludes = false;
            rows[0].sideSolid = false;
            rows[0].translucent = true;
            rows[STONE].texture = 10;
            rows[STONE].ao = 0.2f;
            rows[STONE2].texture = 11;
            rows[GLASS].occludes = false;
            rows[GLASS].cullsSame = true;
            rows[GLASS].layer = TerrainLayer.CUTOUT.ordinal();
            rows[GLASS].texture = 20;
            rows[LEAVES].occludes = false;
            rows[LEAVES].layer = TerrainLayer.CUTOUT.ordinal();
            rows[LEAVES].texture = 30;
            rows[LEAVES].leaves = true;
            rows[LEAVES].sideSolid = false;
            rows[TINT].texture = 40;
            rows[TINT].tintUp = true;
            rows[MODEL].fullCube = false;
            rows[MODEL].occludes = false;
            rows[MODEL].quads = new float[][]{{
                    0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0}};
            rows[MODEL].quadTexture = 60;
            rows[SWAY].layer = TerrainLayer.CUTOUT.ordinal();
            rows[SWAY].texture = 50;
            rows[SWAY].noGreedy = true;
            rows[FLUID].fullCube = false;
            rows[FLUID].occludes = false;
            rows[FLUID].layer = TerrainLayer.TRANSLUCENT.ordinal();
            rows[FLUID].texture = 70;
        }

        @Override
        public boolean isAir(int state) {
            return state == 0;
        }

        @Override
        public boolean isFullCube(int state) {
            return rows[state].fullCube;
        }

        @Override
        public boolean occludes(int state, int dir) {
            return rows[state].occludes;
        }

        @Override
        public boolean cullsSameState(int state) {
            return rows[state].cullsSame;
        }

        @Override
        public int layer(int state) {
            return rows[state].layer;
        }

        @Override
        public int faceTexture(int state, int dir) {
            return rows[state].texture;
        }

        @Override
        public int faceCell(int state, int dir) {
            return 0;
        }

        @Override
        public boolean faceUsesTint(int state, int dir) {
            return rows[state].tintUp && dir == TerrainDirs.UP;
        }

        @Override
        public boolean faceEscapesToGeneral(int state, int dir) {
            return rows[state].generalFaces[dir];
        }

        @Override
        public boolean isLeaves(int state) {
            return rows[state].leaves;
        }

        @Override
        public boolean isSideSolid(int state, int dir) {
            return rows[state].sideSolid;
        }

        @Override
        public boolean noGreedyWhenPack(int state) {
            return rows[state].noGreedy;
        }

        @Override
        public short mcEntityId(int state) {
            return rows[state].mcEntity;
        }

        @Override
        public int lightValue(int state) {
            return rows[state].emission;
        }

        @Override
        public float aoLightValue(int state) {
            return rows[state].ao;
        }

        @Override
        public boolean isTranslucent(int state) {
            return rows[state].translucent;
        }

        @Override
        public int generalQuadCount(int state) {
            return rows[state].quads.length;
        }

        @Override
        public void readGeneralQuad(int state, int quadIndex, ModelQuadView out) {
            System.arraycopy(rows[state].quads[quadIndex], 0, out.positions, 0, 12);
            out.normalPacked = GeneralQuad.packNormal(0, 1, 0);
            out.textureIndex = rows[state].quadTexture;
            out.usesTint = false;
            out.noGreedyWhenPack = rows[state].noGreedy;
            int[] uvs = rows[state].quadUvs != null ? rows[state].quadUvs
                    : GeneralQuad.FULL_SPRITE_UV_PACKED;
            System.arraycopy(uvs, 0, out.uvPacked, 0, 4);
        }
    }

    private static WorldSectionSnapshot snapshot() {
        WorldSectionSnapshot snap = new WorldSectionSnapshot();
        snap.fill(0, 0, 0);
        java.util.Arrays.fill(snap.skyLight(), (byte) 15);
        java.util.Arrays.fill(snap.blockLight(), (byte) 0);
        return snap;
    }

    /** Sets a cell by section-local coordinates (0..15). */
    private static void set(WorldSectionSnapshot snap, int x, int y, int z, int state) {
        snap.states()[WorldSectionSnapshot.index(x + 1, y + 1, z + 1)] = state;
    }

    private static MeshBuffers mesh(WorldSectionSnapshot snap, BlockFaceData data, MesherOptions options) {
        MeshBuffers out = new MeshBuffers();
        new SectionMesher().mesh(snap, data, options, out);
        return out;
    }

    private static List<PackedAxisQuad> quadsWithTexture(MeshBuffers out, int layer, int texture) {
        List<PackedAxisQuad> found = new ArrayList<>();
        for (int i = 0; i < out.axisCount(layer); i++) {
            PackedAxisQuad quad = out.axisQuad(layer, i);
            if (quad.textureIndex() == texture) {
                found.add(quad);
            }
        }
        return found;
    }

    @Test
    void emptySectionEmitsNothing() {
        MeshBuffers out = mesh(snapshot(), new Stub(), MesherOptions.PACK_OFF);
        assertEquals(0, out.axisTotal());
        assertEquals(0, out.generalTotal());
    }

    @Test
    void singleCubeEmitsSixFaces() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        assertEquals(6, out.axisCount(TerrainLayer.SOLID.ordinal()));
        List<PackedAxisQuad> up = new ArrayList<>();
        for (int i = 0; i < out.axisCount(0); i++) {
            PackedAxisQuad q = out.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                up.add(q);
            }
        }
        assertEquals(1, up.size());
        PackedAxisQuad quad = up.get(0);
        assertEquals(8, quad.minX());
        assertEquals(9, quad.minY());
        assertEquals(8, quad.minZ());
        assertEquals(1, quad.extentU());
        assertEquals(1, quad.extentV());
        assertEquals(10, quad.textureIndex());
        assertEquals(255, quad.shade());
        assertEquals(15, quad.skyLight());
    }

    @Test
    void adjacentCubesCullSharedFaces() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        set(snap, 9, 8, 8, STONE);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        // Two cubes: 12 faces - 2 shared. Greedy merges the coplanar pairs (top, bottom,
        // north, south) into single quads: top 1 + bottom 1 + north 1 + south 1 + 2 ends = 6.
        assertEquals(6, out.axisCount(TerrainLayer.SOLID.ordinal()));
        List<PackedAxisQuad> top = quadsWithTexture(out, 0, 10);
        boolean mergedTop = top.stream().anyMatch(q -> q.axis() == 1 && !q.negativeFacing()
                && q.extentU() == 2 && q.extentV() == 1);
        assertTrue(mergedTop, "the two top faces must greedy-merge into one 2x1 quad");
    }

    @Test
    void greedyMergesFullFloor() {
        WorldSectionSnapshot snap = snapshot();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                set(snap, x, 0, z, STONE);
            }
        }
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        // top 1 + bottom 1 + 4 sides = 6 quads for the whole 16x16 slab.
        assertEquals(6, out.axisCount(TerrainLayer.SOLID.ordinal()));
        List<PackedAxisQuad> tops = new ArrayList<>();
        for (int i = 0; i < out.axisCount(0); i++) {
            PackedAxisQuad q = out.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                tops.add(q);
            }
        }
        assertEquals(1, tops.size());
        assertEquals(16, tops.get(0).extentU());
        assertEquals(16, tops.get(0).extentV());
    }

    @Test
    void greedyStopsAtTextureAndLightBoundaries() {
        WorldSectionSnapshot snap = snapshot();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                set(snap, x, 0, z, x < 8 ? STONE : STONE2);
            }
        }
        MeshBuffers byTexture = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        long tops = 0;
        for (int i = 0; i < byTexture.axisCount(0); i++) {
            PackedAxisQuad q = byTexture.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                tops++;
            }
        }
        assertEquals(2, tops, "different textures must not merge");

        WorldSectionSnapshot dark = snapshot();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                set(dark, x, 0, z, STONE);
            }
        }
        // Darken the sky light of the cells above the eastern half's top faces.
        for (int x = 8; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                dark.skyLight()[WorldSectionSnapshot.index(x + 1, 2, z + 1)] = 7;
            }
        }
        MeshBuffers byLight = mesh(dark, new Stub(), MesherOptions.PACK_OFF);
        long darkTops = 0;
        for (int i = 0; i < byLight.axisCount(0); i++) {
            PackedAxisQuad q = byLight.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                darkTops++;
            }
        }
        assertEquals(2, darkTops, "different face light must not merge");
    }

    /** Total face area (in unit faces) of one texture's quads in a layer - greedy-proof counting. */
    private static int faceArea(MeshBuffers out, int layer, int texture) {
        int area = 0;
        for (PackedAxisQuad quad : quadsWithTexture(out, layer, texture)) {
            area += quad.extentU() * quad.extentV();
        }
        return area;
    }

    @Test
    void glassMergesWithItselfButDoesNotOcclude() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, GLASS);
        set(snap, 9, 8, 8, GLASS);
        set(snap, 10, 8, 8, STONE);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        // Glass pair: the shared faces are culled by the same-state rule and the face against
        // the stone is culled by the stone's occlusion, leaving 9 unit faces of glass; the
        // stone keeps all 6 faces because glass occludes nothing.
        assertEquals(9, faceArea(out, TerrainLayer.CUTOUT.ordinal(), 20));
        assertEquals(6, faceArea(out, TerrainLayer.SOLID.ordinal(), 10));
    }

    @Test
    void leafSolidifyMovesSurroundedLeavesToSolid() {
        WorldSectionSnapshot snap = snapshot();
        // A solid 3x3x3 of leaves: the centre leaf is surrounded (predicate A via leaves).
        for (int x = 7; x <= 9; x++) {
            for (int y = 7; y <= 9; y++) {
                for (int z = 7; z <= 9; z++) {
                    set(snap, x, y, z, LEAVES);
                }
            }
        }
        Stub stub = new Stub();
        MeshBuffers out = mesh(snap, stub, MesherOptions.PACK_OFF);
        // Only the centre leaf solidifies: its 6 faces land in SOLID with the leaf texture.
        assertEquals(6, faceArea(out, TerrainLayer.SOLID.ordinal(), 30),
                "the surrounded centre leaf emits its faces as SOLID");
        assertEquals(6, quadsWithTexture(out, TerrainLayer.SOLID.ordinal(), 30).size());

        // Under a pack the swap is suppressed: no leaf face lands in SOLID at all.
        MeshBuffers packed = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.SOLID, true, TerrainVertexRequirements.NONE));
        assertEquals(0, faceArea(packed, TerrainLayer.SOLID.ordinal(), 30));
    }

    @Test
    void leafHollowSuppressesDepthTwoRuns() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 7, 8, 8, LEAVES);
        set(snap, 8, 8, 8, LEAVES);
        set(snap, 9, 8, 8, LEAVES);
        Stub stub = new Stub();
        MeshBuffers hollow = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.HOLLOW, false, TerrainVertexRequirements.NONE));
        // Depth-2 runs exist only inward from the two end leaves: each end leaf loses its
        // inward X face, the middle leaf keeps everything: 18 - 2 = 16 unit faces.
        assertEquals(16, faceArea(hollow, TerrainLayer.CUTOUT.ordinal(), 30));

        MeshBuffers none = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.NONE, false, TerrainVertexRequirements.NONE));
        assertEquals(18, faceArea(none, TerrainLayer.CUTOUT.ordinal(), 30));
    }

    @Test
    void generalQuadIsTranslatedAndLit() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 3, 4, 5, MODEL);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        assertEquals(1, out.generalTotal());
        GeneralQuad quad = out.generalQuad(TerrainLayer.SOLID.ordinal(), 0);
        assertEquals(60, quad.textureIndex());
        assertEquals(3.0f, quad.cornerX(0));
        assertEquals(5.0f, quad.cornerY(0));
        assertEquals(5.0f, quad.cornerZ(0));
        assertEquals(15 * 17, quad.lightWord() & 0xFF, "sky light 15 scaled by 17");
    }

    @Test
    void generalQuadCarriesUvPacked() {
        Stub stub = new Stub();
        int[] uvs = {GeneralQuad.packUv(0.25f, 0.25f), GeneralQuad.packUv(0.25f, 0.5f),
                GeneralQuad.packUv(0.5f, 0.5f), GeneralQuad.packUv(0.5f, 0.25f)};
        stub.rows[MODEL].quadUvs = uvs;
        WorldSectionSnapshot snap = snapshot();
        set(snap, 3, 4, 5, MODEL);
        MeshBuffers out = mesh(snap, stub, MesherOptions.PACK_OFF);
        assertEquals(1, out.generalTotal());
        GeneralQuad quad = out.generalQuad(TerrainLayer.SOLID.ordinal(), 0);
        for (int c = 0; c < 4; c++) {
            assertEquals(uvs[c], quad.uvPacked(c));
        }
    }

    @Test
    void escapedFaceLeavesAxisPathAndEmitsGeneral() {
        Stub stub = new Stub();
        // A full cube whose UP face escaped into the general pool (partial-rectangle UVs):
        // the axis UP face must vanish and the pooled quad must be emitted instead.
        stub.rows[STONE].generalFaces[TerrainDirs.UP] = true;
        stub.rows[STONE].quads = new float[][]{{
                0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0}};
        stub.rows[STONE].quadTexture = 10;
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        MeshBuffers out = mesh(snap, stub, MesherOptions.PACK_OFF);
        assertEquals(5, out.axisCount(TerrainLayer.SOLID.ordinal()));
        assertEquals(1, out.generalTotal());
    }

    @Test
    void auxColumnFollowsRequirements() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        Stub stub = new Stub();
        stub.rows[STONE].mcEntity = 42;
        MeshBuffers withAux = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.SOLID, true,
                new TerrainVertexRequirements(true, false, false, false)));
        assertTrue(withAux.auxEnabled());
        assertEquals(6, withAux.axisCount(0));
        for (int i = 0; i < 6; i++) {
            assertEquals(42, withAux.axisAux(0, i));
        }
        MeshBuffers without = mesh(snap, stub, MesherOptions.PACK_OFF);
        assertFalse(without.auxEnabled());
    }

    @Test
    void packOnSplitsSwayFacesOutOfGreedy() {
        WorldSectionSnapshot snap = snapshot();
        for (int x = 4; x < 8; x++) {
            set(snap, x, 0, 8, SWAY);
        }
        Stub stub = new Stub();
        MeshBuffers off = mesh(snap, stub, MesherOptions.PACK_OFF);
        List<PackedAxisQuad> offTops = new ArrayList<>();
        for (int i = 0; i < off.axisCount(TerrainLayer.CUTOUT.ordinal()); i++) {
            PackedAxisQuad q = off.axisQuad(TerrainLayer.CUTOUT.ordinal(), i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                offTops.add(q);
            }
        }
        assertEquals(1, offTops.size());
        assertEquals(4, offTops.get(0).extentU());

        MeshBuffers on = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.SOLID, true, TerrainVertexRequirements.NONE));
        int onTops = 0;
        for (int i = 0; i < on.axisCount(TerrainLayer.CUTOUT.ordinal()); i++) {
            PackedAxisQuad q = on.axisQuad(TerrainLayer.CUTOUT.ordinal(), i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                onTops++;
                assertEquals(1, q.extentU(), "pack-ON sway faces stay single");
            }
        }
        assertEquals(4, onTops);
    }

    @Test
    void tintIsCarriedAndSeparatesMerging() {
        WorldSectionSnapshot snap = snapshot();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                set(snap, x, 0, z, TINT);
                snap.tintRgb()[WorldSectionSnapshot.index(x + 1, 1, z + 1)] =
                        x < 8 ? 0x7CBD6B : 0xAA0000;
            }
        }
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        List<PackedAxisQuad> tops = new ArrayList<>();
        for (int i = 0; i < out.axisCount(0); i++) {
            PackedAxisQuad q = out.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing()) {
                tops.add(q);
            }
        }
        assertEquals(2, tops.size(), "different tints must not merge");
        PackedAxisQuad west = tops.stream().filter(q -> q.minX() == 0).findFirst().orElseThrow();
        assertTrue(west.hasTint());
        assertEquals(0x7C, west.tintR());
        assertEquals(0xBD, west.tintG());
        assertEquals(0x6B, west.tintB());
    }

    @Test
    void fluidloggedCellEmitsFluidFaces() {
        WorldSectionSnapshot snap = snapshot();
        snap.setFluid(WorldSectionSnapshot.index(9, 9, 9), FLUID);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        assertEquals(6, out.generalCount(TerrainLayer.TRANSLUCENT.ordinal()));
        // The top face sits at the fluid surface height.
        float maxY = -1;
        for (int i = 0; i < out.generalCount(TerrainLayer.TRANSLUCENT.ordinal()); i++) {
            GeneralQuad q = out.generalQuad(TerrainLayer.TRANSLUCENT.ordinal(), i);
            for (int c = 0; c < 4; c++) {
                maxY = Math.max(maxY, q.cornerY(c));
            }
        }
        assertEquals(8 + 0.875f, maxY, 0.001f);

        // Two logged neighbours share (and drop) their common faces.
        WorldSectionSnapshot pair = snapshot();
        pair.setFluid(WorldSectionSnapshot.index(9, 9, 9), FLUID);
        pair.setFluid(WorldSectionSnapshot.index(10, 9, 9), FLUID);
        MeshBuffers pairOut = mesh(pair, new Stub(), MesherOptions.PACK_OFF);
        assertEquals(10, pairOut.generalCount(TerrainLayer.TRANSLUCENT.ordinal()));
    }

    // ---------------------------------------------------------------------------------------------
    // Per-corner smooth light (R-107)
    // ---------------------------------------------------------------------------------------------

    /** One packed corner-light field: corner c of quad i in a layer, axis or general stream. */
    private static int cornerField(MeshBuffers out, int layer, int i, boolean general, int corner) {
        int word = general ? out.generalCornerLight(layer, i, corner >> 1)
                : out.axisCornerLight(layer, i, corner >> 1);
        return (word >> ((corner & 1) * 16)) & 0xFFFF;
    }

    private static int skySum(int field) {
        return field & 63;
    }

    private static int blockSum(int field) {
        return (field >> 6) & 63;
    }

    private static int aoStep(int field) {
        return (field >> 12) & 15;
    }

    /** The index (in its layer) of the single cube's UP face at section-local (x, z). */
    private static int upFaceIndex(MeshBuffers out, int x, int z) {
        for (int i = 0; i < out.axisCount(0); i++) {
            PackedAxisQuad q = out.axisQuad(0, i);
            if (q.axis() == 1 && !q.negativeFacing() && q.minX() == x && q.minZ() == z) {
                return i;
            }
        }
        throw new AssertionError("no UP face at " + x + ", " + z);
    }

    @Test
    void axisCornerLightVariesAcrossAFace() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        // The UP face's face cell is snapshot (9, 10, 9). Darken the west edge cell and the
        // west-north diagonal to sky 7: the (cu=0, cv=0) corner averages 15+7+15+7, the
        // (cu=0, cv=1) corner 15+7+15+15, the east corners stay at 60.
        snap.skyLight()[WorldSectionSnapshot.index(8, 10, 9)] = 7;
        snap.skyLight()[WorldSectionSnapshot.index(8, 10, 8)] = 7;
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        int i = upFaceIndex(out, 8, 8);
        assertEquals(44, skySum(cornerField(out, 0, i, false, 0)), "west-north corner");
        assertEquals(60, skySum(cornerField(out, 0, i, false, 1)), "east-north corner");
        assertEquals(52, skySum(cornerField(out, 0, i, false, 2)), "west-south corner");
        assertEquals(60, skySum(cornerField(out, 0, i, false, 3)), "east-south corner");
        assertEquals(0, blockSum(cornerField(out, 0, i, false, 0)));
    }

    @Test
    void axisCornerAoRespondsToOccluder() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        // A stone in the west edge cell of the west corners (snapshot (8, 10, 9)): its AO
        // value 0.2 replaces air's 1.0 in that corner's average - (1 + 0.2 + 1 + 1) / 4 = 0.8,
        // step 12 of 15. The east corners keep full AO.
        set(snap, 7, 9, 8, STONE);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        int i = upFaceIndex(out, 8, 8);
        assertEquals(12, aoStep(cornerField(out, 0, i, false, 0)));
        assertEquals(12, aoStep(cornerField(out, 0, i, false, 2)));
        assertEquals(15, aoStep(cornerField(out, 0, i, false, 1)));
        assertEquals(15, aoStep(cornerField(out, 0, i, false, 3)));
    }

    @Test
    void generalQuadCornerLightVaries() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 3, 4, 5, MODEL);
        // The model quad faces UP; its face cell is snapshot (4, 6, 6). Darken the west edge
        // and the west-north diagonal: corner 0 (at block-local (0, 1, 0)) averages 44,
        // corner 2 (at (1, 1, 1)) stays at 60.
        snap.skyLight()[WorldSectionSnapshot.index(3, 6, 6)] = 7;
        snap.skyLight()[WorldSectionSnapshot.index(3, 6, 5)] = 7;
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        assertEquals(1, out.generalTotal());
        int layer = TerrainLayer.SOLID.ordinal();
        assertEquals(44, skySum(cornerField(out, layer, 0, true, 0)));
        assertEquals(60, skySum(cornerField(out, layer, 0, true, 2)));
    }

    @Test
    void cornerLightColumnMatchesStoreLayout() {
        WorldSectionSnapshot snap = snapshot();
        set(snap, 8, 8, 8, STONE);
        set(snap, 3, 4, 5, MODEL);
        MeshBuffers out = mesh(snap, new Stub(), MesherOptions.PACK_OFF);
        int[] column = out.cornerLightColumn();
        assertEquals((out.axisTotal() + out.generalTotal()) * 2, column.length);
        // Axis quads first (layer order), then general quads.
        int axisCursor = 0;
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            for (int i = 0; i < out.axisCount(layer); i++) {
                assertEquals(out.axisCornerLight(layer, i, 0), column[axisCursor++]);
                assertEquals(out.axisCornerLight(layer, i, 1), column[axisCursor++]);
            }
        }
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            for (int i = 0; i < out.generalCount(layer); i++) {
                assertEquals(out.generalCornerLight(layer, i, 0), column[axisCursor++]);
                assertEquals(out.generalCornerLight(layer, i, 1), column[axisCursor++]);
            }
        }
        assertEquals(column.length, axisCursor);
    }

    @Test
    void snapshotArraysAreReusedAcrossFills() {
        WorldSectionSnapshot snap = new WorldSectionSnapshot();
        snap.fill(0, 0, 0);
        int[] states = snap.states();
        byte[] light = snap.skyLight();
        snap.fill(3, 1, -2);
        assertSame(states, snap.states());
        assertSame(light, snap.skyLight());
        assertEquals(3, snap.sectionX());
        assertEquals(-2, snap.sectionZ());
    }

    // ------------------------------------------------------------------
    // S2-b-perf K2 (Gate G2-31): the greedyEnabled switch. The off arm must
    // emit exactly the greedy arm's face set, only unmerged - that identity
    // is what makes the on/off pair a usable measurement contrast.
    // ------------------------------------------------------------------

    /** Every unit face a buffer's axis quads cover, as (layer, plane, cell, texture) keys. */
    private static java.util.Set<String> unitFaces(MeshBuffers out) {
        int[] uAxisByAxis = {2, 0, 0};
        int[] vAxisByAxis = {1, 2, 1};
        java.util.Set<String> faces = new java.util.TreeSet<>();
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            for (int i = 0; i < out.axisCount(layer); i++) {
                PackedAxisQuad quad = out.axisQuad(layer, i);
                int axis = quad.axis();
                int[] corner = {quad.minX(), quad.minY(), quad.minZ()};
                for (int du = 0; du < quad.extentU(); du++) {
                    for (int dv = 0; dv < quad.extentV(); dv++) {
                        int[] cell = corner.clone();
                        cell[uAxisByAxis[axis]] += du;
                        cell[vAxisByAxis[axis]] += dv;
                        faces.add(layer + ":" + axis + ":" + quad.negativeFacing() + ":"
                                + cell[0] + "," + cell[1] + "," + cell[2]
                                + ":" + quad.textureIndex());
                    }
                }
            }
        }
        return faces;
    }

    @Test
    void greedyOffEmitsTheSameFacesUnmerged() {
        WorldSectionSnapshot snap = snapshot();
        // A flat 6x6 stone platform: the top and bottom faces merge hard under greedy.
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                set(snap, x, 8, z, STONE);
            }
        }
        Stub stub = new Stub();
        MeshBuffers greedy = mesh(snap, stub, MesherOptions.PACK_OFF);
        MeshBuffers singles = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.DEFAULT, false, TerrainVertexRequirements.NONE, false));
        assertEquals(unitFaces(greedy), unitFaces(singles),
                "the off arm covers exactly the greedy arm's faces");
        assertTrue(singles.axisTotal() > greedy.axisTotal(),
                "unmerged emission is strictly more quads on a mergeable platform: "
                        + singles.axisTotal() + " vs " + greedy.axisTotal());
    }

    @Test
    void greedyOffComposesWithThePackNoGreedySeparation() {
        WorldSectionSnapshot snap = snapshot();
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                set(snap, x, 8, z, STONE);
            }
        }
        // A sway row on top: under a pack its faces are noGreedy singles in both arms.
        for (int x = 5; x <= 10; x++) {
            set(snap, x, 9, 7, SWAY);
        }
        Stub stub = new Stub();
        MeshBuffers greedy = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.DEFAULT, true, TerrainVertexRequirements.NONE, true));
        MeshBuffers singles = mesh(snap, stub, new MesherOptions(
                NativeLeafCulling.Mode.DEFAULT, true, TerrainVertexRequirements.NONE, false));
        assertEquals(unitFaces(greedy), unitFaces(singles),
                "pack arm: the off switch changes merging only, never the face set");
        assertTrue(singles.axisTotal() >= greedy.axisTotal());
    }

    @Test
    void greedyPropertySelectsTheOffArm() {
        String previous = System.getProperty("celtium.terrain.greedy");
        try {
            System.clearProperty("celtium.terrain.greedy");
            assertSame(MesherOptions.PACK_OFF, MesherOptions.packOffFromProperty());
            System.setProperty("celtium.terrain.greedy", "true");
            assertSame(MesherOptions.PACK_OFF, MesherOptions.packOffFromProperty());
            System.setProperty("celtium.terrain.greedy", "false");
            MesherOptions off = MesherOptions.packOffFromProperty();
            assertFalse(off.greedyEnabled());
            assertEquals(MesherOptions.PACK_OFF.leafMode(), off.leafMode());
            assertEquals(MesherOptions.PACK_OFF.packInUse(), off.packInUse());
            assertEquals(MesherOptions.PACK_OFF.requirements(), off.requirements());
        } finally {
            if (previous == null) {
                System.clearProperty("celtium.terrain.greedy");
            } else {
                System.setProperty("celtium.terrain.greedy", previous);
            }
        }
    }
}
