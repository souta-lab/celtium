package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * The per-frame half of the weather column instancing, set once per frame.
 */
public final class WeatherParams {

    public static float translateX;
    public static float translateY;
    public static float translateZ;
    public static float invRadius;

    public static float cameraFracX;
    public static float cameraFracZ;
    public static float partialTicks;
    public static float age;

    public static float vanillaTick;
    public static float providerTick;
    public static float rainStrength;
    public static float providerAge;

    public static float providerInvRadius;

    public static final int TICK_WRAP = 1024;

    public static int generation;

    private WeatherParams() {}

    public static void set(float translateX, float translateY, float translateZ, float invRadius,
        float cameraFracX, float cameraFracZ, float partialTicks, float age,
        int vanillaTick, float rainStrength, int providerTick, float providerAge, float providerInvRadius) {
        WeatherParams.translateX = translateX;
        WeatherParams.translateY = translateY;
        WeatherParams.translateZ = translateZ;
        WeatherParams.invRadius = invRadius;
        WeatherParams.cameraFracX = cameraFracX;
        WeatherParams.cameraFracZ = cameraFracZ;
        WeatherParams.partialTicks = partialTicks;
        WeatherParams.age = age;
        WeatherParams.vanillaTick = vanillaTick & (TICK_WRAP - 1);
        WeatherParams.providerTick = providerTick & (TICK_WRAP - 1);
        WeatherParams.rainStrength = rainStrength;
        WeatherParams.providerAge = providerAge;
        WeatherParams.providerInvRadius = providerInvRadius;
        generation++;
    }
}
