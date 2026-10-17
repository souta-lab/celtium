#!/usr/bin/env python3
"""Execute the production loading-state transfer without Minecraft/GPU dependencies."""
from pathlib import Path
import shutil
import subprocess
import tempfile
from loading_capture_boundary_check import method

ROOT = Path(__file__).resolve().parents[2]
HOOK = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/core/LoadingScreenRendererMixin.java"
BRIDGE = ROOT / "forge122/src/main/java/org/taumc/celeritas/interfaces/LoadingProgressState.java"


def main():
    source = HOOK.read_text()
    assert 'method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN")' in source
    transfer = method(source, "private void celtium$preserveLoadingTask(")
    getters = "\n".join(method(source, signature) for signature in (
        "public String celtium$loadingTitle(", "public String celtium$loadingMessage(",
        "public boolean celtium$loadingSuccess(",
    ))
    java = r'''
import org.taumc.celeritas.interfaces.LoadingProgressState;
class LoadingResizeCheck {
    static class Minecraft { Object loadingScreen; }
    static class CallbackInfo { }
    static class Loading implements LoadingProgressState {
        String currentlyDisplayedText = "", message = "";
        boolean loadingSuccess;
        final Object framebuffer = new Object(), scaledResolution = new Object();
''' + transfer + getters + r'''
    }
    static void check(boolean ok,String label) { if (!ok) throw new AssertionError(label); }
    static Loading resize(Minecraft mc) {
        Loading replacement = new Loading();
        replacement.celtium$preserveLoadingTask(mc,null); // constructor RETURN precedes field assignment
        mc.loadingScreen = replacement;
        return replacement;
    }
    public static void main(String[] args) {
        Minecraft mc = new Minecraft();
        System.setProperty("celtium.vulkan.glless","true");
        Loading first = resize(mc);
        check(first.currentlyDisplayedText.equals("") && first.message.equals("") && !first.loadingSuccess,
              "initial construction fabricated a loading task");
        mc.loadingScreen = new Object();
        check(resize(mc).currentlyDisplayedText.equals(""),"non-bridge predecessor was not safe");
        Loading old = new Loading();
        old.currentlyDisplayedText = "Loading world"; old.message = "Building terrain"; old.loadingSuccess = true;
        mc.loadingScreen = old;
        System.setProperty("celtium.vulkan.glless","false");
        Loading legacy = resize(mc);
        check(legacy.currentlyDisplayedText.equals("") && legacy.message.equals("") && !legacy.loadingSuccess,
              "changed the legacy GL path");
        mc.loadingScreen = old;
        System.setProperty("celtium.vulkan.glless","true");
        Loading next = resize(mc);
        check(next.currentlyDisplayedText.equals("Loading world"),"resize lost loading title");
        check(next.message.equals("Building terrain"),"resize lost secondary message");
        check(next.loadingSuccess,"resize lost saving/shutdown permission");
        check(next.framebuffer!=old.framebuffer && next.scaledResolution!=old.scaledResolution,
              "resized renderer reused stale size-dependent resources");
        check(old.currentlyDisplayedText.equals("Loading world") && old.loadingSuccess,"mutated the predecessor");
        next.currentlyDisplayedText = "世界を保存中"; next.message = "最新の状態"; next.loadingSuccess = false;
        Loading third = resize(mc);
        check(third.currentlyDisplayedText.equals("世界を保存中") && third.message.equals("最新の状態")
              && !third.loadingSuccess,"consecutive replacement did not copy the current task exactly");
        System.out.println("Loading resize task transfer: initial/foreign/legacy/native/repeated/resources cases PASS");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-loading-state-") as tmp:
        path = Path(tmp) / "LoadingResizeCheck.java"
        path.write_text(java)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(BRIDGE), str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "LoadingResizeCheck"], check=True)
        # Before the fix, constructing a replacement left the task at its empty defaults.
        no_transfer = transfer[:transfer.index("{")+1] + "}"
        path.write_text(java.replace(transfer, no_transfer))
        subprocess.run([shutil.which("javac"), "-d", tmp, str(BRIDGE), str(path)], check=True)
        result = subprocess.run([shutil.which("java"), "-cp", tmp, "LoadingResizeCheck"], capture_output=True, text=True)
        assert result.returncode!=0 and "resize lost loading title" in result.stderr
    print("Regression rejects replacement without preserving task state: PASS")


if __name__ == "__main__":
    main()
