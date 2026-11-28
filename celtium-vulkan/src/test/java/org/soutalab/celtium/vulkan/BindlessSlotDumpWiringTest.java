package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bindless slot dump instrument's wiring, pinned by source reading in the house style
 * (see {@link BindlessArrayWiringTest}): the instrument exists to compare what a bindless
 * descriptor slot references against the owning mirror's current handles, and the parts
 * that make that comparison possible - the registry naming the image at registration, the
 * binding recording the write, the frame gate ticking the dump - only exist as connections
 * between classes a device-less test cannot execute together. Behaviour that CAN run
 * headless (the bookkeeping staying empty while disabled, the four-argument registration
 * behaving like the three-argument one) lives in {@code BindlessTextureBindingTest}.
 */
class BindlessSlotDumpWiringTest {

    private static final String REGISTRY =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java";
    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String BINDING =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/bindless/BindlessTextureBinding.java";
    private static final String TEXTURE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return direct;
    }

    /**
     * The registry must name the image behind the view when it feeds the array, or the
     * binding has nothing to compare the slot against - a view alone cannot be resolved
     * back to its image.
     */
    @Test
    void registryNamesTheImageWhenRegistering() throws IOException {
        String registry = read(REGISTRY);
        assertTrue(registry.contains(
                        "onTextureRegistered(glTextureId, texture.view(), texture.sampler(),"),
                "the registry registers with the four-argument form");
        assertTrue(registry.contains("texture.image())"),
                "the fourth argument is the texture's current image");
    }

    /**
     * Both halves of the instrument read the same gate property, so one flag arms the
     * bookkeeping and the census together; and the census compares the slot's recorded
     * handles against the mirror's current ones, which is the S1/S2 decision itself.
     */
    @Test
    void instrumentGateAndComparisonAreWired() throws IOException {
        String registry = read(REGISTRY);
        String binding = read(BINDING);
        assertTrue(binding.contains("celtium.debug.bindlessSlotDump"),
                "the binding arms its bookkeeping on the dump property");
        assertTrue(registry.contains("celtium.debug.bindlessSlotDump"),
                "the registry arms its census on the same property");
        assertTrue(registry.contains("binding.writtenImageAt(slot)"),
                "the census reads what the slot was written with");
        assertTrue(registry.contains("texture.celtium$readLevelZero()"),
                "the census reads mirror content back");
        assertTrue(binding.contains("recordSlotWrite"),
                "the binding records every slot write while armed");
    }

    /** The dump only runs if the frame completion gate ticks it. */
    @Test
    void frameGateTicksTheDump() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("celtium$maybeDumpBindlessSlots()"),
                "the backend ticks the slot dump from its completion gate");
    }

    /** The mirror readback the census uses is the texture's own level-0 readback. */
    @Test
    void textureExposesItsLevelZeroReadback() throws IOException {
        String texture = read(TEXTURE);
        assertTrue(texture.contains("celtium$readLevelZero()"),
                "the texture exposes a level-0 readback for the census");
        assertTrue(texture.contains("return readMirrorLevel(0);"),
                "the readback is the existing mirror readback, not a second copy path");
    }
}
