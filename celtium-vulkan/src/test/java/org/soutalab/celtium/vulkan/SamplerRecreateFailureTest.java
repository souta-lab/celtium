package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L06-03's regression shape: a failed sampler recreate must leave the texture exactly as it was,
 * so the next lookup retries instead of sampling through sampler 0 for the rest of the texture's
 * life.
 * <p>
 * The pre-fix shape committed before it built: {@code confirmSamplerStateFromRecord} consumed the
 * recorded-parameter version at its head and the rebuild retired the old sampler before creating
 * the replacement, so one failed {@code vkCreateSampler} left the version saying "confirmed", the
 * parameter fields saying "new" and the sampler itself 0 - and no later lookup ever retried.
 * {@code setSamplerReach} had the same shape in miniature (the reach was raised before the
 * recreate, so a failure made {@code wanted <= samplerMaxLod} conclude the work was done).
 * <p>
 * The reach path is driven here on a device-free instance ({@link VulkanGlTexture#uncreatedForTest()}):
 * with no context, {@code createSamplerHandle} fails immediately with a {@link RuntimeException}
 * (the null context), which is exactly the failure the fix's catch block exists for. The behavioural
 * assertions are the fix's contract - reach and sampler untouched by the failure, and the retry
 * still armed. The parameter path's restore is pinned as a source contract below, because driving
 * it needs the GL stub's recorded-parameter state for a texture id, a setup the device-free
 * instance cannot offer honestly.
 */
class SamplerRecreateFailureTest {

    private static void setMipLevels(VulkanGlTexture texture, int levels) throws Exception {
        Field field = VulkanGlTexture.class.getDeclaredField("mipLevels");
        field.setAccessible(true);
        field.setInt(texture, levels);
    }

    @Test
    void aFailedReachRecreateLeavesTheReachAndSamplerUntouched() throws Exception {
        VulkanGlTexture texture = VulkanGlTexture.uncreatedForTest();
        setMipLevels(texture, 8);
        assertEquals(1, texture.samplerMaxLod(), "a fresh mirror keeps the default reach");

        assertThrows(RuntimeException.class, () -> texture.forceSamplerReach(5),
                "with no device the recreate fails - the failure itself is expected here");

        assertEquals(1, texture.samplerMaxLod(),
                "the reach stays at the value the live sampler was built with: raising it before the"
                        + " recreate is what stranded the texture, because the retry then read as done");
        assertEquals(0L, texture.sampler(), "no sampler was created or half-installed by the failure");
    }

    @Test
    void aFailedReachRecreateLeavesTheRetryArmed() throws Exception {
        VulkanGlTexture texture = VulkanGlTexture.uncreatedForTest();
        setMipLevels(texture, 8);

        assertThrows(RuntimeException.class, () -> texture.forceSamplerReach(5));
        assertThrows(RuntimeException.class, () -> texture.forceSamplerReach(5),
                "the second call must attempt the recreate again (and fail again here), not conclude"
                        + " from the raised reach that the work is already done");
        assertEquals(1, texture.samplerMaxLod());
    }

    @Test
    void theParameterRebuildRestoresItsFieldsWhenTheCreateFails() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java"));

        int confirm = source.indexOf("public void confirmSamplerStateFromRecord(int glTextureId)");
        assertTrue(confirm >= 0, "confirmSamplerStateFromRecord is declared");
        String body = source.substring(confirm, confirm + 12000);

        assertTrue(body.contains("catch (RuntimeException failure)"),
                "the parameter rebuild catches the create failure");
        int restore = body.indexOf("this.wrapRepeatU = prevWrapRepeatU;");
        assertTrue(restore >= 0, "the catch restores the previous parameter fields");
        assertTrue(body.indexOf("throw failure;", restore) > restore,
                "the failure propagates after the restore, so the caller sees the rebuild did not happen");

        int headEnd = body.indexOf("boolean repeatU =");
        assertTrue(headEnd > 0, "the record lookups are present");
        assertTrue(!body.substring(0, headEnd).contains("this.confirmedParameterVersion = parameterVersion;"),
                "the recorded version is not consumed at the head, before the rebuild: consuming it there"
                        + " is what made a failed create permanent, because no later lookup retried");
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
