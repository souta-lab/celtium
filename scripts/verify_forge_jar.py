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


def jvmdowngrader_pin(repository):
    """The JVM Downgrader version the common project's plugin pin names, read from the build file.

    The stubs this covers are shaded in by `common/build.gradle`'s `shadeDowngradedApi`, so that file is the
    pin. `forge122/build.gradle.kts` resolves the licence artifact at the same version; a licence notice for a
    different version than the code it covers is worse than no notice, so the two are compared here rather
    than trusted to stay in step.
    """
    text = (repository / "common" / "build.gradle").read_text(encoding="utf-8")
    match = re.search(r"id\s+['\"]xyz\.wagyourtail\.jvmdowngrader['\"]\s+version\s+['\"]([^'\"]+)['\"]", text)
    return match.group(1) if match else None


def cached_jvmdowngrader(version):
    """The dependency's own jar and POM in the Gradle cache, or (None, None) when they are not there."""
    cache = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "xyz.wagyourtail.jvmdowngrader" / "jvmdowngrader" / version
    if not cache.is_dir():
        return None, None
    jars = sorted(cache.glob("*/*.jar"))
    poms = sorted(cache.glob("*/*.pom"))
    return (jars[0] if jars else None), (poms[0] if poms else None)


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
        # The prefix is `common/`-rooted: the stubs come from the common project's multi-source-set layout.
        "common/xyz/wagyourtail/jvmdg/": "licenses/jvmdowngrader-LICENSE.md",
        "org/embeddedt/embeddium/impl/shadow/joml/": "META-INF/LICENSE",
    }
    census = {}
    for prefix, notice in coverage.items():
        count = sum(1 for n in names if n.startswith(prefix) and not n.endswith("/"))
        census[prefix] = {"classes": count, "notice": notice}
        if count and notice not in names:
            errors.append(f"{prefix} is bundled ({count} classes) but its notice {notice} is absent")
    notes["licenseCensus"] = census

    # (4) The JVM Downgrader notice must come from the version the build pins, compared against the
    # dependency's own jar rather than against a remembered string.
    version = jvmdowngrader_pin(repository)
    notes["jvmDowngraderPin"] = version
    if version is None:
        errors.append("Could not read the jvmdowngrader plugin pin from common/build.gradle")
    else:
        dep_jar, dep_pom = cached_jvmdowngrader(version)
        if dep_jar is None:
            notes["jvmDowngraderNoticeCheck"] = f"SKIPPED: jvmdowngrader-{version}.jar is not in the Gradle cache"
        else:
            with zipfile.ZipFile(dep_jar) as dependency:
                for entry, shipped in (("LICENSE.md", "licenses/jvmdowngrader-LICENSE.md"),
                                       ("license/LGPLv2.1.md", "licenses/jvmdowngrader-LGPLv2.1.md")):
                    if shipped not in names:
                        errors.append(f"Missing entry: {shipped} ({entry} of jvmdowngrader-{version})")
                    elif dependency.read(entry) != jar.read(shipped):
                        errors.append(f"{shipped} differs from {entry} in jvmdowngrader-{version}.jar")
            notes["jvmDowngraderNoticeCheck"] = f"compared against jvmdowngrader-{version}.jar"
        if dep_pom is None:
            notes["jvmDowngraderDeclaredLicense"] = "unknown: POM not in the Gradle cache"
        else:
            pom_text = dep_pom.read_text(encoding="utf-8")
            declared = re.search(r"<licenses>.*?<name>([^<]+)</name>", pom_text, re.S)
            notes["jvmDowngraderDeclaredLicense"] = declared.group(1) if declared else "no <licenses> block in the POM"

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
            "licenses/jvmdowngrader-LICENSE.md",
            "licenses/jvmdowngrader-LGPLv2.1.md",
            "org/taumc/celeritas/CeleritasVintage.class",
            "org/taumc/celeritas/core/CeleritasLoadingPlugin.class",
            "org/soutalab/celtium/forge/CeltiumCommand.class",
            "org/embeddedt/embeddium/impl/shadow/joml/Matrix4f.class",
        }
        for prefix, module in (("org/soutalab/celtium/probe/", "celtium-probe"),
                               ("org/soutalab/celtium/vulkan/", "celtium-vulkan")):
            sources = repository / module / "src/main/java" / prefix
            required.update(prefix + source.stem + ".class" for source in sources.glob("*.java"))
        errors.extend(f"Missing entry: {name}" for name in sorted(required - names))

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
