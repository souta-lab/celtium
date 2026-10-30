package org.taumc.celeritas.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.util.MixinClassValidator;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CeleritasVintageMixinPlugin implements IMixinConfigPlugin {
    public static final Logger LOGGER = LogManager.getLogger("CeltiumMixins");

    @Override
    public void onLoad(String mixinPackage) {
        // Celtium targets CleanroomMC only. The previous revision probed for RetroFuturaBootstrap here and, when it
        // found it, reached into SharedConfig's transformer list to add lwjgl3ify exclusions. Both are gone: the
        // supported runtime is pinned (see docs/plan.md section 0), so there is nothing to detect and nothing to
        // patch, and the mod no longer reflects into another component's internals at startup.
        LOGGER.info("Loaded Celeritas mixin plugin (CleanroomMC target)");
        celtium$installGllessEarly();
    }

    /**
     * Installs the GL-less stub from the plugin, so GL-less does not depend only on a mixin.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-e2e-268-xlog}, {@code -Xlog:class+load=info}): in the 268-mod E2E pack
     * RandomPatches' tweak initialisation defines {@code org.lwjgl.opengl.Display} at <b>8.975 s</b>, while this
     * plugin's {@link #getMixins()} only runs at <b>9.837 s</b>. {@code onLoad} runs at 7.1 s in the same leg, so
     * the stub is installed here as well; {@code install()} is idempotent.
     * <p>
     * The window's {@code GLFW_NO_API} hint is <b>not</b> set here, and deliberately: GLFW only permits the
     * init-related calls before {@code glfwInit} (its introduction lists {@code glfwInitHint}, not
     * {@code glfwWindowHint}), and {@code glfwInit} resets window hints to their defaults anyway - so a hint from
     * this point would either be a {@code GLFW_NOT_INITIALIZED} no-op or be wiped. The hint belongs where
     * {@code core.LwjglxDisplayMixin} puts it, after init and immediately before {@code glfwCreateWindow}; what
     * this plugin changes is <em>when that mixin is registered</em> (the JSON's {@code client} list, read at
     * 7.123 s, instead of {@link #getMixins()} at 9.837 s), which is what lets it apply at all.
     */
    private static void celtium$installGllessEarly() {
        try {
            org.soutalab.celtium.vulkan.gl.CeltiumGlStub.install();
            LOGGER.info("CELTIUM_GLSTUB stub GL provider installed early (from the mixin plugin)");
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("CELTIUM_GLSTUB early stub install failed: {}", error.toString());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    private static String mixinClassify(Path baseFolder, Path path) {
        try {
            String className = baseFolder.relativize(path).toString().replace('/', '.').replace('\\', '.');
            return className.substring(0, className.length() - 6);
        } catch(RuntimeException e) {
            throw new IllegalStateException("Error relativizing " + path + " to " + baseFolder, e);
        }
    }

    @Override
    public List<String> getMixins() {
        List<Path> rootPaths = new ArrayList<>();

        rootPaths.addAll(Stream.of("org.taumc.celeritas.mixin")
                .flatMap(str -> {
                    URL url = CeleritasVintageMixinPlugin.class.getResource("/" + str.replace('.', '/'));
                    if (url == null) {
                        return Stream.empty();
                    }
                    try {
                        return Stream.of(Paths.get(url.toURI()));
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList()));

        if (rootPaths.isEmpty()) {
            try {
                URI uri = Objects.requireNonNull(CeleritasVintageMixinPlugin.class.getResource("/mixins.celeritas.json")).toURI();
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException var11) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                rootPaths.add(fs.getPath("org", "taumc", "celeritas", "mixin").toAbsolutePath());
            } catch(Exception e) {
                LOGGER.error("Error finding mixins", e);
            }
        }

        Set<String> possibleMixinClasses = new HashSet<>();
        for(Path rootPath : rootPaths) {
            try(Stream<Path> mixinStream = Files.find(rootPath, Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().endsWith(".class"))) {
                mixinStream
                        .map(Path::toAbsolutePath)
                        .filter(MixinClassValidator::isMixinClass)
                        .map(path -> mixinClassify(rootPath, path))
                        .forEach(possibleMixinClasses::add);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("Found {} mixin classes", possibleMixinClasses.size());
        if (possibleMixinClasses.size() == 0) {
            throw new IllegalStateException("Found no mixin classes, something went very wrong");
        }
        // core.LwjglxDisplayMixin used to be excluded here because it was declared in mixins.celeritas.json's
        // `client` list instead. Measured 2026-10-15 (gpu-verifier, cohort + the fixed jar): the JSON entry
        // registers nothing - the list is parsed in the same deferred prepareConfigs pass as this one, which the
        // measured leg puts at 9.837 s, after Display is defined at 8.975 s - so with the exclusion in place the
        // mixin was registered by nobody at all (cleanmix.log: 0 LwjglxDisplayMixin lines and 64 APPLY against the
        // unmodified jar's 3 and 65). The mixin and the JSON entry are both gone; the window hint comes from
        // CeltiumGllessWindowTransformer and the stub from this plugin's onLoad, and the cohort leg passes with
        // openGl.vendor="Celtium" without either of them.
        return new ArrayList<>(possibleMixinClasses);
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
