/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (MC/GLSM-free).
 */
package org.soutalab.celtium.shader.texture.format;

import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.shader.texture.PBRType;
import org.soutalab.celtium.shader.texture.mipmap.ChannelMipmapGenerator;
import org.soutalab.celtium.shader.texture.mipmap.CustomMipmapGenerator;
import org.soutalab.celtium.shader.texture.mipmap.DiscreteBlendFunction;
import org.soutalab.celtium.shader.texture.mipmap.LinearBlendFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface TextureFormat {
    String name();

    @Nullable
    String version();

    default List<String> getDefines() {
        List<String> defines = new ArrayList<>();
        String defineName = name().toUpperCase(Locale.ROOT).replaceAll("-", "_");
        defines.add("MC_TEXTURE_FORMAT_" + defineName);
        if (version() != null) {
            String defineVersion = version().replaceAll("[.-]", "_");
            defines.add("MC_TEXTURE_FORMAT_" + defineName + "_" + defineVersion);
        }
        return defines;
    }

    default boolean canInterpolateValues(PBRType pbrType) {
        return true;
    }

    @Nullable
    default CustomMipmapGenerator getMipmapGenerator(PBRType pbrType) {
        return null;
    }

    final class LabPBR implements TextureFormat {
        private final String name;
        private final String version;

        public LabPBR(String name, @Nullable String version) {
            this.name = name;
            this.version = version;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        @Nullable
        public String version() {
            return version;
        }

        @Override
        public boolean canInterpolateValues(PBRType pbrType) {
            return pbrType != PBRType.SPECULAR;
        }

        @Override
        @Nullable
        public CustomMipmapGenerator getMipmapGenerator(PBRType pbrType) {
            if (pbrType == PBRType.SPECULAR) {
                return new ChannelMipmapGenerator(
                        LinearBlendFunction.INSTANCE,
                        new DiscreteBlendFunction(v -> v < 230 ? 0 : 1),
                        new DiscreteBlendFunction(v -> v < 65 ? 0 : 1),
                        new DiscreteBlendFunction(v -> v < 255 ? 0 : 1));
            }
            return null;
        }
    }

    final class Registry {
        public static final Registry INSTANCE = new Registry();
        private final Map<String, Factory> factories = new ConcurrentHashMap<>();

        private Registry() {
            register("lab-pbr", LabPBR::new);
        }

        public void register(String name, Factory factory) {
            factories.put(name.toLowerCase(Locale.ROOT), factory);
        }

        @Nullable
        public TextureFormat create(String name, @Nullable String version) {
            Factory f = factories.get(name.toLowerCase(Locale.ROOT));
            return f != null ? f.create(name, version) : null;
        }

        @FunctionalInterface
        public interface Factory {
            TextureFormat create(String name, @Nullable String version);
        }
    }
}
