/*
 * The CPU-level contract of FontBatch (docs/refactoring-plan.md R3b, first half).
 *
 * Two of the things the extraction changes are read here rather than inferred from a leg:
 *
 *  - the run key now includes the target identity, so a run cannot span a switch between the main framebuffer and
 *    another one (the plan's section 5.4 hole). The check appends under one target, switches, appends again, and reads
 *    which target each submitted run was written for.
 *  - a refused run is retained and offered again instead of losing its glyphs. The check refuses a submission, reads
 *    the retention counters, and then proves the bytes survived the pending buffer's reuse by comparing a checksum of
 *    what the sink receives on the retry against what it received on the refusal.
 *
 * It also pins the verification summary's field names and their order, because that string is quoted by past
 * acceptances and compared byte for byte: a new counter may not appear in it.
 *
 * Build and run (no game, no GPU, no Gradle build):
 *
 *   LWJGL=$(find ~/.gradle/caches -name 'lwjgl-3.4.1.jar' | head -1)
 *   NATIVES=$(find ~/.gradle/caches -name 'lwjgl-3.4.1-natives-linux.jar' | head -1)
 *   javac -nowarn -cp "$LWJGL" -d /tmp/fontbatch-check scripts/cpu-contract/FontBatchContractCheck.java \
 *       celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FontBatch.java
 *   java -cp "/tmp/fontbatch-check:$LWJGL:$NATIVES" FontBatchContractCheck            # retention on (default)
 *   java -Dceltium.font.retaining=false -cp "/tmp/fontbatch-check:$LWJGL:$NATIVES" FontBatchContractCheck
 *
 * The second run is the diagnostic control: with retention off the refused run is dropped and counted, which is what
 * the backend does today.
 */
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.FontBatch;

public final class FontBatchContractCheck {

    private static final int STRIDE = 24;
    private static final int FORMAT_ID = 2;
    private static final int DRAW_MODE = org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;

    private static final List<String> failures = new ArrayList<String>();
    private static int checks;

    private static final class State implements FontBatch.FontRunState {
        boolean stalled;
        boolean matricesDirty;
        boolean mainTarget = true;
        long targetIdentity = 1L;
        boolean texture2dEnabled = true;
        int boundTextureName = 42;
        int whiteTextureId = 7;
        float red = 1.0F, green = 1.0F, blue = 1.0F, alpha = 1.0F;
        boolean alphaTestEnabled;
        float alphaRef;
        final float[] projection = new float[16];
        final float[] modelView = new float[16];

        public boolean stalled() { return this.stalled; }
        public boolean matricesDirty() { return this.matricesDirty; }
        public boolean mainTarget() { return this.mainTarget; }
        public long targetIdentity() { return this.targetIdentity; }
        public boolean texture2dEnabled() { return this.texture2dEnabled; }
        public int boundTextureName() { return this.boundTextureName; }
        public int whiteTextureId() { return this.whiteTextureId; }
        public float colorRed() { return this.red; }
        public float colorGreen() { return this.green; }
        public float colorBlue() { return this.blue; }
        public float colorAlpha() { return this.alpha; }
        public boolean alphaTestEnabled() { return this.alphaTestEnabled; }
        public float alphaFuncReference() { return this.alphaRef; }
        public float[] projection() { return this.projection; }
        public float[] modelView() { return this.modelView; }
    }

    /** Records what it was offered, with a checksum of the bytes so a retained run's copy can be compared. */
    private static final class Sink implements FontBatch.RunSink {
        final List<long[]> runs = new ArrayList<long[]>();   // {vertexCount, texture, targetIdentity, bytesChecksum}
        final List<String> how = new ArrayList<String>();
        int refusals;
        boolean refuse;

        public boolean submit(FontBatch.Run run) {
            if (this.refuse) {
                this.refusals++;
                return false;
            }
            ByteBuffer bytes = MemoryUtil.memByteBuffer(run.address, run.vertexCount * run.stride);
            long checksum = 0L;
            for (int i = 0; i < run.vertexCount * run.stride; i++) {
                checksum = checksum * 31L + bytes.get(i);
            }
            this.runs.add(new long[] {run.vertexCount, run.texture, run.targetIdentity, checksum});
            this.how.add(run.how);
            return true;
        }
    }

    private static void check(String name, Object measured, Object expected) {
        checks++;
        boolean ok = measured == null ? expected == null : measured.equals(expected);
        if (!ok) {
            failures.add(name + ": measured " + measured + ", expected " + expected);
        }
        System.out.printf("%-4s %-44s measured=%s expected=%s%n", ok ? "ok" : "FAIL", name, measured, expected);
    }

    private static int field(String summary, String key) {
        for (String token : summary.split(" ")) {
            if (token.startsWith(key + "=")) {
                return Integer.parseInt(token.substring(key.length() + 1));
            }
        }
        return -1;
    }

    private static String[] keys(String summary) {
        String[] tokens = summary.split(" ");
        String[] keys = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            keys[i] = tokens[i].substring(0, tokens[i].indexOf('='));
        }
        return keys;
    }

    private static FontBatch newBatch(State state, Sink sink) {
        FontBatch batch = new FontBatch();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        return batch;
    }

    private static long quad(FontBatch batch, ByteBuffer source) {
        return batch.celtium$fontAppend(Integer.valueOf(FORMAT_ID), STRIDE, FORMAT_ID,
                MemoryUtil.memAddress(source, 0), 4, DRAW_MODE) ? 1 : 0;
    }

    public static void main(String[] args) {
        boolean retaining = !"false".equalsIgnoreCase(System.getProperty("celtium.font.retaining", "true"));
        System.out.println("FontBatch contract check, retaining=" + retaining);
        ByteBuffer source = MemoryUtil.memAlloc(4 * STRIDE);
        for (int i = 0; i < 4 * STRIDE; i++) {
            source.put(i, (byte) (i + 1));
        }

        // 1. The first append starts a run; a second with the same state joins it.
        State state = new State();
        Sink sink = new Sink();
        FontBatch batch = newBatch(state, sink);
        check("first append is taken", quad(batch, source), 1L);
        check("run is open after one append", batch.celtium$fontRunOpen(), true);
        check("nothing submitted while the run is open", sink.runs.size(), 0);
        check("second append with the same state joins", quad(batch, source), 1L);
        check("still one run", field(batch.celtium$fontBatchSummary(), "fontRunsStarted"), 1);
        check("two quads in the run", field(batch.celtium$fontBatchSummary(), "fontQuadsAppended"), 2);
        check("strip separator inserted between them", field(batch.celtium$fontBatchSummary(), "fontStripSeparators"), 1);

        // 2. A colour change closes the run, and the submitted run carries the state it was appended under.
        state.red = 0.5F;
        check("append after a colour change is taken", quad(batch, source), 1L);
        check("the colour change submitted the first run", sink.runs.size(), 1);
        check("first run has 4+2+4 vertices", sink.runs.get(0)[0], 10L);
        check("first run closed as a state change", sink.how.get(0), "state-change");
        check("first run kept its texture witness", sink.runs.get(0)[1], 42L);

        // 3. The target witness: a switch between targets closes the run instead of spanning it.
        state.targetIdentity = 1L;
        batch.celtium$flushPendingFontBatch("boundary");
        int before = sink.runs.size();
        check("append for target 1", quad(batch, source), 1L);
        state.targetIdentity = 2L;
        check("append for target 2 is taken", quad(batch, source), 1L);
        check("the target switch submitted the target-1 run", sink.runs.size(), before + 1);
        check("that run says target 1", sink.runs.get(before)[2], 1L);
        batch.celtium$flushPendingFontBatch("boundary");
        check("the target-2 run goes out separately", sink.runs.size(), before + 2);
        check("that run says target 2", sink.runs.get(before + 1)[2], 2L);

        // 4. A refused run is retained, and the bytes survive the pending buffer's reuse.
        state.targetIdentity = 1L;
        check("append before the refusal", quad(batch, source), 1L);
        sink.refuse = true;
        batch.celtium$flushPendingFontBatch("intervening");
        check("the refusal was seen", sink.refusals, 1);
        if (retaining) {
            check("the refused run is retained", batch.celtium$retainedRunCount(), 1);
            check("retention counted one run", field(batch.celtium$fontRetentionSummary(), "retainedRuns"), 1);
        } else {
            check("the control dropped the run", batch.celtium$retainedRunCount(), 0);
            check("the control counted the drop", field(batch.celtium$fontRetentionSummary(), "retainedControlDrops"), 1);
        }

        if (retaining) {
            // A run appended after the refusal and flushed while another target is current is refused too, so the
            // queue holds two runs for target 1 - both waiting for it, neither dropped.
            for (int i = 0; i < 4 * STRIDE; i++) {
                source.put(i, (byte) 0x5A);   // overwrite the buffer a retained run must not alias
            }
            quad(batch, source);
            state.targetIdentity = 2L;
            batch.celtium$flushPendingFontBatch("boundary");
            check("retained runs wait for their own target",
                    field(batch.celtium$fontRetentionSummary(), "retainedPendingRuns"), 2);
            state.targetIdentity = 1L;
            sink.refuse = false;
            int beforeRetry = sink.runs.size();
            batch.celtium$flushPendingFontBatch("boundary");
            check("both retained runs are offered again", sink.runs.size(), beforeRetry + 2);
            check("the oldest retained run goes first", sink.how.get(beforeRetry), "retained");
            check("retained run kept its vertex count", sink.runs.get(beforeRetry)[0], 4L);
            check("retained bytes survived the buffer reuse", sink.runs.get(beforeRetry)[3], checksumOfOriginal(source));
            check("retention queue is empty after the retry", batch.celtium$retainedRunCount(), 0);
        }

        // 5. The verification summary's field names and order, which the CELTIUM_VERIFY line quotes.
        String summary = batch.celtium$fontBatchSummary();
        check("summary keys are unchanged and in order", Arrays.toString(keys(summary)),
                "[fontRunsStarted, fontQuadsAppended, fontStripSeparators, flushedIntervening, flushedBoundary,"
                        + " flushedStateChange, appendMillis]");
        check("retention counters stay out of that string", summary.contains("retained"), false);

        MemoryUtil.memFree(source);
        batch.close();
        System.out.println(checks + " checks, " + failures.size() + " failed");
        for (String failure : failures) {
            System.out.println("  FAIL " + failure);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    /**
     * The checksum the sink would have computed for the original 1..96 byte pattern, used to prove the retained run's
     * copy is the glyph data and not the overwritten buffer.
     */
    private static long checksumOfOriginal(ByteBuffer overwritten) {
        long checksum = 0L;
        for (int i = 0; i < 4 * STRIDE; i++) {
            checksum = checksum * 31L + (byte) (i + 1);
        }
        return checksum;
    }
}
