#!/usr/bin/env python3
"""Check the actual distributable, without loading Minecraft or native libraries."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile


def read_manifest(data):
    lines = data.decode("utf-8").replace("\r\n", "\n").split("\n")
    attributes = {}
    key = None
    for line in lines:
        if not line:
            break
        if line.startswith(" ") and key is not None:
            attributes[key] += line[1:]
        else:
            key, value = line.split(": ", 1)
            attributes[key] = value
    return attributes


def rebrand_errors(jar):
    """The mod ID stays `celeritas` for compatibility; every user-visible name must be Celtium.

    Translation values are checked, not keys: keys such as `celeritas.options...` are API for resource packs
    and existing overrides. Upstream attribution stays in the credits, licence notices and documentation.
    """
    errors = []
    checked = 0
    for name in jar.namelist():
        if not name.endswith(".lang") or "/lang/" not in name:
            continue
        checked += 1
        for number, line in enumerate(jar.read(name).decode("utf-8").splitlines(), start=1):
            if line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            if "Celeritas" in value:
                errors.append(f"{name}:{number} still shows 'Celeritas' to the user: {key}")
    if not checked:
        errors.append("No language file was found in the distributable")
    return {"errors": errors, "languageFiles": checked}


def license_errors(jar, names, repository):
    """Check the licence content the jar ships against the components it actually bundles.

    A `verified: true` from this script is quoted as evidence about the distributable, so the licence half of
    that claim has to be measured too. Before this, the script required `META-INF/LICENSE` to exist and never
    read it, which is how a notice for the wrong component could pass.
    """
    errors = []
    notes = {}

    # (1) The project's own licence, and that it is the repository's own text.
    for entry in ("COPYING", "COPYING.LESSER"):
        if entry not in names:
            errors.append(f"Missing entry: {entry} (the project's own licence text)")

    # (2) META-INF/LICENSE must be the JOML notice, because 113 relocated JOML classes are bundled under
    # org/embeddedt/embeddium/impl/shadow/joml/. The relocation is why `grep org/joml` finds nothing: a check
    # that looked for the unrelocated package would have reported the jar clean and the licence wrong.
    joml_classes = [n for n in names
                    if n.startswith("org/embeddedt/embeddium/impl/shadow/joml/") and not n.endswith("/")]
    notes["relocatedJomlClasses"] = len(joml_classes)
    if joml_classes:
        if "META-INF/LICENSE" not in names:
            errors.append("JOML is bundled (relocated) but META-INF/LICENSE is absent")
        else:
            text = jar.read("META-INF/LICENSE").decode("utf-8", "replace")
            if "JOML" not in text or "MIT License" not in text:
                errors.append("META-INF/LICENSE is not the JOML MIT notice although JOML is bundled")
        pom = "META-INF/maven/org.joml/joml/pom.properties"
        if pom in names:
            notes["jomlVersion"] = jar.read(pom).decode("utf-8", "replace").strip().replace("\n", " ")
        else:
            errors.append("JOML classes are bundled but no META-INF/maven/org.joml/joml/pom.properties records the version")

    # (3) Every bundled third-party top-level package must have a named licence entry. The census is recorded
    # either way, so a new dependency shows up as an uncovered package instead of as a silent addition.
    coverage = {
        "grondag/bitraster/": "licenses/bitraster-LICENSE",
        "org/embeddedt/embeddium/impl/shadow/joml/": "META-INF/LICENSE",
    }
    census = {}
    for prefix, notice in coverage.items():
        count = sum(1 for n in names if n.startswith(prefix) and not n.endswith("/"))
        census[prefix] = {"classes": count, "notice": notice}
        if count and notice not in names:
            errors.append(f"{prefix} is bundled ({count} classes) but its notice {notice} is absent")
    notes["licenseCensus"] = census

    return errors, notes


def verify(path):
    repository = Path(__file__).resolve().parent.parent
    errors = []
    class_versions = Counter()
    with zipfile.ZipFile(path) as jar:
        entries = jar.namelist()
        names = set(entries)
        duplicates = [name for name, count in Counter(entries).items() if count > 1]
        if duplicates:
            errors.append(f"Duplicate entries: {duplicates}")
        corrupt = jar.testzip()
        if corrupt:
            errors.append(f"Corrupt entry: {corrupt}")

        required = {
            "META-INF/MANIFEST.MF", "META-INF/LICENSE", "mcmod.info", "icon.png",
            "META-INF/celeritas_at.cfg", "mixins.celeritas.json",
            "COPYING", "COPYING.LESSER", "README.md", "docs/upstream-README.md",
            "licenses/bitraster-LICENSE",
            "org/taumc/celeritas/CeleritasVintage.class",
            "org/taumc/celeritas/core/CeleritasLoadingPlugin.class",
            "org/soutalab/celtium/forge/CeltiumCommand.class",
            "org/embeddedt/embeddium/impl/shadow/joml/Matrix4f.class",
        }
        for prefix, module in (("org/soutalab/celtium/probe/", "celtium-probe"),
                               ("org/soutalab/celtium/vulkan/", "celtium-vulkan")):
            sources = repository / module / "src/main/java" / prefix
            required.update(prefix + source.stem + ".class" for source in sources.glob("*.java"))
        # Recursive coverage of the packages whose absence still leaves a jar that starts and draws nothing (#392): the GL
        # stub is a subpackage, and the shaders are resources, so the flat
        # glob above never saw any of them - 104 entries could be deleted from a distribution jar and this said
        # verified=true with no errors.
        # The validation client is deliberately *not* in this list: it is a separate jar and must not ship, which is the
        # check right below.
        for prefix, module in (("org/soutalab/celtium/vulkan/", "celtium-vulkan"),):
            sources = repository / module / "src/main/java" / prefix
            if not sources.is_dir():
                errors.append(f"Missing source tree for the entry manifest: {sources}")
                continue
            for source in sorted(sources.rglob("*.java")):
                relative = source.relative_to(repository / module / "src/main/java")
                required.add(str(relative.with_suffix(".class")))
        # The runtime shaders: every GLSL the build compiles has to be in the jar, because a missing one is a pipeline that
        # cannot be built for that family - the failure is at run time, in the game, not here.
        shader_sources = repository / "celtium-vulkan" / "src/main/shaders"
        for shader in sorted(shader_sources.glob("*")):
            if shader.suffix in (".vert", ".frag", ".comp", ".geom"):
                required.add("org/soutalab/celtium/vulkan/spirv/" + shader.name + ".spv")
        errors.extend(f"Missing entry: {name}" for name in sorted(required - names))
        # The fixture is a test harness, not part of the product: shipping it would put the validation client's mixins in
        # an ordinary install.
        leaked = sorted(name for name in names if name.startswith("org/soutalab/celtium/validation/"))
        if leaked:
            errors.append("The validation client is in the distribution jar: %s" % leaked[:3])

        manifest = read_manifest(jar.read("META-INF/MANIFEST.MF"))
        if manifest.get("Class-Path"):
            errors.append("Manifest names external jars although the dependencies are embedded")
        expected_manifest = {
            "FMLAT": "celeritas_at.cfg",
            "FMLCorePlugin": "org.taumc.celeritas.core.CeleritasLoadingPlugin",
            "FMLCorePluginContainsFMLMod": "true",
            "ForceLoadAsMod": "true",
            "Celtium-License": "LGPL-3.0",
            "Celtium-Vulkan-Minimum": "1.2",
            "Celtium-Upstream-Commit": "989ebf2e52ceed052ec112a569e0c1d172095756",
        }
        for key, expected in expected_manifest.items():
            if manifest.get(key) != expected:
                errors.append(f"Manifest {key}: expected {expected!r}, got {manifest.get(key)!r}")
        if "Celtium-Stage" in manifest:
            errors.append("Manifest Celtium-Stage is a stale 2026-10-09 label; replaced by Celtium-License and "
                          "Celtium-Vulkan-Minimum")

        metadata = json.loads(jar.read("mcmod.info"))
        if len(metadata) != 1 or metadata[0].get("modid") != "celeritas":
            errors.append("mcmod.info must declare exactly one mod, with ID celeritas")
        else:
            if metadata[0].get("mcversion") != "1.12.2":
                errors.append("Unexpected Minecraft version")
            if not metadata[0].get("version") or "${" in metadata[0]["version"]:
                errors.append("Unexpanded mod version")
            # The mod ID stays `celeritas` so existing integrations keep working, but every user-visible name
            # must be Celtium. Credits and the licence notices keep the upstream attribution.
            if metadata[0].get("name") != "Celtium":
                errors.append(f"Mod display name must be Celtium, got {metadata[0].get('name')!r}")

        rebrand = rebrand_errors(jar)
        errors.extend(rebrand["errors"])

        notices = {
            "COPYING": "COPYING",
            "COPYING.LESSER": "COPYING.LESSER",
            "README.md": "README.md",
            "docs/upstream-README.md": "docs/upstream-README.md",
            "licenses/bitraster-LICENSE": "common/src/main/java/grondag/bitraster/LICENSE",
        }
        for entry, original in notices.items():
            if entry in names and jar.read(entry) != (repository / original).read_bytes():
                errors.append(f"Notice differs from the repository: {entry}")

        license_problems, license_notes = license_errors(jar, names, repository)
        errors.extend(license_problems)

        for name in entries:
            if name.startswith(("org/lwjgl/", "org/joml/", "com/google/gson/")):
                errors.append(f"Unexpected bundled runtime / unrelocated dependency: {name}")
            if name.startswith("org/soutalab/celtium/validation/"):
                errors.append(f"Validation fixture must not be embedded in the mod: {name}")
            if name.endswith((".so", ".dll", ".dylib", ".jnilib", ".jar")):
                errors.append(f"Unexpected native library or nested jar: {name}")
            if name.endswith(".class"):
                data = jar.read(name)
                if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                    errors.append(f"Invalid class file: {name}")
                    continue
                minor, major = struct.unpack(">HH", data[4:8])
                class_versions[major] += 1
                # The contract is the project's, not Java 8's. Celtium's own modules build at --release 21, so class
                # major 65 is expected and allowed; what must not happen is a class from a later release (Java 22 is
                # 66) or a preview class, because the game is only promised to run on the JDKs the plan names. The
                # check used to demand 52 for every class, which reported the whole Vulkan package as broken while the
                # README quoted "Java 8 bytecode" back at the reader - two documents agreeing on a contract the code
                # had already left. If the release target changes, this number and the README change with it.
                if major > 65 or minor == 65535:
                    errors.append(f"Class is newer than the release-21 target: {name} ({major}.{minor})")

    return {
        "jar": str(path),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "classVersions": dict(sorted(class_versions.items())),
        "modMetadata": metadata,
        "manifest": manifest,
        "licenses": license_notes,
        "errors": errors,
        "verified": not errors,
        "scope": "packaging and licence content only; no Minecraft startup, GL/Vulkan interoperability or "
                 "correctness claim",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = verify(args.jar)
    rendered = json.dumps(report, indent=2) + "\n"
    print(rendered, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    return 0 if report["verified"] else 1


if __name__ == "__main__":
    sys.exit(main())
