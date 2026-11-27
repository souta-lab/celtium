package org.soutalab.celtium.gradle.remapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Classpath carrier for the mod build's reobfuscation support.
 *
 * <p>Applying this plugin changes nothing in the project it is applied to: it registers no
 * tasks, creates no extensions and adds no conventions. Its sole effect is that Gradle puts
 * this project's classes, together with the libraries the reobfuscation task runs on
 * (tiny-remapper, mapping-io and ASM), onto the build script classpath of the consuming
 * build, because the plugin ships as an included build.
 *
 * <p>Every registration lives in the consuming build script instead. That script is the
 * only place that knows the concrete files of the mod build (which jar is the input, where
 * the mappings come from, what the result must be called), so it declares the
 * {@link ReobfuscateCodeAndMixinsTask} instances itself.
 */
public class CeltiumRemapperPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Nothing to do: the contract of this plugin is the classpath it arrives on.
    }
}
