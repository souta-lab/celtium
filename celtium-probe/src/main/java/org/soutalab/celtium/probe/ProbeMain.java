package org.soutalab.celtium.probe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ProbeMain {
    private ProbeMain() { }

    public static void main(String[] args) throws Exception {
        Path output = null;
        boolean requireCompatible = false;
        int deviceIndex = -1;
        for (int i = 0; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) output = Paths.get(args[++i]);
            else if ("--require-compatible".equals(args[i])) requireCompatible = true;
            else if ("--egl-device".equals(args[i]) && i + 1 < args.length) {
                deviceIndex = Integer.parseInt(args[++i]);
                if (deviceIndex < 0) throw new IllegalArgumentException("EGL device index must be nonnegative");
            } else throw new IllegalArgumentException("Usage: celtium-probe [--egl-device index] [--output report.json] [--require-compatible]");
        }
        ProbeReport report;
        try (HeadlessGlContext context = HeadlessGlContext.create(deviceIndex)) {
            report = CeltiumProbe.inspectCurrentContext();
        } catch (RuntimeException | LinkageError error) {
            report = new ProbeReport();
            report.reasons.add("Headless context unavailable: " + CeltiumProbe.describe(error));
            InteropEligibility.evaluate(report);
        }
        report.contextSource = "headless-egl";
        if (deviceIndex >= 0) report.eglDeviceIndex = deviceIndex;
        String json = CeltiumProbe.toJson(report);
        System.out.println(json);
        if (output != null) {
            Path absolute = output.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            Files.write(absolute, (json + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (requireCompatible && !"CANDIDATE_FOR_INTEROP_TEST".equals(report.status)) System.exit(2);
    }
}
