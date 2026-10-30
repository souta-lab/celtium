#!/usr/bin/env python3
"""Execute the production loading-state transfer and resize assignment without a GPU."""
from pathlib import Path
import shutil
import subprocess
import tempfile
from loading_capture_boundary_check import method

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/core"
BRIDGE = ROOT / "forge122/src/main/java/org/taumc/celeritas/interfaces/LoadingProgressState.java"


def main():
    loading = (CORE / "LoadingScreenRendererMixin.java").read_text()
    minecraft = (CORE / "MinecraftMixin.java").read_text()
    assert 'method = "resize(II)V"' in minecraft and 'opcode = Opcodes.PUTFIELD' in minecraft
    assert 'target = "Lnet/minecraft/client/Minecraft;loadingScreen:Lnet/minecraft/client/LoadingScreenRenderer;"' in minecraft
    assert '<init>' not in loading, "unrelated progress-renderer construction must remain untouched"
    selector = method(minecraft, "private void celtium$replaceLoadingScreen(")
    transfer = method(loading, "public void celtium$copyLoadingTask(")
    getters = "\n".join(method(loading, signature) for signature in (
        "public String celtium$loadingTitle(", "public String celtium$loadingMessage(",
        "public boolean celtium$loadingSuccess(",
    ))
    java = r'''
import org.taumc.celeritas.interfaces.LoadingProgressState;
class LoadingResizeCheck {
    static class Minecraft { Object loadingScreen; }
    static class LoadingScreenRenderer implements LoadingProgressState {
        String currentlyDisplayedText = "", message = "";
        boolean loadingSuccess;
        final Object framebuffer = new Object(), scaledResolution = new Object();
''' + transfer + getters + r'''
    }
''' + selector + r'''
    static void check(boolean ok,String label) { if (!ok) throw new AssertionError(label); }
    static LoadingScreenRenderer resize(Minecraft mc) {
        LoadingScreenRenderer replacement = new LoadingScreenRenderer();
        new LoadingResizeCheck().celtium$replaceLoadingScreen(mc,replacement);
        check(mc.loadingScreen==replacement,"replacement was not assigned");
        return replacement;
    }
    public static void main(String[] args) {
        Minecraft mc = new Minecraft();
        LoadingScreenRenderer first = resize(mc);
        check(first.currentlyDisplayedText.equals("") && first.message.equals("") && !first.loadingSuccess,
              "initial construction fabricated a loading task");
        mc.loadingScreen = new Object();
        check(resize(mc).currentlyDisplayedText.equals(""),"non-screen predecessor was not safe");
        LoadingScreenRenderer old = new LoadingScreenRenderer();
        old.currentlyDisplayedText = "Loading world"; old.message = "Building terrain"; old.loadingSuccess = true;
        mc.loadingScreen = old;
        LoadingScreenRenderer unrelated = new LoadingScreenRenderer();
        check(unrelated.currentlyDisplayedText.equals("") && !unrelated.loadingSuccess,
              "unrelated progress renderer inherited another task");
        mc.loadingScreen = old;
        LoadingScreenRenderer next = resize(mc);
        check(next.currentlyDisplayedText.equals("Loading world"),"resize lost loading title");
        check(next.message.equals("Building terrain"),"resize lost secondary message");
        check(next.loadingSuccess,"resize lost saving/shutdown permission");
        check(next.framebuffer!=old.framebuffer && next.scaledResolution!=old.scaledResolution,
              "resized renderer reused stale size-dependent resources");
        check(old.currentlyDisplayedText.equals("Loading world") && old.loadingSuccess,"mutated the predecessor");
        next.currentlyDisplayedText = "世界を保存中"; next.message = "最新の状態"; next.loadingSuccess = false;
        LoadingScreenRenderer third = resize(mc);
        check(third.currentlyDisplayedText.equals("世界を保存中") && third.message.equals("最新の状態")
              && !third.loadingSuccess,"consecutive replacement did not copy the current task exactly");
        System.out.println("Loading resize transfer: initial/foreign/repeated/resources/unrelated cases PASS");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-loading-state-") as tmp:
        path = Path(tmp) / "LoadingResizeCheck.java"
        path.write_text(java)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(BRIDGE), str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "LoadingResizeCheck"], check=True)
        # Previous behavior assigned a freshly constructed renderer with an empty task.
        no_transfer = selector[:selector.index("{")+1] + "mc.loadingScreen = replacement;}"
        path.write_text(java.replace(selector, no_transfer))
        subprocess.run([shutil.which("javac"), "-d", tmp, str(BRIDGE), str(path)], check=True)
        result = subprocess.run([shutil.which("java"), "-cp", tmp, "LoadingResizeCheck"], capture_output=True, text=True)
        assert result.returncode!=0 and "resize lost loading title" in result.stderr
    print("Regression rejects replacement without preserving task state: PASS")


if __name__ == "__main__":
    main()
