package org.soutalab.celtium.vulkan;

/**
 * TerrainStatistics - the terrain domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/history/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
 * every leg record (scripts/cpu-contract/verify_line_baseline.txt), so this class does not choose an order: it
 * exposes one appendRunN per contiguous run of its fields and the backend calls them in the existing order. The
 * domains are interleaved in the line, so a single fragment() per class would reorder it.
 *
 * <p>Fields are package-private only for the wiring pass; the writers move in with the domain (R3a/R3b/R4/R5/R6)
 * and the visibility narrows then. Expressions that read another class or a helper (VulkanContext, CeltiumGlStub,
 * TextureCopyService, VulkanModelQuadCapture, the celtium\$* accessors, the *Summary helpers) are left as calls;
 * they are the wiring's decisions, not this class's fields.
 *
 * <p>Runs, in the line's order:
 * <li>run 0 (backend L8307-L8309): passes skipped errors lastDraw lastSections skipReason</li>
 * <li>run 1 (backend L8438-L8455): regionUploadFailures sectionsLeftUnbuilt terrainIndirect calls draws clamps growths fallbacks lastFallback terrainIndirectStream indirectChecksum directDraws directChecksum match lastRegionUploadFailure</li>
 * <li>run 2 (backend L8649-L8650): terrainPasses immediateOnlyPasses worldPassBoundaryFlushes</li>
 * <li>run 3 (backend L8657-L8657): arenaUploads arenaUploadedBytes</li>
 * <li>run 4 (backend L8734-L8734): lastTerrainPassFrame</li>
 */
public final class TerrainStatistics {

    /** backend field regionUploadFailures (declared L?). */
    long regionUploadFailures;
    /** backend field sectionsLeftUnbuilt (declared L?). */
    long sectionsLeftUnbuilt;
    /** backend field lastRegionUploadFailure (declared L?). */
    long lastRegionUploadFailure;
    /** backend field lastTerrainPassFrame (declared L?). */
    long lastTerrainPassFrame;
    /** backend field lastDrawCommands (declared L257). */
    long lastDrawCommands;
    /** backend field passSubmissions (declared L384). */
    int passSubmissions;
    /** backend field passSkips (declared L385). */
    int passSkips;
    /** backend field passErrors (declared L386). */
    int passErrors;
    /** backend field lastSectionsDrawn (declared L412). */
    long lastSectionsDrawn;
    /** backend field terrainIndirectOffers (declared L1426). */
    long terrainIndirectOffers;
    /** backend field terrainIndirectCalls (declared L1427). */
    long terrainIndirectCalls;
    /** backend field terrainIndirectDraws (declared L1428). */
    long terrainIndirectDraws;
    /** backend field terrainIndirectGrowths (declared L1429). */
    long terrainIndirectGrowths;
    /** backend field terrainIndirectClampEvents (declared L1430). */
    long terrainIndirectClampEvents;
    /** backend field terrainIndirectCommandDraws (declared L1443). */
    long terrainIndirectCommandDraws;
    /** backend field terrainIndirectCommandChecksum (declared L1444). */
    long terrainIndirectCommandChecksum;
    /** backend field terrainDirectEquivalentDraws (declared L1445). */
    long terrainDirectEquivalentDraws;
    /** backend field terrainDirectEquivalentChecksum (declared L1446). */
    long terrainDirectEquivalentChecksum;
    /** backend field terrainIndirectFallbacks (declared L1457). */
    long terrainIndirectFallbacks;
    /** backend field terrainPasses (declared L1617). */
    int terrainPasses;
    /** backend field immediateOnlyPasses (declared L1618). */
    int immediateOnlyPasses;
    /** backend field worldPassBoundaryFlushes (declared L1656). */
    int worldPassBoundaryFlushes;

    /** Run 0, reproducing backend L8307-L8309: passes skipped errors lastDraw lastSections skipReason. */
    public void appendRun0(StringBuilder out) {
        out.append("celtium/vulkan-check passes=");
        out.append(this.passSubmissions);
        out.append(" skipped=");
        out.append(this.passSkips);
        out.append(" errors=");
        out.append(this.passErrors);
        out.append(" lastDraw=");
        out.append(this.lastDrawCommands);
        out.append(" lastSections=");
        out.append(this.lastSectionsDrawn);
        out.append(" skipReason=");
        out.append((this.lastSkipReason == null ? "none" : this.lastSkipReason.replace(' ', '_')));
    }

    /** Run 1, reproducing backend L8438-L8455: regionUploadFailures sectionsLeftUnbuilt terrainIndirect calls draws clamps growths fallbacks lastFallback terrainIndirectStream indirectChecksum directDraws directChecksum match lastRegionUploadFailure. */
    public void appendRun1(StringBuilder out) {
        out.append(" regionUploadFailures=");
        out.append(this.regionUploadFailures);
        out.append(" sectionsLeftUnbuilt=");
        out.append(this.sectionsLeftUnbuilt);
        out.append(" terrainIndirect=[offers=");
        out.append(this.terrainIndirectOffers);
        out.append(" calls=");
        out.append(this.terrainIndirectCalls);
        out.append(" draws=");
        out.append(this.terrainIndirectDraws);
        out.append(" clamps=");
        out.append(this.terrainIndirectClampEvents);
        out.append(" growths=");
        out.append(this.terrainIndirectGrowths);
        out.append(" fallbacks=");
        out.append(this.terrainIndirectFallbacks);
        out.append(" lastFallback=");
        out.append(this.lastTerrainIndirectFallback.replace(' ', '_'));
        out.append(" terrainIndirectStream=[indirectDraws=");
        out.append(this.terrainIndirectCommandDraws);
        out.append(" indirectChecksum=");
        out.append(Long.toHexString(this.terrainIndirectCommandChecksum));
        out.append(" directDraws=");
        out.append(this.terrainDirectEquivalentDraws);
        out.append(" directChecksum=");
        out.append(Long.toHexString(this.terrainDirectEquivalentChecksum));
        out.append(" match=");
        out.append((this.terrainIndirectCommandDraws == this.terrainDirectEquivalentDraws && this.terrainIndirectCommandChecksum == this.terrainDirectEquivalentChecksum));
        out.append(" lastRegionUploadFailure=");
        out.append(this.lastRegionUploadFailure);
    }

    /** Run 2, reproducing backend L8649-L8650: terrainPasses immediateOnlyPasses worldPassBoundaryFlushes. */
    public void appendRun2(StringBuilder out) {
        out.append(" terrainPasses=");
        out.append(this.terrainPasses);
        out.append(" immediateOnlyPasses=");
        out.append(this.immediateOnlyPasses);
        out.append(" worldPassBoundaryFlushes=");
        out.append(this.worldPassBoundaryFlushes);
    }

    /** Run 3, reproducing backend L8657-L8657: arenaUploads arenaUploadedBytes. */
    public void appendRun3(StringBuilder out) {
        out.append(" arenaUploads=");
        out.append(this.celtium$arenaUploadCount());
        out.append(" arenaUploadedBytes=");
        out.append(this.celtium$arenaUploadedBytes());
    }

    /** Run 4, reproducing backend L8734-L8734: lastTerrainPassFrame. */
    public void appendRun4(StringBuilder out) {
        out.append(" lastTerrainPassFrame=");
        out.append(this.lastTerrainPassFrame);
    }

}
