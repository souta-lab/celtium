package org.soutalab.celtium.core;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pure parts of the plugin-shadowing detection ({@code CeltiumCompetitors.checkPluginShadowing}):
 * the CodeSource comparison and the jar name extraction. The live half - resolving the plugin class
 * through the launch class loader and comparing it against the {@code @Mod} class - only means
 * anything inside a real launch, so it is covered by the coexistence acceptance leg instead of here.
 */
class CeltiumCompetitorsShadowTest {

    private static URL url(String form) throws Exception {
        return new URL(form);
    }

    @Test
    void sameArtifactDoesNotDiffer() throws Exception {
        URL jar = url("file:/instance/mods/celtium.jar");
        assertFalse(CeltiumCompetitors.sourcesDiffer(jar, jar));
        assertFalse(CeltiumCompetitors.sourcesDiffer(jar, url("file:/instance/mods/celtium.jar")));
    }

    @Test
    void differentArtifactsDiffer() throws Exception {
        assertTrue(CeltiumCompetitors.sourcesDiffer(
                url("file:/instance/mods/actinium.jar"), url("file:/instance/mods/celtium.jar")));
    }

    @Test
    void sameJarDifferentClassEntriesDoNotDiffer() throws Exception {
        // The shape a launch class loader actually reports: the full resource URL of each class,
        // same jar, different entries. This is Celtium running alone and must not read as shadowing.
        URL plugin = url("jar:file:/instance/mods/celtium-forge-mc1.12.2-0.2.2.jar"
                + "!/org/soutalab/celtium/core/CeleritasLoadingPlugin.class");
        URL mod = url("jar:file:/instance/mods/celtium-forge-mc1.12.2-0.2.2.jar"
                + "!/org/taumc/celeritas/CeleritasVintage.class");
        assertFalse(CeltiumCompetitors.sourcesDiffer(plugin, mod));
    }

    @Test
    void jarWrapperAndPlainFileFormsOfOneJarDoNotDiffer() throws Exception {
        assertFalse(CeltiumCompetitors.sourcesDiffer(
                url("jar:file:/instance/mods/celtium.jar!/org/soutalab/celtium/core/CeleritasLoadingPlugin.class"),
                url("file:/instance/mods/celtium.jar")));
    }

    @Test
    void differentJarsInEntryFormDiffer() throws Exception {
        assertTrue(CeltiumCompetitors.sourcesDiffer(
                url("jar:file:/instance/mods/actinium.jar!/org/soutalab/celtium/core/CeleritasLoadingPlugin.class"),
                url("jar:file:/instance/mods/celtium.jar!/org/taumc/celeritas/CeleritasVintage.class")));
    }

    @Test
    void unknownLocationNeverDiffers() throws Exception {
        URL jar = url("file:/instance/mods/celtium.jar");
        assertFalse(CeltiumCompetitors.sourcesDiffer(null, jar));
        assertFalse(CeltiumCompetitors.sourcesDiffer(jar, null));
        assertFalse(CeltiumCompetitors.sourcesDiffer(null, null));
    }

    @Test
    void jarFileNameExtraction() throws Exception {
        assertEquals("actinium.jar", CeltiumCompetitors.jarFileName(url("file:/instance/mods/actinium.jar")));
        assertEquals("actinium.jar", CeltiumCompetitors.jarFileName(
                url("jar:file:/instance/mods/actinium.jar!/com/dhj/actinium/Actinium.class")));
        assertEquals("classes", CeltiumCompetitors.jarFileName(url("file:/home/dev/celtium/build/classes/")));
        assertEquals("an unknown jar", CeltiumCompetitors.jarFileName(null));
    }
}
