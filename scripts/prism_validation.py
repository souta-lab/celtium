#!/usr/bin/env python3
"""Lock a Linux x86_64 Prism profile and launch an isolated, offline test copy.

The source instance and shared library/asset caches are read only. Authentication
data is neither read nor copied. This currently supports local patched profiles,
such as the official Cleanroom instance; it is not a general Minecraft launcher.
"""

import argparse
import configparser
import fcntl
from contextlib import nullcontext
import hashlib
import json
import platform
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import sys
import time
import urllib.request
import uuid

from headless_wayland import HeadlessWayland


#: Pack directories a caller may add to the isolated game dir. `mods`, `config`, `resourcepacks` and `saves` are
#: copied unconditionally; these are the content directories a pack keeps beside them (`resources` for custom
#: assets, `structures` for Recurrent Complex-style world content, `scripts` for CraftTweaker recipes,
#: `schematics` for WorldEdit/Schematica files). A name outside this list is refused rather than copied, so the
#: harness never carries in something it has not audited. `structures` was added when RLCraft 2.9.3's P1
#: preparation measured 338 structure files in `overrides/structures` that no leg could otherwise carry in.
SUPPLEMENTAL_PACK_DIRS = ("scripts", "schematics", "structures", "resources")


def supplemental_pack_dirs(requested):
    """Validate `--copy-pack-dir` names and return them deduplicated in the caller's order.

    Measured need (RLCraft 2.9.3): the pack ships `overrides/resources` (816 files), `overrides/structures`
    (338) and `overrides/scripts` (9) beside `overrides/config` (2,205), and only the last of those reaches a
    run dir. A boot that then reports missing content cannot say whether the pack is broken or the harness
    left the content behind, which is exactly the ambiguity this option removes.
    """
    unknown = [name for name in requested if name not in SUPPLEMENTAL_PACK_DIRS]
    if unknown:
        raise ValueError("--copy-pack-dir accepts only " + ", ".join(SUPPLEMENTAL_PACK_DIRS)
                         + "; refused: " + ", ".join(sorted(set(unknown))))
    return list(dict.fromkeys(requested))


def copy_pack_into_game_dir(source_game, game, extra_dirs, ignored_mods):
    """Copy the pack's game directories into an isolated game dir; report what the extra ones carried.

    `mods`, `config`, `resourcepacks` and `saves` are always copied. `extra_dirs` (already validated by
    :func:`supplemental_pack_dirs`) are copied too, and each one is recorded with its file count, so a run can
    say whether the content it needed was present instead of assuming the request was honoured. A directory the
    source profile does not have is recorded as `copied: false` rather than counted as content.
    """
    for name in ("mods", "config", "resourcepacks", "saves", *extra_dirs):
        source = source_game / name
        if source.is_dir():
            ignore = shutil.ignore_patterns(*ignored_mods) if name == "mods" else None
            shutil.copytree(source, game / name, ignore=ignore)
    if not extra_dirs:
        return []
    return [{"name": name, "copied": (source_game / name).is_dir(),
             "files": sum(1 for p in (game / name).rglob("*") if p.is_file()) if (game / name).is_dir() else 0}
            for name in extra_dirs]


def create_isolated_game_directory(game):
    """Claim a new isolated directory without removing any previous run or unrelated user files."""
    Path(game).mkdir(parents=True, exist_ok=False)


def digest(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def coordinate_path(name, classifier=None):
    group, artifact, version, *extra = name.split(":")
    suffix = ""
    if classifier:
        suffix = "-" + classifier
    elif extra:
        suffix = "-" + extra[0]
    return Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}{suffix}.jar"


NATIVES_CLASSIFIERS = ("natives-linux", "natives-linux-64", "natives-linux-amd64", "natives-linux-arm64")


def library_descriptor(lib):
    """{@return} the Linux jar descriptor for this library, with its classifier recorded.

    Some 1.12.2 libraries carry their OS natives as classifiers instead of an artifact - jinput-platform is the one
    that bites a fresh imported profile, because the launcher extracts it as a native and never caches the coordinate
    the profile names. Returning the descriptor the launcher itself would place means the harness resolves the file
    that exists instead of refusing the profile over a jar no launcher keeps in that shape.
    """
    downloads = lib.get("downloads", {})
    artifact = downloads.get("artifact") or {}
    if artifact:
        return artifact
    classifiers = downloads.get("classifiers") or {}
    for key in NATIVES_CLASSIFIERS:
        descriptor = classifiers.get(key)
        if descriptor:
            found = dict(descriptor)
            found["classifier"] = key
            return found
    return {}


def fetch_library(url, destination, expected_sha1=None):
    """Download one library into the launcher's cache and verify the profile's own SHA-1."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "celtium-validation"})
    with urllib.request.urlopen(request, timeout=180) as response:
        with tempfile.NamedTemporaryFile(dir=destination.parent, prefix=destination.name + ".",
                                         delete=False) as handle:
            shutil.copyfileobj(response, handle)
            temporary = Path(handle.name)
    try:
        if expected_sha1:
            actual = digest(temporary, "sha1")
            if actual != expected_sha1:
                raise ValueError(f"downloaded library does not match the profile SHA-1: {url} ({actual})")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def allowed(library):
    name = library["name"]
    if "natives-" in name and not name.endswith(":natives-linux"):
        return False
    permitted = not library.get("rules")
    for rule in library.get("rules", []):
        system = rule.get("os", {})
        matches = system.get("name", "linux") == "linux"
        matches &= bool(re.search(system.get("arch", ".*"), "amd64"))
        matches &= bool(re.search(system.get("version", ".*"), platform.release()))
        matches &= not any(rule.get("features", {}).values())
        if matches:
            permitted = rule["action"] == "allow"
    return permitted


def component_patch(instance, prism, component):
    """{@return} (json path, "local"|"meta") for one profile component, or (None, None).

    A profile Prism imported (a CurseForge Forge pack - exactly the Cleanroom Relauncher setup the external testers
    run) carries no local patches: the launcher resolves every standard component from its shared meta when it
    launches. Reading only patches/ made such a profile look like it had no Minecraft, no main class and no assets, so
    the harness refused the very configuration it is meant to test. The meta file has the same shape as a patch.
    """
    local = instance / "patches" / (component["uid"] + ".json")
    if local.is_file():
        return local, "local"
    version = component.get("version")
    if not version:
        return None, None
    meta = prism / "meta" / str(component["uid"]) / (str(version) + ".json")
    return (meta, "meta") if meta.is_file() else (None, None)


def inspect(instance, java, fetch_missing=False):
    prism = instance.parent.parent
    pack = json.loads((instance / "mmc-pack.json").read_text())
    libraries = {}
    main_jar = None
    main_class = None
    tweakers = []
    jvm_args = []
    assets = None
    patches = []
    for component in pack["components"]:
        patch, patch_source = component_patch(instance, prism, component)
        if patch is None:
            # A meta component Prism itself prunes (the 1.12.2 tree keeps an org.lwjgl3 entry whose libraries are the
            # 3.3.1 ones a 3.4.3 renderer must not load) stays empty; anything it would have contributed (main class,
            # tweakers, libraries) is absent from the classpath, which is the safe direction: the harness passes its
            # own LWJGL and natives.
            print(f"note: {component['uid']} {component.get('version')} is not resolvable; treated as empty")
            continue
        data = json.loads(patch.read_text())
        patches.append({"uid": component["uid"], "version": data.get("version", component.get("version")),
                        "sha256": digest(patch), "source": patch_source})
        main_class = data.get("mainClass", main_class)
        main_jar = data.get("mainJar", main_jar)
        assets = data.get("assetIndex", assets)
        tweakers.extend(data.get("+tweakers", []))
        jvm_args.extend(data.get("+jvmArgs", []))
        for lib in data.get("libraries", []):
            if allowed(lib):
                group, artifact, version, *classifier = lib["name"].split(":")
                libraries[(group, artifact, tuple(classifier))] = lib
    if not main_jar or not main_class or not assets:
        raise ValueError("Profile lacks a main jar, main class or asset index")

    if not java:
        config = configparser.ConfigParser(interpolation=None)
        config.read(instance / "instance.cfg")
        java = Path(config["General"]["JavaPath"])
    java = java.resolve()
    java_version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    # Provenance (audit S24): which commit, whether the tree was clean, and which flags actually reached the JVM. Without
    # these a measurement cannot be traced back to the code and configuration that produced it.
    def git(*args):
        try:
            out = subprocess.run(["git", *args], capture_output=True, text=True, cwd=Path(__file__).resolve().parent.parent)
            return out.stdout.strip() if out.returncode == 0 else None
        except OSError:
            return None

    dirty = git("status", "--porcelain")
    report = {
        "scope": "runtime-lock; no rendering, compatibility or performance claim",
        "sourceInstance": str(instance),
        "platform": platform.platform(),
        "git": {
            "head": git("rev-parse", "HEAD"),
            "subject": git("log", "-1", "--pretty=%s"),
            "dirty": bool(dirty),
            "dirtyFiles": dirty.splitlines() if dirty else [],
        },
        "effectiveConfig": {
            # The profile's arguments and the ones the runner adds are different channels, and only the union is what
            # actually reaches the JVM - recording just the first was the gap this field was added to close (S24).
            "profileJvmArgs": jvm_args,
            "runnerJvmArgs": [],
            "jvmArgs": list(jvm_args),
            "tweakers": tweakers,
            "mainClass": main_class,
            "mainJar": str(main_jar),
        },
        "java": {"executable": str(java),
                 "version": (java_version.stderr + java_version.stdout).strip(),
                 "major": java_major_version(java_version.stderr + java_version.stdout)},
        "components": patches,
        "libraries": [],
        "mods": [],
    }
    classpath = []
    for lib in list(libraries.values()) + [main_jar]:
        descriptor = library_descriptor(lib)
        # Prism stores by the coordinate, including patched version suffixes
        # such as 3.4.1-unsafe, even when downloads.artifact.path differs.
        path = prism / "libraries" / coordinate_path(lib["name"], descriptor.get("classifier"))
        if not path.is_file() and descriptor.get("path"):
            path = prism / "libraries" / descriptor["path"]
        if not path.is_file():
            # A pack may ship its own copies beside the instance (Cleanroom 0.7 does: it drops vanilla libraries by
            # overriding them with "999999.0-empty" jars that only exist under the instance's libraries/).
            local = instance / "libraries" / path.name
            if local.is_file():
                path = local
        if not path.is_file() and fetch_missing and descriptor.get("url"):
            # A profile the launcher imported but never launched has none of its standard 1.12.2/Forge jars: the
            # launcher downloads them on first launch and this harness runs before that. Fetching them from the
            # profile's own recorded URL is what the launcher would do, and the SHA-1 is checked against that record.
            print(f"fetching missing library {lib['name']} -> {path}")
            fetch_library(descriptor["url"], path, descriptor.get("sha1"))
        if not path.is_file():
            hint = "" if descriptor.get("url") else (
                " (the profile records no download for this artifact; the launcher provides it as a native)")
            raise FileNotFoundError(f"Missing cached library: {path}{hint}"
                                    + ("" if fetch_missing else "; --fetch-missing-libraries downloads it"))
        if descriptor.get("sha1") and digest(path, "sha1") != descriptor["sha1"]:
            raise ValueError(f"Library does not match the profile SHA-1: {path}")
        report["libraries"].append({"coordinate": lib["name"], "sha256": digest(path), "size": path.stat().st_size})
        classpath.append(str(path))
    for path in sorted((instance / "minecraft/mods").glob("*.jar")):
        entry = {"file": path.name, "sha256": digest(path), "size": path.stat().st_size}
        # The fixture is what produces the acceptance verdict, so it is identified rather than left among the mods (S24).
        if "validation" in path.name:
            entry["role"] = "validation-fixture"
            report["fixture"] = {"file": path.name, "sha256": entry["sha256"]}
        report["mods"].append(entry)
    # The fixture the runner passes in is the one that produces the verdict, so it is recorded by hash even though it is
    # copied into the isolated game directory later rather than being an instance mod (S24).
    return report, java, classpath, main_class, tweakers, jvm_args, prism / "assets", assets["id"]


def shared_game_slot(root):
    """Git worktrees share one GPU slot; an unpacked standalone source tree retains its local slot."""
    root = Path(root).resolve()
    try:
        done = subprocess.run(["git", "rev-parse", "--git-common-dir"], cwd=root,
                              capture_output=True, text=True, timeout=5)
    except (OSError, subprocess.TimeoutExpired) as error:
        if (root / ".git").exists():
            raise RuntimeError("cannot locate the shared repository game slot") from error
        return root / ".celtium-work" / "game-slot"
    if done.returncode != 0 or not done.stdout.strip():
        if (root / ".git").exists():
            raise RuntimeError("git did not identify the shared repository game slot")
        return root / ".celtium-work" / "game-slot"
    common = Path(done.stdout.strip())
    if not common.is_absolute():
        common = root / common
    return common.resolve().parent / ".celtium-work" / "game-slot"


def claim_pid_file(lock):
    """Publish a complete PID atomically; another runner must never observe an empty live owner's file."""
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="ascii", dir=lock.parent,
                                         prefix=".owner-", delete=False) as pending:
            temporary = Path(pending.name)
            pending.write(str(os.getpid()))
        # link is an exclusive creation, like O_EXCL, but the inode already contains the complete PID.
        os.link(temporary, lock)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)



def game_slot_owner(lock):
    try:
        holder = int(lock.read_text(encoding="ascii").strip() or "0")
    except (OSError, ValueError):
        return 0, False
    if holder > 0:
        try:
            os.kill(holder, 0)
            return holder, True
        except ProcessLookupError:
            pass
        except PermissionError:
            return holder, True
    return holder, False


def try_claim_game_slot(lock):
    """Serialize stale inspection/replacement as well as initial publication."""
    # Keep this guard inode: unlinking it would let old and new openers hold distinct flock objects.
    with (lock.parent / ".owner.guard").open("a") as guard:
        fcntl.flock(guard, fcntl.LOCK_EX)
        try:
            claim_pid_file(lock)
            return True, os.getpid()
        except FileExistsError:
            holder, alive = game_slot_owner(lock)
            if alive:
                return False, holder
            print(f"Reclaiming the game slot from pid {holder}, which is gone")
            lock.unlink(missing_ok=True)
            claim_pid_file(lock)
            return True, os.getpid()


def release_game_slot(lock):
    """A runner can release only its own owner record."""
    with (lock.parent / ".owner.guard").open("a") as guard:
        fcntl.flock(guard, fcntl.LOCK_EX)
        holder, _ = game_slot_owner(lock)
        if holder == os.getpid():
            lock.unlink(missing_ok=True)


RELAUNCHER_MOD_GLOB = "*cleanroom-relauncher*.jar"


def instance_heap_mib(instance, explicit=None):
    """{@return} the heap the launched JVM is given, in MiB.

    An explicit value wins. Otherwise the instance's own MaxMemAlloc is used, because a pack that needs a large heap
    records it there. The old fixed 2G is only the last resort, and it is a measured defect rather than a default: a
    Cleanroom Relauncher copies the launcher JVM's -Xmx into the relaunched game, so a harness that always asked for 2G
    handed a pack that asks for 10G a 2G heap. That fails as an OutOfMemoryError while the world loads, which reads
    like a renderer or pack fault and is neither.
    """
    if explicit is not None:
        if explicit < 512:
            raise ValueError("--memory must be at least 512 MiB")
        return explicit
    config = configparser.ConfigParser(interpolation=None)
    config.read(instance / "instance.cfg")
    try:
        value = int(config["General"]["MaxMemAlloc"])
    except (KeyError, ValueError):
        return 2048
    return value if value >= 512 else 2048


def relauncher_version(mod_name):
    """{@return} the version in a relauncher jar's name, or None."""
    match = re.search(r"(\d+(?:\.\d+)+)", mod_name or "")
    return match.group(1) if match else None


def java_major_version(version_text):
    """{@return} the Java major version from `java -version` output, or None.

    Forge 1.12.2's launchwrapper casts the system classloader to a URLClassLoader, which Java 9 removed; the launcher
    therefore runs a 1.12.2 Forge profile on Java 8 and lets the Cleanroom Relauncher provide a modern JVM for the
    relaunched game. The harness has to know which of the two it is starting, because some of its own flags
    (`--enable-native-access`) do not exist before Java 17.
    """
    match = re.search(r'version "(\d+)(?:\.(\d+))?', version_text or "")
    if not match:
        return None
    major = int(match.group(1))
    if major == 1 and match.group(2):
        return int(match.group(2))
    return major


def java_supports_native_access(version_or_major):
    """{@return} whether `--enable-native-access` can be passed to this JVM (Java 17 and newer can)."""
    major = version_or_major if isinstance(version_or_major, int) else java_major_version(version_or_major)
    return major is not None and major >= 17


def cleanroom_generation(version_text):
    """{@return} (major, minor) for a Cleanroom version string, or None.

    0.7 is the SDL3 / LWJGL 3.4.3 generation, which is the one Celtium is built for; 0.6 and older cannot host a
    gl-less Vulkan window at all, so a run pinned to them is worth naming before it starts rather than after it fails.
    """
    match = re.search(r"(\d+)\.(\d+)", version_text or "")
    return (int(match.group(1)), int(match.group(2))) if match else None


def relauncher_summary(instance):
    """{@return} how this instance reaches Cleanroom, or None when it is not a relauncher instance.

    The Cleanroom Relauncher is a Forge coremod: the profile starts Forge, that plugin prepares Cleanroom and starts a
    second JVM on Cleanroom's classpath, and it forwards the launcher JVM's input arguments (-D flags and -Xmx) to that
    second JVM. That forwarding is why the acceptance flags reach the game on a relauncher instance at all, and why the
    heap has to be right on the first launch: the relaunched JVM never reads the instance's memory settings itself.
    Recording the version here stops a run of the Forge profile from being reported as a run of Cleanroom.
    """
    mods = sorted((instance / "minecraft" / "mods").glob(RELAUNCHER_MOD_GLOB))
    if not mods:
        return None
    config = {}
    path = instance / "minecraft" / "config" / "relauncher.json"
    if path.is_file():
        try:
            config = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            config = {}
    return {
        "mod": mods[0].name,
        "version": relauncher_version(mods[0].name),
        "configPath": "config/relauncher.json",
        "cleanroomVersion": config.get("selectedVersion"),
        "latestKnownVersion": config.get("latestVersion"),
        "autoSetup": config.get("autoSetup"),
        "enableRelauncher": config.get("enableRelauncher"),
        "targetJavaVersion": config.get("targetJavaVersion"),
        "launcherJvmArgs": config.get("args"),
        "forwardsLauncherJvmArgs": True,
    }


def inject_relauncher_arguments(game, runner_arguments):
    """{@return} the -D flags written into the isolated copy's relauncher config.

    The relauncher starts the game in a second JVM and builds that command itself: -Xmx is forwarded from the launcher
    JVM, and a 1.3.0 run here reached the game without the harness's -D flags. That is not good enough for an
    acceptance run, because a diagnostic flag that silently does not arrive makes the run look clean and unexplained at
    the same time. Its documented lever is config/relauncher.json's "args", which it appends to the child's command, so
    the flags go there - in the copy only, never in the source instance.
    """
    added = [str(arg) for arg in runner_arguments or [] if str(arg).startswith("-D")]
    if not added:
        return []
    path = game / "config" / "relauncher.json"
    config = {}
    if path.is_file():
        try:
            config = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            config = {}
    config["args"] = ((config.get("args") or "").strip() + " " + " ".join(added)).strip()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    return added


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("instance", type=Path, help="Prism instance directory (parent of minecraft/)")
    parser.add_argument("--output", type=Path, required=True, help="Runtime lock JSON")
    parser.add_argument("--java", type=Path)
    parser.add_argument("--game-dir", type=Path, help="Create a fresh isolated game directory")
    parser.add_argument("--mod", type=Path, action="append", default=[])
    parser.add_argument("--exclude-instance-mod", action="append", default=[],
                        help="Glob of instance mods not to copy into the test game dir (e.g. 'StorageDrawers-*'); the instance itself is untouched")
    parser.add_argument("--seed-options", type=Path, action="append", default=[],
                        help="Copy this file into the isolated game directory as options.txt (repeatable; last wins)."
                             " It is how a launch condition the game reads from options - fullscreen, GUI scale,"
                             " render distance - is reproduced in a fresh directory, which the game otherwise"
                             " starts with defaults.")
    parser.add_argument("--seed-save", type=Path, action="append", default=[],
                        help="Copy this world save directory into the isolated game dir as saves/<name>. A fixture "
                             "scene that loads a named save needs it there, and the isolated directory starts empty so "
                             "the source instance never has to be modified")
    parser.add_argument("--copy-pack-dir", action="append", default=[],
                        help="Also copy one of the pack's content directories (" + ", ".join(SUPPLEMENTAL_PACK_DIRS)
                             + ") into the test game dir; the source profile is never changed")
    parser.add_argument("--library", type=Path, action="append", default=[], help="Extra test-only classpath jar; the source profile stays unchanged")
    parser.add_argument("--force-x11", action="store_true",
                        help="Hide the Wayland socket from the game so GLFW uses the Xwayland display instead; the "
                             "Wayland keyboard path compiles the compositor's keymap with libxkbcommon and a "
                             "rolling distribution can break that path for the client")
    parser.add_argument("--jvm-arg", action="append", default=[])
    parser.add_argument("--fetch-missing-libraries", action="store_true",
                        help="Download libraries the launcher has not cached yet, from the profile's own meta URLs, "
                             "into <prism>/libraries and verify their SHA-1. A profile Prism imported but never "
                             "launched has none of the standard 1.12.2/Forge jars, and refusing it was the harness's "
                             "relauncher-instance gap")
    parser.add_argument("--memory", type=int, default=None, metavar="MIB",
                        help="Heap for the launched JVM (-Xmx). Defaults to the instance's MaxMemAlloc, else 2048. "
                             "A Cleanroom Relauncher instance copies this to the relaunched game, so a pack that asks"
                             " for 10G must say so here - the old fixed 2G became an OutOfMemoryError during load")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--validate", action="store_true", help="Enable and require the separately installed validation fixture")
    parser.add_argument("--duration", type=int, default=60, help="Maximum runtime in seconds; a timeout is not success")
    parser.add_argument("--slot-wait", type=int, default=900,
                        help="Seconds to wait for the single-game slot; the slot is a lock file in .celtium-work/game-slot")
    parser.add_argument("--headless", action="store_true", help="Use an isolated GPU-rendered Sway/Xwayland display")
    parser.add_argument("--render-node", type=Path, help="Explicit DRM render node for the headless compositor")
    parser.add_argument("--window-size", default="1280x720",
                        help="Window size the game is asked for (WxH, default 1280x720 = the headless output)")
    parser.add_argument("--require-gpu", help="Require this substring in the fixture's actual GL renderer")
    args = parser.parse_args()
    if sys.platform != "linux" or platform.machine() != "x86_64":
        parser.error("Only Linux x86_64 has been implemented for this validation launcher")
    if args.duration <= 0:
        parser.error("Duration must be positive")
    if (args.mod or args.library or args.run) and not args.game_dir:
        parser.error("--mod, --library and --run require an isolated --game-dir")
    try:
        args.copy_pack_dir = supplemental_pack_dirs(args.copy_pack_dir)
    except ValueError as error:
        parser.error(str(error))
    if args.copy_pack_dir and not args.game_dir:
        parser.error("--copy-pack-dir requires an isolated --game-dir")
    if args.validate and not args.run:
        parser.error("--validate requires --run")
    if args.headless and (not args.run or not args.render_node):
        parser.error("--headless requires --run and an explicit --render-node")
    if args.require_gpu and not args.validate:
        parser.error("--require-gpu requires --validate")
    instance = args.instance.resolve()
    report, java, cp, main_class, tweakers, jvm_args, assets, asset_index = inspect(
        instance, args.java, fetch_missing=args.fetch_missing_libraries)
    try:
        memory_mib = instance_heap_mib(instance, args.memory)
    except ValueError as error:
        parser.error(str(error))
    report["heapMiB"] = memory_mib
    # The launcher profile is not always the runtime: a Cleanroom Relauncher instance starts Forge, that coremod
    # prepares Cleanroom, and the game the operator sees is a second JVM. What it forwards (the launcher's -D flags and
    # -Xmx) is the contract this record exists for, so a run can never be cited as a Cleanroom run without it.
    relauncher = relauncher_summary(instance)
    if relauncher is not None:
        report["relauncher"] = relauncher
        print("Relauncher instance: " + relauncher["mod"] + " prepares Cleanroom "
              + str(relauncher.get("cleanroomVersion")) + " and forwards the launcher's -D flags and -Xmx " + str(memory_mib)
              + "m to the relaunched game")
        if relauncher.get("enableRelauncher") is False:
            parser.error("the instance carries the Cleanroom Relauncher but config/relauncher.json disables it; "
                         "Celtium needs the Cleanroom runtime, not the Forge profile")
        generation = cleanroom_generation(relauncher.get("cleanroomVersion"))
        if generation is not None and generation < (0, 7):
            print("warning: the relauncher is pinned to Cleanroom " + str(relauncher.get("cleanroomVersion"))
                  + "; Celtium requires the 0.7 line (SDL3, LWJGL 3.4.3) and will refuse the older generation")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Locked {len(report['libraries'])} library jars and {len(report['mods'])} mods")
    if not args.game_dir:
        return 0
    game = args.game_dir.resolve()
    source_game = instance / "minecraft"
    if game == source_game or game.is_relative_to(source_game) or source_game.is_relative_to(game):
        parser.error("The test directory must be separate from the source instance")
    # A native crash or an interrupted launch may never produce the fixture report. Its logs, world and crash
    # evidence are still an experiment: only a new directory belongs to this launch.
    try:
        create_isolated_game_directory(game)
    except FileExistsError:
        parser.error(f"The test directory already exists; preserve it and choose a fresh --game-dir: {game}")
    # A game condition that lives in options.txt has to be seeded before the JVM reads it, and the isolated directory
    # starts empty on purpose, so the caller supplies the file it wants the game to see.
    if args.seed_options:
        seeded = args.seed_options[-1]
        if not seeded.is_file():
            parser.error(f"--seed-options file does not exist: {seeded}")
        shutil.copyfile(seeded, game / "options.txt")
        print(f"Seeded {game / 'options.txt'} from {seeded}")
    # A jar the caller supplies with --mod is not copied from the instance, even when the instance has one with the
    # same name. It used to be a hard error ("Test mod would overwrite a copied mod"), which was right when the instance
    # was never expected to hold one - but the user keeps a build in their instance mods to launch the game themselves,
    # and that made every test run fail at startup with the jar in place. The caller's jar wins for the names it names;
    # a genuine collision between two supplied files is still an error below.
    supplied = {mod.name for mod in args.mod}
    # The instance's own copy of this project's jars is never carried into a test game dir, whatever the caller names
    # its frozen copies. Excluding only the exact supplied names was not enough: a worker who freezes the build under a
    # different file name (to keep two legs on one hash) then gets the instance's jar copied alongside it, and the game
    # dies on a duplicate mod id - which cost fog-path a leg before this line existed.
    ignored_mods = ("celtium-forge-*.jar", "celtium-validation-client-*.jar") + tuple(supplied) + tuple(args.exclude_instance_mod)
    supplemental = copy_pack_into_game_dir(source_game, game, args.copy_pack_dir, ignored_mods)
    if supplemental:
        report["supplementalPackDirs"] = supplemental
    # After the profile is copied: the copy already carries the instance's own saves/, so seeding first made the copy
    # fail on an existing directory (measured, first in-world Supersymmetry attempt).
    for save in args.seed_save:
        if not save.is_dir():
            parser.error(f"--seed-save is not a directory: {save}")
        target = game / "saves" / save.resolve().name
        if target.exists():
            parser.error(f"the isolated game dir already has a save named {target.name}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(save, target)
        print(f"Seeded save {target.name} from {save}")
        report.setdefault("seededSaves", []).append({"name": target.name, "source": str(save.resolve())})
    # A relauncher instance runs the game in a second JVM whose command the relauncher builds. Writing the diagnostic
    # flags into the copy's config/relauncher.json is the path the relauncher documents, and the only one that does not
    # depend on the version's argument forwarding: a 1.3.0 run reached the game without the harness's -D flags while
    # keeping glless (which is on by default on Linux), so a run could otherwise be judged without its instruments.
    # This has to happen after the profile is copied into the isolated directory, or the copy fails on config/ existing.
    if relauncher is not None:
        injected = inject_relauncher_arguments(game, args.jvm_arg)
        if injected:
            relauncher["injectedJvmArgs"] = injected
            print("Relauncher arguments: wrote " + str(len(injected)) + " -D flag(s) into the copy's config/relauncher.json")
    (game / "mods").mkdir(exist_ok=True)
    for mod in args.mod:
        target = game / "mods" / mod.name
        if target.exists():
            raise FileExistsError(f"Test mod would overwrite a copied mod: {target}")
        shutil.copy2(mod, target)
    report["extraTestLibraries"] = []
    if args.library:
        library_dir = game / "test-libraries"
        library_dir.mkdir()
        for library in args.library:
            target = library_dir / library.name
            if library.suffix != ".jar" or target.exists():
                raise ValueError(f"Extra test library must be a distinct jar: {library}")
            shutil.copy2(library, target)
            cp.append(str(target))
            report["extraTestLibraries"].append({"file": target.name, "sha256": digest(target)})
    report["testMods"] = [{"file": p.name, "sha256": digest(p)} for p in sorted((game / "mods").glob("*.jar"))]
    # Reference the capabilities the renderer recorded rather than duplicating them (audit S24): the driver and limits
    # belong to the run, and that file is where they are measured rather than restated.
    capabilities = game / "logs" / "celtium-capabilities.json"
    if capabilities.is_file():
        try:
            caps = json.loads(capabilities.read_text())
            report["driver"] = {
                "source": str(capabilities.relative_to(game)),
                "deviceName": caps.get("deviceName") or caps.get("device", {}).get("deviceName"),
                "driverVersion": caps.get("driverVersion") or caps.get("device", {}).get("driverVersion"),
                "apiVersion": caps.get("apiVersion") or caps.get("device", {}).get("apiVersion"),
            }
        except (OSError, ValueError):
            report["driver"] = {"source": str(capabilities.relative_to(game)), "error": "unreadable"}    # The runner's own arguments are a separate channel from the profile's, and only the union reaches the JVM.
    report["effectiveConfig"]["runnerJvmArgs"] = [str(a) for a in (args.jvm_arg or [])]
    report["effectiveConfig"]["jvmArgs"] = (list(report["effectiveConfig"].get("profileJvmArgs", []))
            + report["effectiveConfig"]["runnerJvmArgs"])
    # The fixture that produces the verdict is passed in by the runner, not present as an instance mod.
    for candidate in (args.mod or []):
        candidate = Path(candidate)
        if candidate.is_file() and "validation" in candidate.name:
            report["fixture"] = {"file": candidate.name, "sha256": digest(candidate), "source": str(candidate)}

    report["isolatedGameDirectory"] = str(game)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not args.run:
        return 0
    # ---- one game instance at a time, arbitrated rather than hoped for -------------------------------
    # Every worker was told to check for a running client before launching, and four of them passed that check in the
    # same second: the check-then-launch window is the problem, not anyone's discipline. All Git worktrees use
    # the same slot. Its PID is written before atomic publication; a stale owner is reclaimed, and a live one is
    # waited for. Two runs on one GPU are not evidence, and the runs they cost are the expensive part.
    slot = shared_game_slot(Path(__file__).resolve().parent.parent)
    slot.mkdir(parents=True, exist_ok=True)
    lock = slot / "owner.pid"
    deadline = time.monotonic() + args.slot_wait
    while True:
        claimed, holder = try_claim_game_slot(lock)
        if claimed:
            break
        if time.monotonic() >= deadline:
            parser.error(f"the game slot is held by pid {holder} and --slot-wait ({args.slot_wait}s) expired; "
                         "one instance at a time is a measurement requirement, not a courtesy")
        print(f"Waiting for the game slot (held by pid {holder}) ...")
        time.sleep(5)
    try:
        return run_locked(args, game, cp, main_class, tweakers, jvm_args, java, assets, asset_index, parser, memory_mib,
                          report["java"].get("major"))
    finally:
        release_game_slot(lock)


def run_locked(args, game, cp, main_class, tweakers, jvm_args, java, assets, asset_index, parser, memory_mib=2048,
               java_major=None):
    player = "CeltiumValidation"
    player_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode("utf-8")).digest(), version=3).hex
    validation_args = ["-Dceltium.validation=true"] if args.validate else []
    # The window the game is asked for. It used to be hard-coded 1280x720, which is the headless compositor's output
    # size and therefore the only size any placement measurement could be taken at - while the operator's own window
    # is 1920x1080, and the presented frame's placement at that size is exactly what the "the menu is shifted" report
    # is about. `--window-size WxH` lets a leg start at the size in question.
    window_size = args.window_size.lower().split("x")
    if len(window_size) != 2:
        parser.error("--window-size must be WxH, e.g. 1920x1080")
    try:
        window_width = int(window_size[0])
        window_height = int(window_size[1])
    except ValueError:
        parser.error("--window-size must be WxH with integer values, e.g. 1920x1080")
    java_supports_native = java_supports_native_access(java_major)
    command = [str(java), "-Xms512m", f"-Xmx{memory_mib}m"]
    if java_supports_native:
        command.append("--enable-native-access=ALL-UNNAMED")
    command += [*jvm_args, *args.jvm_arg, *validation_args, "-cp", ":".join(cp), main_class,
               "--username", player, "--version", "1.12.2", "--gameDir", str(game),
               "--assetsDir", str(assets), "--assetIndex", asset_index,
               "--uuid", player_uuid, "--accessToken", "0", "--userType", "legacy",
               "--versionType", "release", "--width", str(window_width), "--height", str(window_height)]
    for tweaker in tweakers:
        command.extend(["--tweakClass", tweaker])
    (game / "validation-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    started = time.monotonic()
    # The compositor's output takes the requested window size, so `--window-size` is what the game is tiled to rather
    # than only what it asks GLFW for. Without this the output stayed 1280x720 and every smaller request came back
    # resized to it.
    display = (HeadlessWayland(game / "headless", args.render_node, (window_width, window_height))
               if args.headless else nullcontext())
    with display as headless, (game / "validation-console.log").open("wb") as log:
        environment = headless.environment if headless else os.environ.copy()
        if args.force_x11:
            environment.pop("WAYLAND_DISPLAY", None)
            environment.pop("WAYLAND_SOCKET", None)
        # Keep the test client's OpenAL audio off the desktop speakers. Not only for --headless: a headless-compositor
        # run still plays its music through the system mixer, which the user hears as "music only, and it is loud"
        # from a run they cannot even see. A run that genuinely wants audio back sets CELTIUM_ALLOW_AUDIO=1.
        if not os.environ.get("CELTIUM_ALLOW_AUDIO"):
            environment["ALSOFT_DRIVERS"] = "null"
        # Every caller used to export VK_LAYER_PATH by hand, and the ones that forgot produced a device-creation
        # failure whose downstream shape is a World.getSkyColorBody NPE (EntityRenderer.func_78466_h) - read for hours
        # as a renderer crash. sky-draws measured that chain on 2026-10-11 and found the same shape in four older runs.
        # The harness now supplies the repo's extracted layers when they exist, so a leg cannot fail for a missing
        # export; an explicit export in the environment still wins.
        repo_root = Path(__file__).resolve().parent.parent
        layer_dir = repo_root / ".celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
        if layer_dir.is_dir():
            environment.setdefault("VK_LAYER_PATH", str(layer_dir))
            lib_dir = repo_root / ".celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"
            if lib_dir.is_dir():
                existing = environment.get("LD_LIBRARY_PATH", "")
                if str(lib_dir) not in existing.split(":"):
                    environment["LD_LIBRARY_PATH"] = f"{lib_dir}:{existing}" if existing else str(lib_dir)
        process = subprocess.Popen(command, cwd=game, env=environment, stdout=log, stderr=subprocess.STDOUT)
        ended_by = "process-exit"
        try:
            try:
                code = process.wait(timeout=args.duration)
            except subprocess.TimeoutExpired:
                ended_by = "time-limit"
                process.terminate()
                try:
                    code = process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    code = process.wait()
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    outcome = {"exitCode": code, "endedBy": ended_by, "elapsedSeconds": time.monotonic() - started}
    if args.headless:
        outcome["headlessSession"] = str(game / "headless/session.json")
    if args.validate:
        result = game / "logs/celtium-validation.json"
        fixture = json.loads(result.read_text()) if result.exists() else {}
        outcome["fixtureStatus"] = fixture.get("status", "MISSING_REPORT")
        screenshot = game / "screenshots/celtium-validation.png"
        # A PNG signature is not evidence of a picture. Under the GL-less stub the game-level screenshot is black by
        # construction - there is no GL default framebuffer to read - yet it passes a signature-and-size check, so the
        # field read as evidence while proving nothing (measured: 1 distinct colour, 0 bands, reported as saved). The
        # fixture now says which file its verdict is about (`pictureEvidenceSource` and `pictureFile_<stage>`), so the
        # harness reports that file and calls the GL screenshot what it is in this configuration.
        outcome["screenshotSaved"] = screenshot.is_file() and screenshot.stat().st_size > 24 and screenshot.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
        evidence_source = fixture.get("pictureEvidenceSource") if isinstance(fixture, dict) else None
        outcome["pictureEvidenceSource"] = evidence_source
        if evidence_source:
            # Which stage's picture the verdict is about. A GUI run dumps both a world and a gui frame, and taking
            # whichever key the JSON happens to list first means a GUI verdict can be checked against the world image.
            stage = "gui" if fixture.get("sceneGuiStage") else "world"
            stage_file = fixture.get("pictureFile_" + stage) if isinstance(fixture, dict) else None
            if not (isinstance(stage_file, str) and stage_file):
                stage_file = next((v for k, v in (fixture or {}).items()
                                   if k.startswith("pictureFile_") and isinstance(v, str) and v), None)
                stage = "other" if stage_file else stage
            outcome["pictureStage"] = stage
            if stage_file:
                evidence = game / stage_file
                outcome["pictureFile"] = stage_file
                is_png = (evidence.is_file() and evidence.stat().st_size > 24
                          and evidence.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n")
                outcome["picturePresent"] = is_png
                if not is_png:
                    outcome["pictureEvidenceProblem"] = (
                        "picture source " + str(evidence_source) + " names " + str(stage_file)
                        + ", which is missing or not a PNG")
            outcome["screenshotIsEvidence"] = evidence_source == "gl-screenshot"
            if evidence_source != "gl-screenshot":
                # Do not let a black GL screenshot stand in for the picture, and do not fail a run for its absence:
                # the presented dump is the evidence in this configuration - but the dump must be a real PNG, which
                # is what the check above is for. The previous form set this True without reading the file, which is
                # how a run could call itself evidenced while naming a file that did not exist.
                outcome["screenshotSaved"] = bool(outcome.get("picturePresent"))
        if not outcome["screenshotSaved"] and code == 0:
            code = 1
        # Which device actually rendered. The GL renderer string is not an answer in the GL-less configuration - it is
        # the stub's own name - so the authoritative line is the one the renderer prints when it has chosen its physical
        # device. The GL string stays as the bridge's preflight and as the fallback for a run that never reached device
        # selection, and the source of the verdict is recorded rather than implied.
        console = game / "validation-console.log"
        native_name = native_uuid = None
        native_matched = None
        try:
            text = console.read_text(errors="replace") if console.is_file() else ""
            match = re.search(r'CELTIUM_NATIVE_DEVICE name="([^"]*)" uuid=([0-9a-fA-F]*) matchedOpenGl=(\w+)', text)
            if match:
                native_name, native_uuid, native_matched = match.group(1), match.group(2), match.group(3) == "true"
        except OSError:
            pass
        outcome["nativeDeviceName"] = native_name
        outcome["nativeDeviceUuid"] = native_uuid
        outcome["nativeDeviceMatchedOpenGl"] = native_matched
        outcome["glRendererPreflight"] = fixture.get("glRenderer")
        if args.require_gpu:
            if native_name is not None:
                outcome["gpuRequirementSource"] = "nativeDeviceName"
                outcome["gpuRequirementMet"] = args.require_gpu.lower() in native_name.lower()
            else:
                # No device line: the run never selected a physical device, so the GL renderer is all there is to go on.
                outcome["gpuRequirementSource"] = "glRendererPreflight"
                outcome["gpuRequirementMet"] = args.require_gpu.lower() in fixture.get("glRenderer", "").lower()
            if not outcome["gpuRequirementMet"] and code == 0:
                code = 1
        # Exit zero is reserved for an accepted fixture. Uncalibrated means the
        # picture gate did not judge the scene, not that rendering is defective:
        # report the distinct inconclusive code 2, preserving any prior failure.
        outcome["fixtureUncalibrated"] = outcome["fixtureStatus"] == "UNCALIBRATED_PRESENTED_IMAGE"
        if outcome["fixtureUncalibrated"]:
            print("note: the picture gate did not judge this scene (no reference at this pose/variant) - "
                  + str(outcome["fixtureStatus"]))
        if outcome["fixtureStatus"] not in ("PASSED", "UNCALIBRATED_PRESENTED_IMAGE") and code == 0:
            code = 1
        # Validation messages are a failure, not a detail. Leaving this out is what let a run report PASSED with
        # thirty-six validation messages inside it: the fixture's counters only see the renderer's own errors, and the
        # layer reports through the log. The whole log is read after the process has exited, so a message emitted during
        # shutdown still counts.
        console = game / "logs/latest.log"
        messages = []
        # Allowed messages are recorded too, not silently dropped: the point of this counter is that a skipped line must be
        # visible as skipped. Two rounds running, a message read through a truncated preview looked like something it was
        # not, and a gate that says nothing about what it allowed invites exactly that mistake.
        allowed = []
        if console.exists():
            for line in console.read_text(errors="ignore").splitlines():
                marker = "Celtium Vulkan validation: "
                if marker not in line:
                    continue
                text = line.split(marker, 1)[1].strip()
                severity = None
                if text.startswith("severity="):
                    head, _, rest = text.partition(" ")
                    severity = head.split("=", 1)[1]
                    if rest.startswith("id="):
                        _, _, rest = rest.partition(" ")
                    text = rest
                    if severity in ("INFO", "VERBOSE"):
                        allowed.append("%s %s" % (severity, text))
                        continue
                    if severity == "WARNING" and "deprecated" in text:
                        allowed.append("WARNING(deprecated) %s" % text)
                        continue
                else:
                    # Unstructured text, which is what older logs and driver messages look like. Only clearly
                    # informational lines are skipped: everything else counts, so an error phrased in any style - the
                    # "Validation Error: [ VUID-... ]" form the audit used - cannot pass as clean.
                    if "Information" in text and "Error" not in text:
                        allowed.append("Information %s" % text)
                        continue
                messages.append(("severity=%s " % severity if severity else "") + text)
        else:
            # No log at all is not a clean run: the old check treated a missing file as no messages.
            messages.append("the game log is missing, so the validation result is unknown")
        outcome["validationAllowedCount"] = len(allowed)
        if allowed:
            outcome["validationAllowedFirst"] = allowed[:3]
        outcome["validationMessageCount"] = len(messages)
        outcome["validationClean"] = not messages
        if messages:
            outcome["validationFirstMessages"] = messages[:3]
            if code == 0:
                code = 1
    # Give real failures precedence over the inconclusive status (for example,
    # an uncalibrated picture together with a Vulkan validation error is failed).
    if outcome.get("fixtureUncalibrated") and code == 0:
        code = 2
    # The reported codes have to carry the verdict: a validation message is a failed run, not a note in the JSON.
    outcome["validationExitCode"] = code if ended_by == "process-exit" else 124
    if outcome.get("validationClean") is False:
        outcome["fixturePassed"] = outcome.get("fixtureStatus") == "PASSED"
        outcome["validationClean"] = False
        if outcome["validationExitCode"] == 0:
            outcome["validationExitCode"] = 1
    # The driver and limits the renderer recorded, referenced rather than duplicated (audit S24). This is read after the
    # run, which is when the file exists - reading it when the lock is written finds nothing, which is what the first
    # attempt did.
    capabilities = game / "logs" / "celtium-capabilities.json"
    if capabilities.is_file():
        try:
            caps = json.loads(capabilities.read_text())
            devices = caps.get("vulkanDevices") or []
            # The device this run used, not the first one the loader listed: those differ whenever more than one GPU is
            # present, which is exactly the case this reporting exists for. Matched by the UUID the renderer printed,
            # then by name; the first entry is only the last resort.
            # Selected device, read here rather than from a name that only the earlier reporting block binds: that
            # block is on a path which does not always run, and reading its local raised UnboundLocalError mid-run -
            # which aborted the harness after the game had started, so the report was cut off and the run looked like
            # a slow one.
            selected_uuid = selected_name = None
            try:
                console_text = (game / "validation-console.log").read_text(errors="replace")
            except OSError:
                console_text = ""
            picked = re.search(
                r'CELTIUM_NATIVE_DEVICE name="([^"]*)" uuid=([0-9a-fA-F]*) matchedOpenGl=(\w+)', console_text)
            if picked:
                selected_name, selected_uuid = picked.group(1), picked.group(2)
            device = {}
            for candidate in devices:
                if not isinstance(candidate, dict):
                    continue
                if selected_uuid and candidate.get("deviceUUID") == selected_uuid:
                    device = candidate
                    break
                if selected_name and candidate.get("name") == selected_name:
                    device = candidate
                    break
            if not device and devices:
                device = devices[0]
                flat_note = "first-listed"
            else:
                flat_note = "selected"
            outcome.setdefault("driverSelection", flat_note)
            flat = {"lwjglVersion": caps.get("lwjglVersion"),
                    "vulkanLoaderVersion": caps.get("vulkanLoaderVersion"),
                    "vulkanDeviceCount": len(devices)}
            for key in ("name", "deviceName", "driverName", "driverInfo", "driverVersion", "apiVersion", "deviceType",
                        "deviceId", "vendorId", "deviceUUID"):
                if isinstance(device, dict) and device.get(key) is not None:
                    flat[key] = device[key]
            outcome["driver"] = {"source": str(capabilities.relative_to(game)), **flat}
        except (OSError, ValueError) as error:
            outcome["driver"] = {"source": str(capabilities.relative_to(game)), "error": str(error)}
    (game / "validation-process.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(outcome))
    return code if ended_by == "process-exit" else 124


if __name__ == "__main__":
    sys.exit(main())
