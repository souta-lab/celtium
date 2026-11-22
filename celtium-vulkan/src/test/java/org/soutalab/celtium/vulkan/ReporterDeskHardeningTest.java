package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reporter desk-hardening rules (issue #425's remaining teardown exposure and item 529's
 * candidate (a)): the parts of those failures that are the product's own behaviour, fixed without
 * knowing which upstream hypothesis is true.
 * <p>
 * Three contracts, all decidable without a device, in {@link DeviceLostRobustnessTest}'s shape - pure
 * functions where the decision is a function, source contracts where it is a call site:
 * <ul>
 *   <li>A renderer reload survives its old manager's teardown throwing: {@code initRenderer} discards
 *       the old manager anyway and still creates the new one, instead of keeping the half-destroyed
 *       manager installed and never reaching the creation.</li>
 *   <li>The backend's release sequence cannot strand the registration: {@code delete()} holds the
 *       sequence to one catch that records and detaches, and a completed full teardown is not run
 *       twice.</li>
 *   <li>A {@code vkCreateDevice} refusal carries its context (item 529): the parameters it was asked
 *       with and, when this process already reported a device loss, that report - the join that used
 *       to cost a reporter round-trip between the device-lost log and the crash report.</li>
 * </ul>
 * What these tests do not prove: anything about a lost device itself, or about the reporter's
 * hardware. They prove the shapes above are the ones in the tree.
 */
class ReporterDeskHardeningTest {

    // ------------------------------------------------------------------ item 529: the refusal sentence

    /** The loss note's form: operation, generation, wall time - the three facts the join needs. */
    @Test
    void theLossNoteNamesOperationGenerationAndTime() {
        String note = VulkanContext.celtium$deviceLossReport("vkWaitForFences(deferred texture staging reuse)",
                3L, 1763726400000L);
        assertTrue(note.contains("vkWaitForFences(deferred texture staging reuse)"),
                "the note names the reporting operation");
        assertTrue(note.contains("generation 3"), "the note names the reporting context's generation");
        assertTrue(note.contains("1763726400000"), "the note carries the wall time");
    }

    /** With a prior loss, the refusal reads as its downstream and carries the creation parameters. */
    @Test
    void theRefusalAfterALossQuotesItAndCarriesTheParameters() {
        String message = VulkanContext.celtium$deviceCreationFailure(-3, "NVIDIA GeForce GTX 1650 Ti",
                List.of("VK_KHR_swapchain"), "multiDrawIndirect=true",
                "vkWaitForFences(deferred texture staging reuse) (context generation 3, at 1763726400000)");
        assertTrue(message.contains("returned VkResult -3"), "the result is named");
        assertTrue(message.contains("NVIDIA GeForce GTX 1650 Ti"), "the device is named");
        assertTrue(message.contains("VK_KHR_swapchain"), "the enabled extensions are carried");
        assertTrue(message.contains("multiDrawIndirect=true"), "the enabled features are carried");
        assertTrue(message.contains("A device loss was already reported in this process"),
                "the prior loss is quoted, not left in the other file");
        assertTrue(message.contains("downstream of that loss"),
                "the refusal is read as the loss's downstream, which is the item 529 finding");
    }

    /** Without a prior loss, the refusal says so - the absence is itself the discriminating fact. */
    @Test
    void theRefusalWithoutALossSaysNoneWasReported() {
        String message = VulkanContext.celtium$deviceCreationFailure(-3, "Some GPU",
                List.of(), "multiDrawIndirect=false", null);
        assertTrue(message.contains("No device loss was reported earlier in this process."),
                "a first refusal in a process is stated as one");
    }

    /**
     * The call sites: the loss funnel writes the note, and the creation site throws the bundled
     * sentence rather than the generic check's bare one.
     */
    @Test
    void theLossFunnelWritesTheNoteAndTheCreationSiteBundlesIt() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
        int funnel = source.indexOf("private IllegalStateException celtium$lostDevice(String operation)");
        assertTrue(funnel >= 0, "the loss funnel is declared");
        String funnelBody = source.substring(funnel, funnel + 3000);
        assertTrue(funnelBody.contains("lastDeviceLossReport = celtium$deviceLossReport("),
                "the funnel records the process-level note every reported loss passes through");
        int create = source.indexOf("vkCreateDevice(this.physicalDevice, createInfo, null, handle)");
        assertTrue(create >= 0, "the creation call is present");
        String createSite = source.substring(create, create + 2200);
        assertTrue(createSite.contains("celtium$deviceCreationFailure(createResult, this.deviceName,"),
                "a refusal throws the bundled sentence");
        assertTrue(createSite.contains("lastDeviceLossReport"),
                "the bundled sentence is fed the process-level loss note");
        assertTrue(!source.contains("check(vkCreateDevice(this.physicalDevice, createInfo, null, handle),"),
                "the bare generic check on vkCreateDevice is gone: it produced the context-free -3 line");
    }

    // ------------------------------------------------------------------ #425: the reload survives teardown

    /**
     * {@code initRenderer}: the old manager's destroy is guarded, the field is nulled regardless, and
     * the new manager's creation still follows - in that order.
     */
    @Test
    void theReloadDiscardsTheOldManagerEvenWhenItsTeardownThrows() throws IOException {
        String source = Files.readString(findRepoFile(
                "common/src/main/java/org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer.java"));
        int init = source.indexOf("protected void initRenderer(CommandList commandList)");
        assertTrue(init >= 0, "initRenderer is declared");
        String body = source.substring(init, init + 5000);
        int destroy = body.indexOf("this.renderSectionManager.destroy();");
        int guard = body.lastIndexOf("try {", destroy);
        int caught = body.indexOf("catch (RuntimeException failure)", destroy);
        int nulled = body.indexOf("this.renderSectionManager = null;", destroy);
        int create = body.indexOf("this.renderSectionManager = this.createRenderSectionManager(commandList);");
        assertTrue(destroy >= 0, "the old manager is destroyed");
        assertTrue(guard >= 0 && guard < destroy, "the destroy is inside a try");
        assertTrue(caught > destroy, "a teardown failure is caught, not propagated out of the reload");
        assertTrue(nulled > caught, "the field is nulled after the guard: the wreckage is not kept installed");
        assertTrue(create > nulled, "the new manager is still created after the discard");
    }

    /**
     * The backend's {@code delete()}: the completed-teardown guard precedes the settle, the release
     * sequence runs inside the catch that detaches, and the sequence sets the completion flag only at
     * its end - after the window-owned return, which must not set it.
     */
    @Test
    void theBackendTeardownDetachesOnFailureAndRunsOnce() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int delete = source.indexOf("    public void delete() {");
        assertTrue(delete >= 0, "delete() is declared");
        String deleteBody = source.substring(delete, delete + 7000);
        int guard = deleteBody.indexOf("if (this.fullyDeleted) {");
        int settle = deleteBody.indexOf("if (!settleGpu(\"backend delete\")) {");
        int sequence = deleteBody.indexOf("celtium$deleteSettled();");
        int caught = deleteBody.indexOf("catch (RuntimeException | LinkageError failure)", sequence);
        assertTrue(guard >= 0 && guard < settle,
                "a completed full teardown is refused before the settle can wait on a deleted device");
        assertTrue(settle >= 0 && sequence > settle, "the sequence runs after the settle succeeds");
        assertTrue(caught > sequence, "the sequence runs inside the catch");
        String catchBody = deleteBody.substring(caught, caught + 2500);
        assertTrue(catchBody.contains("record(\"teardown: the release sequence stopped early"),
                "a stopped sequence is recorded, not swallowed");
        assertTrue(catchBody.contains("celtium$detachRegistration();"),
                "the catch detaches the registration: nothing keeps drawing into the half-deleted backend");

        int settled = source.indexOf("private void celtium$deleteSettled() {");
        assertTrue(settled >= 0, "the release sequence is its own method");
        String settledBody = source.substring(settled, settled + 16000);
        int windowExit = settledBody.indexOf("if (this.windowOwned) {");
        int completed = settledBody.indexOf("this.fullyDeleted = true;");
        assertTrue(windowExit >= 0, "the window-owned exit is inside the sequence");
        assertTrue(completed > windowExit,
                "the completion flag is set at the sequence's end, past the window-owned return it must not mark");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
