#!/usr/bin/env python3
"""Execute the fixture's producer capture with frozen ticks; check the swap hook's boundary."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java"
HOOK = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java"


def method(source, signature):
    start = source.index(signature)
    pos = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[pos] == "{") - (source[pos] == "}")
        pos += 1
    return source[start:pos]


def main():
    source = SOURCE.read_text()
    hook = HOOK.read_text()
    assert 'method = "setLoadingProgress(I)V"' in hook
    assert '@At(value = "INVOKE"' in hook and 'target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"' in hook
    assert 'shift =' not in hook, "request must precede the producer's present, not follow it"
    assert 'Boolean.getBoolean("celtium.validation.dumpLoadingProgress")' in hook
    assert '.getMethod("requestLoadingProgressDump", int.class, String.class, String.class)' in hook
    body = method(source, "public static void requestLoadingProgressDump(")
    code = r'''
import java.util.*;
class ValidationClient {
    static class JsonObject {
        final Map<String,Object> data = new LinkedHashMap<>();
        void addProperty(String key,Object value) { data.put(key,value); }
        void add(String key,JsonObject value) { data.put(key,value); }
    }
    static class Minecraft {
        static final Minecraft MC = new Minecraft();
        Object world = null, currentScreen = null;
        int displayWidth = 1280, displayHeight = 720;
        static Minecraft getMinecraft() { return MC; }
    }
    static class PictureEvidence { int presentedFramesAtRequest = 12; }
    static ValidationClient instance;
    boolean enabled, launched, finished, celtium$controlledLoadingProbe;
    int celtium$loadingProgressCaptures, celtium$clientTicks = 7, worldTicks = 0, requests;
    JsonObject report = new JsonObject();
    PictureEvidence celtium$requestPresentedDump(Minecraft mc,String stage) {
        check(stage.equals("loading-progress-"+(requests+1)),"wrong request stage");
        requests++; return new PictureEvidence();
    }
''' + body + r'''
    static void check(boolean yes,String label) { if (!yes) throw new AssertionError(label); }
    static void capture() { requestLoadingProgressDump(-1,"Loading level","Preparing spawn area"); }
    public static void main(String[] args) {
        System.setProperty("celtium.validation.dumpLoadingProgress","true");
        System.setProperty("celtium.validation.dumpPresentedImage","true");
        capture(); // absent fixture
        instance = new ValidationClient();
        capture(); check(instance.requests==0,"disabled fixture captured");
        instance.enabled = true;
        capture(); check(instance.requests==0,"pre-launch frame captured");
        instance.launched = true;
        System.setProperty("celtium.validation.dumpLoadingProgress","false");
        capture(); check(instance.requests==0,"disabled producer capture ran");
        System.setProperty("celtium.validation.dumpLoadingProgress","true");
        System.setProperty("celtium.validation.dumpPresentedImage","false");
        capture(); check(instance.requests==0,"disabled image capture ran");
        System.setProperty("celtium.validation.dumpPresentedImage","true");
        instance.finished = true;
        capture(); check(instance.requests==0,"finished fixture captured");
        instance.finished = false;
        capture();
        instance.celtium$controlledLoadingProbe = true;
        requestLoadingProgressDump(50,"Celtium loading probe","Controlled renderer probe (not world progress)");
        JsonObject nativeFrame = (JsonObject)instance.report.data.get("loading-progress-1");
        JsonObject controlled = (JsonObject)instance.report.data.get("loading-progress-2");
        check(nativeFrame.data.get("progress").equals(-1) && nativeFrame.data.get("controlledProbe").equals(false),
              "native indeterminate frame mislabeled");
        check(controlled.data.get("progress").equals(50) && controlled.data.get("controlledProbe").equals(true),
              "controlled numeric frame mislabeled");
        check(nativeFrame.data.get("clientTick").equals(7) && controlled.data.get("clientTick").equals(7)
              && controlled.data.get("worldTick").equals(0) && controlled.data.get("worldPresent").equals(false),
              "capture depended on advancing ticks/world existence");
        check(nativeFrame.data.get("title").equals("Loading level")
              && controlled.data.get("windowSize").equals("1280x720"),"producer identity missing");
        for (int i=0;i<20;i++) capture();
        check(instance.requests==8 && instance.celtium$loadingProgressCaptures==8 && instance.report.data.size()==8,
              "producer capture limit not enforced");
        check(instance.celtium$clientTicks==7 && instance.worldTicks==0,"capture changed simulation time");
        System.out.println("Loading producer capture: guards, constant ticks, native/controlled identity and bound PASS");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-loading-capture-") as tmp:
        path = Path(tmp) / "ValidationClient.java"
        path.write_text(code)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "ValidationClient"], check=True)
    print("Loading swap hook: descriptor + pre-present invocation + opt-in guard PASS")


if __name__ == "__main__":
    main()
