#!/usr/bin/env python3
"""Execute production atlas submit/await for a failed custom loader and a later reload.

Forge calls Stitcher.addSprite *only if* generateMipmaps returns true. Its redirect
is the sole way into `await`, so a failed custom loader is never consumed there.
A REFUSED marker on false therefore leaks and breaks a later successful reload.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
LOADER=ROOT/'forge122/src/main/java/org/taumc/celeritas/impl/CeltiumAtlasLoader.java'
MIXIN=ROOT/'forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureMapParallelMixin.java'


def method(source, signature):
    i=source.index(signature)
    start=source.index('{',i)
    n,end=1,start+1
    while n:
        if end>=len(source):raise RuntimeError('Unterminated method '+signature)
        n+=(source[end]=='{')-(source[end]=='}')
        end+=1
    return source[i:end]


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=Path,default=LOADER)
    opts=parser.parse_args()
    mixin=MIXIN.read_text()
    assert 'if (!CeltiumAtlasLoader.ENABLED)' in mixin
    assert 'return CeltiumAtlasLoader.submit(self,' in mixin
    assert 'this.celeritas$pendingStitch.add(sprite);' in mixin
    src=opts.source.read_text()
    submit=method(src,'public static boolean submit(final TextureMap map, final String basePath, final int mipmapLevels,')
    await_body=method(src,'public static boolean await(TextureAtlasSprite sprite) throws Throwable')
    java='''import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
class TextureMap {}
class IResourceManager {}
class ResourceLocation {}
class TextureAtlasSprite {
    final String name;
    boolean custom = true;
    boolean loaded;
    int frames;
    TextureAtlasSprite(String n){name=n;}
    boolean hasCustomLoader(IResourceManager mgr,ResourceLocation loc){return custom;}
    int getFrameCount(){return frames;}
    String getIconName(){return name;}
}
public class CeltiumAtlasLoader {
    static class Logger {void error(String f,String n) {}}
    static final Logger LOGGER=new Logger();
    static final Map<TextureAtlasSprite,Future<Boolean>> PENDING=new ConcurrentHashMap<>();
    static final Set<TextureAtlasSprite> REFUSED=ConcurrentHashMap.newKeySet();
    static final ExecutorService POOL=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
    static final AtomicLong workerBusyMillis=new AtomicLong();
    static long submittedSprites,firstSubmitNanos,parallelLoads,awaitedSprites,awaitedMillis,lastReportedStitch,windowMillis;
    static ResourceLocation resourceLocationOf(String base,TextureAtlasSprite sprite){return new ResourceLocation();}
    static boolean generateMipmapsSequential(String base,int mip,IResourceManager mgr,TextureAtlasSprite sprite){return sprite.loaded;}
    static boolean generateMipmapsWorker(IResourceManager mgr,ResourceLocation loc,int mip,TextureAtlasSprite s){return true;}
    static String summary(){return "atlas test";}
'''+submit+'\n'+await_body+'''
    static void require(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static boolean mimicForgeStitch(TextureAtlasSprite sprite) throws Throwable {
        // Forge TextureMap.loadTexture: if (generateMipmaps(...)) stitcher.addSprite(sprite);
        if (!submit(new TextureMap(),"textures",0,new IResourceManager(),sprite))return false;
        return await(sprite); // Mixin redirect defers the addSprite until this point
    }
    public static void main(String[] args) throws Throwable {
        TextureAtlasSprite reused=new TextureAtlasSprite("mod:flaky-custom");
        reused.loaded=false; reused.frames=0;
        require(!mimicForgeStitch(reused),"a failed loader must not be stitched");
        require(REFUSED.isEmpty(),"failed synchronous loader cannot leave a marker: Forge skips addSprite/await");
        reused.loaded=true; reused.frames=1;
        require(mimicForgeStitch(reused),"same sprite must stitch on subsequent successful resource reload");
        for(int i=0;i<300;i++) {
            TextureAtlasSprite fail=new TextureAtlasSprite("mod:failed-"+i);
            require(!mimicForgeStitch(fail),"custom decoder returned failure");
        }
        require(REFUSED.isEmpty(),"no unbounded refused-marker leak after repeated failed mod sprites");
        require(PENDING.isEmpty(),"custom loaders must not create worker futures");
        System.out.println("atlas sync custom loader: false path cleaned, successful reload accepted, 300 failures leak-free PASS");
        POOL.shutdownNow();
    }
}
'''
    with tempfile.TemporaryDirectory() as directory:
        path=Path(directory)/'CeltiumAtlasLoader.java'
        path.write_text(java)
        subprocess.run(['javac','--release','21','-d',directory,str(path)],check=True)
        subprocess.run(['java','-cp',directory,'CeltiumAtlasLoader'],check=True)


if __name__=='__main__':
    main()
