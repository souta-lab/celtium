package org.soutalab.celtium.probe;

import com.google.gson.GsonBuilder;

/** Optional diagnostics entry point; invoke only on a thread with a current GL context. */
public final class CeltiumProbe {
    private CeltiumProbe() { }

    public static ProbeReport inspectCurrentContext() {
        ProbeReport report = new ProbeReport();
        try {
            report.lwjglVersion = org.lwjgl.Version.getVersion();
            report.openGl = CurrentGlProbe.inspect();
        } catch (RuntimeException | LinkageError error) {
            report.reasons.add("OpenGL query unavailable: " + describe(error));
        }
        try {
            VulkanProbe.inspect(report);
        } catch (RuntimeException | LinkageError error) {
            report.reasons.add("Vulkan query unavailable: " + describe(error));
        }
        InteropEligibility.evaluate(report);
        return report;
    }

    public static String inspectCurrentContextJson() {
        return toJson(inspectCurrentContext());
    }

    static String toJson(ProbeReport report) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report);
    }

    static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }
}
