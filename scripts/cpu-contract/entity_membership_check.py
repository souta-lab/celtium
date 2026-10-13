#!/usr/bin/env python3
"""Run the production entity gatherer, membership epoch, and hook handler with CPU Minecraft fixtures."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FORGE = ROOT / "forge122/src/main/java"


def main():
    stubs = {
        "net/minecraft/world/World.java": """package net.minecraft.world;public class World{public boolean isRemote=true;}""",
        "net/minecraft/client/multiplayer/WorldClient.java": """package net.minecraft.client.multiplayer;
import java.util.*;import net.minecraft.entity.Entity;
public class WorldClient extends net.minecraft.world.World {
 public long tick=1;public Object provider=new Object();public List<Entity> loadedEntityList=new ArrayList<>();
 public long getTotalWorldTime(){return tick;}public Object getChunkProvider(){return provider;}
}""",
        "net/minecraft/entity/Entity.java": """package net.minecraft.entity;
public class Entity {public boolean eligible=true;public static int currentPass;
 public boolean shouldRenderInPass(int pass){return eligible&&pass==currentPass;}}""",
        "net/minecraft/util/ClassInheritanceMultiMap.java": """package net.minecraft.util;
public class ClassInheritanceMultiMap<T> extends java.util.ArrayList<T>{}""",
        "net/minecraft/world/chunk/Chunk.java": """package net.minecraft.world.chunk;
import net.minecraft.entity.Entity;import net.minecraft.util.ClassInheritanceMultiMap;
public class Chunk implements org.taumc.celeritas.mixin.core.terrain.ChunkAccessor {
 public ClassInheritanceMultiMap<Entity> entities=new ClassInheritanceMultiMap<>();
 public boolean celeritas$getHasEntities(){return !entities.isEmpty();}
 @SuppressWarnings("unchecked") public ClassInheritanceMultiMap<Entity>[] getEntityLists(){return new ClassInheritanceMultiMap[]{entities};}
}""",
        "org/taumc/celeritas/mixin/core/terrain/ChunkAccessor.java": """package org.taumc.celeritas.mixin.core.terrain;
public interface ChunkAccessor{boolean celeritas$getHasEntities();}""",
        "org/taumc/celeritas/mixin/core/terrain/ChunkProviderClientAccessor.java": """package org.taumc.celeritas.mixin.core.terrain;
public interface ChunkProviderClientAccessor{java.util.Map<Long,net.minecraft.world.chunk.Chunk> celeritas$getLoadedChunks();}""",
        "org/spongepowered/asm/mixin/Mixin.java": """package org.spongepowered.asm.mixin;
public @interface Mixin{Class<?>[] value();}""",
        "org/spongepowered/asm/mixin/Shadow.java": """package org.spongepowered.asm.mixin;public @interface Shadow{}""",
        "org/spongepowered/asm/mixin/Final.java": """package org.spongepowered.asm.mixin;public @interface Final{}""",
        "org/spongepowered/asm/mixin/injection/At.java": """package org.spongepowered.asm.mixin.injection;public @interface At{String value();}""",
        "org/spongepowered/asm/mixin/injection/Inject.java": """package org.spongepowered.asm.mixin.injection;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Inject{String[] method();At at();}""",
        "org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java": """package org.spongepowered.asm.mixin.injection.callback;public class CallbackInfo{}""",
        "EntityCacheProbe.java": """import java.util.*;import java.lang.reflect.*;
import net.minecraft.client.multiplayer.WorldClient;import net.minecraft.world.chunk.Chunk;import net.minecraft.entity.Entity;
import org.taumc.celeritas.impl.render.entity.*;import org.taumc.celeritas.mixin.core.terrain.*;
public class EntityCacheProbe {
 static class Provider implements ChunkProviderClientAccessor {
  Map<Long,Chunk> chunks=new LinkedHashMap<>();int walks;boolean fail;
  public Map<Long,Chunk> celeritas$getLoadedChunks(){walks++;if(fail){fail=false;throw new IllegalStateException("gather fault");}return chunks;}
 }
 static class Hook extends ChunkEntityMembershipMixin{}
 static void changed(WorldClient world)throws Exception {
  Hook hook=new Hook();Field f=ChunkEntityMembershipMixin.class.getDeclaredField("world");f.setAccessible(true);f.set(hook,world);
  Method m=ChunkEntityMembershipMixin.class.getDeclaredMethod("celtium$entityMembershipChanged",
   org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);m.setAccessible(true);
  m.invoke(hook,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo());
 }
 static void expect(String label,int got,int want){if(got!=want)throw new AssertionError(label+": "+got+" != "+want);}
 public static void main(String[] args)throws Exception {
  Method hook=ChunkEntityMembershipMixin.class.getDeclaredMethod("celtium$entityMembershipChanged",
   org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
  String[] targets=hook.getAnnotation(org.spongepowered.asm.mixin.injection.Inject.class).method();
  if(!new HashSet<>(Arrays.asList(targets)).equals(Set.of("func_76612_a","func_76608_a","func_76631_c","func_76623_d")))
   throw new AssertionError("membership hook target changed");
  WorldClient w=new WorldClient();Provider p=new Provider();w.provider=p;Chunk c=new Chunk();p.chunks.put(0L,c);
  Entity a=new Entity(),b=new Entity();c.entities.add(a);EntityGatherer g=new EntityGatherer();
  Entity.currentPass=0;expect("initial",g.getLoadedEntityList(w,0)[0].size(),1);
  g.getLoadedEntityList(w,0);expect("stable membership keeps chunk cache",p.walks,1);
  c.entities.add(b);changed(w);expect("same tick add",g.getLoadedEntityList(w,0)[0].size(),2);
  expect("mutation caused one new walk",p.walks,2);
  c.entities.remove(a);changed(w);expect("same tick remove",g.getLoadedEntityList(w,0)[0].size(),1);
  b.eligible=false;expect("eligibility change",g.getLoadedEntityList(w,0)[0].size(),0);
  b.eligible=true;Entity.currentPass=1;expect("actual pass context",g.getLoadedEntityList(w,1)[1].size(),1);
  expect("eligibility did not force a chunk walk",p.walks,3);
  Entity.currentPass=0;p.chunks.clear();changed(w);expect("chunk unload",g.getLoadedEntityList(w,0)[0].size(),0);
  p.chunks.put(0L,c);changed(w);expect("chunk reload",g.getLoadedEntityList(w,0)[0].size(),1);
  c.entities.add(a);changed(w);p.fail=true;
  try{g.getLoadedEntityList(w,0);throw new AssertionError("gather fault ignored");}catch(IllegalStateException expected){}
  expect("failed gather retried",g.getLoadedEntityList(w,0)[0].size(),2);
  WorldClient other=new WorldClient();other.provider=p;expect("world switch",g.getLoadedEntityList(other,0)[0].size(),2);
  WorldClient fallback=new WorldClient();fallback.loadedEntityList.add(a);
  expect("fallback",g.getLoadedEntityList(fallback,0)[0].size(),1);
  fallback.loadedEntityList.clear();fallback.loadedEntityList.add(b);fallback.loadedEntityList.add(a);
  expect("fallback same tick replacement",g.getLoadedEntityList(fallback,0)[0].size(),2);
  g.clear();expect("clear and gather",g.getLoadedEntityList(w,0)[0].size(),2);
  WorldClient server=new WorldClient();server.isRemote=false;EntityMembershipEpoch.changed(server);
  expect("server does not mutate client epoch",(int)EntityMembershipEpoch.current(server),0);
  System.out.println("entity cache: stable walk, same-tick membership, pass predicate, unload/reload, failure retry, world switch, fallback PASS");
 }
}""",
    }
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)
        for name in ["org/taumc/celeritas/impl/render/entity/EntityGatherer.java",
                     "org/taumc/celeritas/impl/render/entity/EntityMembershipEpoch.java",
                     "org/taumc/celeritas/mixin/core/terrain/ChunkEntityMembershipMixin.java"]:
            stubs[name] = (FORGE / name).read_text()
        for name, text in stubs.items():
            out = path / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(text)
        subprocess.run([shutil.which("javac"), "--release", "21", "-d", str(path / "classes"),
                        *map(str, path.rglob("*.java"))], check=True)
        subprocess.run([shutil.which("java"), "-cp", str(path / "classes"), "EntityCacheProbe"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
