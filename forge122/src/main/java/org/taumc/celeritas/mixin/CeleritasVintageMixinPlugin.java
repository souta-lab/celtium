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
        // A mixin that cannot be applied does not degrade, it takes the run with it: the failure surfaces inside
        // the transformer while the class loader is defining the target, so the class is never defined and the
        // game dies on NoClassDefFoundError. That is measured, not assumed - with LoliASM 5.33 present,
        // ExplicitGcMixin's injection into Minecraft fails, MixinProcessor converts it to a critical
        // MixinTransformerError, and the run exits before a frame is drawn (rounds 702-705). Neither
        // @Redirect(require = 0) nor shouldApplyMixin can reach that path; the only lever is whether the class is
        // registered at all, which is decided here.
        //
        // So a class whose target is contested is dropped from the list, by name, with the reason logged. What is
        // lost is the optimisation it carried, which is why the drop is named rather than silent: an unlogged
        // exclusion reads exactly like a mixin that applied.
        if (possibleMixinClasses.remove("core.ExplicitGcMixin")) {
            LOGGER.warn("Excluding core.ExplicitGcMixin: {} is on the classpath and also transforms "
                    + "net.minecraft.client.Minecraft, where this mixin's injection does not survive. The vanilla "
                    + "System.gc() calls stay in place; the pause they cause is kept rather than the run being lost.",
                    contestedTargetProbe());
        }
        return new ArrayList<>(possibleMixinClasses);
    }

    /**
     * {@return the name of the coremod whose presence makes {@code ExplicitGcMixin} unsafe}, or a note that it was
     * not found.
     * <p>
     * The probe is the coremod's own plugin class, which is on the classpath by the time a mixin config plugin
     * runs. The mod list is deliberately not consulted: this runs before it is dependable, and a wrong answer here
     * would either drop a mixin that would have applied or keep one that kills the run.
     */
    private static String contestedTargetProbe() {
        try {
            Class.forName("zone.rong.loliasm.core.LoliLoadingPlugin", false,
                    CeleritasVintageMixinPlugin.class.getClassLoader());
            return "LoliASM";
        } catch (Throwable absent) {
            return "an unidentified transformer";
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
