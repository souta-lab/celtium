package org.soutalab.celtium.vulkan;

/**
 * FontStatistics - the font domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
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
 * <li>run 0 (backend L8709-L8711): fontStripSeparators fontRunsStarted fontQuadsAppended</li>
 */
public final class FontStatistics {

    /** backend field fontQuadsAppended (declared L1716). */
    long fontQuadsAppended;
    /** backend field fontBatchesStarted (declared L1717). */
    long fontBatchesStarted;
    /** backend field fontStripSeparators (declared L1723). */
    long fontStripSeparators;

    /** Run 0, reproducing backend L8709-L8711: fontStripSeparators fontRunsStarted fontQuadsAppended. */
    public void appendRun0(StringBuilder out) {
        out.append(" fontStripSeparators=");
        out.append(this.fontStripSeparators);
        out.append(" fontRunsStarted=");
        out.append(this.fontBatchesStarted);
        out.append(" fontQuadsAppended=");
        out.append(this.fontQuadsAppended);
    }

}
