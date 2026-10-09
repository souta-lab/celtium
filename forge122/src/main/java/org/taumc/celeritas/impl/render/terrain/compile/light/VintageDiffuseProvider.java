package org.taumc.celeritas.impl.render.terrain.compile.light;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.embeddedt.embeddium.impl.model.light.DiffuseProvider;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

import static org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.*;

public enum VintageDiffuseProvider implements DiffuseProvider {
    INSTANCE;

    /**
     * Diagnostic switch: forces the face brightness to 1.0 so a run measures the frame without any diffuse term.
     * It answers whether the sided brightness is applied once (vanilla: top 1.0, sides 0.6/0.8) or twice, which
     * would square those factors - 0.36 for a side face against the reference's 0.6.
     */
    private static final boolean NO_SHADE = Boolean.getBoolean("celtium.terrain.noShade");

    @Override
    public float getDiffuse(float normalX, float normalY, float normalZ, boolean shade) {
        if (NO_SHADE || !shade) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(normalX, normalY, normalZ);
    }

    public static EnumFacing toEnumFacing(ModelQuadFacing facing) {
        return switch (facing) {
            case NEG_Y -> EnumFacing.DOWN;
            case POS_Y -> EnumFacing.UP;
            case NEG_Z -> EnumFacing.NORTH;
            case POS_Z -> EnumFacing.SOUTH;
            case NEG_X -> EnumFacing.WEST;
            case POS_X -> EnumFacing.EAST;
            case UNASSIGNED -> throw new IllegalArgumentException();
        };
    }

    public static ModelQuadFacing fromEnumFacing(EnumFacing facing) {
        return switch (facing) {
            case DOWN  -> NEG_Y;
            case UP    -> POS_Y;
            case NORTH -> NEG_Z;
            case SOUTH -> POS_Z;
            case WEST  -> NEG_X;
            case EAST  -> POS_X;
        };
    }

    public static ModelQuadFacing fromEnumFacingOrUnassigned(EnumFacing facing) {
        if (facing == null) {
            return UNASSIGNED;
        }
        return fromEnumFacing(facing);
    }

    @Override
    public float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        if (NO_SHADE || !shade) {
            return 1.0f;
        }
        return LightUtil.diffuseLight(toEnumFacing(lightFace));
    }
}
