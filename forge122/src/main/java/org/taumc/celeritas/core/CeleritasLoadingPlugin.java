package org.taumc.celeritas.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("Celeritas")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class CeleritasLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    /**
     * The GL-less window hint is set from a transformer rather than only from a mixin, because a mixin can lose a
     * race it cannot see: measured 2026-10-15, RandomPatches' tweak defines {@code org.lwjgl.opengl.Display} at
     * 8.975 s in the 268-mod E2E pack while Mixin prepares its configurations at 9.837 s (leg
     * {@code gv-e2e-268-xlog}), so {@code core.LwjglxDisplayMixin} never applied and the window got a real GL
     * context. This array is read when the coremod loads, before either.
     */
    /**
     * The earliest Java point this mod controls, and therefore the right place to install the GL-less capabilities.
     * <p>
     * LWJGL's ICD is write-once, so whoever sets capabilities first supplies the addresses every generated GL class
     * reads; the plugin's {@code onLoad} (7.1 s) is already too late in a 268-mod pack. Measured offline: a set built
     * from this stub's own 427 extension names has {@code GL_ARB_shader_objects=true} and a real
     * {@code glCreateShaderObjectARB} address, while one built from the stub's empty {@code GL_EXTENSIONS} answer has
     * {@code false} and {@code 0x0} - which is the abort the pack legs keep hitting.
     */
    @Override
    public @Nullable String[] getASMTransformerClass() {
        if (!"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            try {
                org.soutalab.celtium.vulkan.gl.CeltiumGlStub.install();
                org.soutalab.celtium.vulkan.gl.CeltiumGlStub.installFirstCapabilities();
            } catch (Throwable error) {
                org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin.LOGGER.warn(
                        "CELTIUM_GLLESS early capabilities install failed: {}", error.toString());
            }
        }

        return new String[] {"org.taumc.celeritas.core.CeltiumGllessWindowTransformer",
                "org.taumc.celeritas.core.CeltiumGllessNativeTransformer"};
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> map) {

    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.celeritas.json");
    }
}
