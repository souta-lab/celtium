package org.taumc.celeritas.mixin;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.util.MixinClassValidator;
import org.objectweb.asm.tree.ClassNode;
import org.soutalab.celtium.patch.PatchGate;
import org.soutalab.celtium.patch.PatchGroups;
import org.soutalab.celtium.patch.PatchLedger;
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
            org.soutalab.celtium.vulkan.gl.GlsmFrontend.install();
            LOGGER.info("CELTIUM_GLSTUB stub GL provider installed early (from the mixin plugin)");
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("CELTIUM_GLSTUB early stub install failed: {}", error.toString());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    /**
     * This {@code false} is load-bearing, and it is not the patch gate.
     * <p>
     * The companion list returned by {@link #getMixins()} is prepared with the plugin ignored
     * (CleanMix prepares companion mixins with {@code ignorePlugin = true}), so this method is never consulted
     * for celtium's own mixins - the group gate therefore lives in {@code getMixins()} itself, which is the
     * only place a companion mixin can be refused before it is registered. What this {@code false} does decide
     * is the fate of the two entries in {@code mixins.celeritas.json}'s {@code client} list
     * ({@code core.SplashProgressMixin} and {@code core.GLAllocationMixin}): the JSON arrays are prepared with
     * the plugin consulted, so both are refused here, and because the JSON pass runs first and claims their
     * names in the global mixin list, the companion pass then skips them as duplicates. The two are shadowed,
     * not dead code in the JSON: deleting those JSON lines, or returning {@code true} here, would register and
     * apply both mixins for the first time. That ruling is item 356-B's; changing it is a separate item with
     * its own leg, because both mixins duplicate answers the transformers and the GL stub already give.
     */
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
        List<String> discovered = new ArrayList<>(possibleMixinClasses);
        List<String> registered = discovered;
        PatchLedger ledger;
        try {
            // The patch gate (item 356-B): filter the companion list by patch group before it is handed to
            // Mixin. This must never throw. Mixin's post-initialise call site catches Exception and carries on
            // with the config registered and zero companion mixins, so a gate that threw would not stop the
            // game - it would start it with every patch silently gone. Any failure therefore returns the
            // discovered list untouched: exactly the behaviour before the gate existed.
            ledger = celtium$patchLedger();
            registered = PatchGate.register(discovered, ledger);
        } catch (Throwable error) {
            LOGGER.warn("CELTIUM_PATCH_TABLE gate=fail-open error={}: {}",
                    error.getClass().getSimpleName(), celtium$firstLine(error));
            return discovered;
        }
        try {
            celtium$announcePatches(ledger, discovered, registered);
        } catch (Throwable error) {
            LOGGER.warn("CELTIUM_PATCH_TABLE announce=failed error={}", error.getClass().getSimpleName());
        }
        return registered;
    }

    private static PatchLedger celtium$ledger;
    private static boolean celtium$announced;

    /** {@return the launch's patch ledger, loaded once; the decision is a launch property, not a per-call one}. */
    private static synchronized PatchLedger celtium$patchLedger() {
        if (celtium$ledger == null) {
            celtium$ledger = PatchLedger.load();
        }
        return celtium$ledger;
    }

    /**
     * Announces the gate's decision once per launch: one skip line per filtered group, one line per ignored
     * switch, the table summary, and - only when the census switch is on - one registration line per mixin.
     * The skipped map is also published on the launch blackboard for a future screen; that hand-off is inside
     * the caller's guard, so a blackboard that is not ready yet costs the publication, never the decision.
     */
    private void celtium$announcePatches(PatchLedger ledger, List<String> discovered, List<String> registered) {
        synchronized (CeleritasVintageMixinPlugin.class) {
            if (celtium$announced) {
                return;
            }
            celtium$announced = true;
        }
        for (String group : ledger.skippedGroups()) {
            LOGGER.info(ledger.skippedLine(group));
        }
        for (String line : ledger.ignoredLines()) {
            LOGGER.info(line);
        }
        LOGGER.info(ledger.tableLine(discovered.size(), registered.size()));
        if (ledger.census()) {
            for (String name : registered) {
                LOGGER.info("CELTIUM_PATCH_REGISTERED mixin={} group={}", name, PatchGroups.of(name));
            }
        }
        try {
            if (Launch.blackboard != null) {
                Launch.blackboard.put(PatchLedger.SKIPPED_BLACKBOARD_KEY, ledger.skipped());
            }
        } catch (Throwable error) {
            LOGGER.warn("CELTIUM_PATCH_TABLE blackboard=failed error={}", error.getClass().getSimpleName());
        }
    }

    private static String celtium$firstLine(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return "";
        }
        int end = message.indexOf('\n');
        return (end < 0 ? message : message.substring(0, end)).trim();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
