#!/usr/bin/env python3
"""Execute the production atlas await logic, including an unfinished sprite Future.

The worker populates sprite frames asynchronously. Checking frameCount before
future.get() used to falsely refuse valid sprites and leave their tasks in PENDING.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
LOADER = ROOT / 'forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java'


def method(source: str, signature: str) -> str:
    pos = source.index(signature)
    start = source.index('{', pos)
    depth, end = 1, start + 1
    while depth:
        if end >= len(source):
            raise RuntimeError(f'Unterminated production method {signature}')
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[pos:end]


def run(source: str) -> None:
    await_method = method(source, 'public static boolean await(TextureAtlasSprite sprite) throws Throwable')
    helper = method(LOADER.read_text(), 'private static boolean stitchable(TextureAtlasSprite sprite)')
    java = '''import java.util.*;
import java.util.concurrent.*;
class TextureAtlasSprite {
    volatile int frames;
    final String name;
    TextureAtlasSprite(String n, int count) {name=n;frames=count;}
    int getFrameCount() {return frames;}
    String getIconName() {return name;}
}
public class CeltiumAtlasLoader {
    static final Map<TextureAtlasSprite, Future<Boolean>> PENDING=new ConcurrentHashMap<>();
    static class Logger {void error(String pattern,String name) { /* expected on missing frames */ }}
    static final Logger LOGGER=new Logger();
    static long awaitedSprites, awaitedMillis, lastReportedStitch, windowMillis;
    static long firstSubmitNanos=System.nanoTime();
    static String summary() {return "atlas test";}
''' + await_method + '\n' + helper + '''
    static void require(boolean v, String msg) {if(!v)throw new AssertionError(msg);}
    public static void main(String[] args) throws Throwable {
        // A just-submitted async sprite has ZERO frames before its worker finishes.
        TextureAtlasSprite late=new TextureAtlasSprite("mod:late",0);
        CompletableFuture<Boolean> pending=new CompletableFuture<>();
        PENDING.put(late,pending);
        Thread worker=new Thread(() -> {
            try {Thread.sleep(85);} catch(InterruptedException e) {throw new RuntimeException(e);}
            late.frames=1;
            pending.complete(true);
        });
        worker.setDaemon(true);worker.start();
        long begin=System.nanoTime();
        require(await(late),"successful worker must be awaited before frame-count gate");
        long elapsedMs=(System.nanoTime()-begin)/1_000_000L;
        require(elapsedMs>=30,"drain returned before pending worker completed");
        require(PENDING.isEmpty(),"await must remove the completed future");
        require(awaitedSprites==1,"successful worker must count as awaited");

        TextureAtlasSprite empty=new TextureAtlasSprite("mod:empty",0);
        PENDING.put(empty,CompletableFuture.completedFuture(true));
        require(!await(empty),"worker success with zero frames must be refused");
        require(PENDING.isEmpty(),"empty sprite must not retain stale future");
        require(awaitedSprites==2,"zero-frame async worker must still count in await metric");

        TextureAtlasSprite failed=new TextureAtlasSprite("mod:failed",1);
        PENDING.put(failed,CompletableFuture.completedFuture(false));
        require(!await(failed),"worker returning false must be refused");
        require(PENDING.isEmpty(),"failed worker must be drained");

        TextureAtlasSprite customEmpty=new TextureAtlasSprite("mod:sync-empty",0);
        // Failed custom-loader submit already returned false to Forge, so it was never enqueued
        // for stitching. A direct frame-less nonasync request must still be rejected.
        require(!await(customEmpty),"frame-less sync sprite must not be stitched");

        require(!await(new TextureAtlasSprite("mod:missing",0)),"sync no-frames must not be stitched");
        require(await(new TextureAtlasSprite("mod:ready",1)),"sync populated sprite must be stitched");

        TextureAtlasSprite broken=new TextureAtlasSprite("mod:throws",0);
        CompletableFuture<Boolean> exception=new CompletableFuture<>();
        exception.completeExceptionally(new IllegalStateException("decode failure"));
        PENDING.put(broken,exception);
        boolean threw=false;
        try {await(broken);}catch(ExecutionException expected){threw=true;}
        require(threw,"worker exceptions must propagate to original caller");
        require(PENDING.isEmpty(),"exceptional worker must be drained");
        require(awaitedSprites==4,"success/refusal/exception await census must match");
        System.out.println("parallel atlas: delayed valid frame, empty/refused/failed sprites, cleanup, exception PASS");
    }
}
'''
    with tempfile.TemporaryDirectory() as directory:
        path=Path(directory)/'CeltiumAtlasLoader.java'
        path.write_text(java)
        subprocess.run(['javac','--release','21','-d',directory,str(path)],check=True)
        subprocess.run(['java','-cp',directory,'CeltiumAtlasLoader'],check=True)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source', type=Path,default=LOADER)
    args=parser.parse_args()
    run(args.source.read_text())


if __name__=='__main__':
    main()
