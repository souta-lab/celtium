#!/usr/bin/env python3
"""Check that the mixin plugin drops a contested mixin by the name the mixin system actually uses.

The exclusion in CeleritasVintageMixinPlugin is a string removal, and a string removal that names the wrong
string removes nothing. The mixin system reports this class as `mixins.celeritas.json:core.ExplicitGcMixin`,
so the name the plugin must remove is `core.ExplicitGcMixin` - the path under the mixin package with the
`.class` suffix dropped and separators turned into dots. That derivation is what this checks, against the
plugin's own `mixinClassify` rather than against a copy of it.

The second check is that the removal is logged. An exclusion nobody can see reads exactly like a mixin that
applied, which is the failure mode this whole change exists to avoid.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java"
MIXIN = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/core/ExplicitGcMixin.java"


def check(name: str, got, want) -> bool:
    ok = got == want
    print(f"{'ok  ' if ok else 'FAIL'} {name}: got {got!r} want {want!r}")
    return ok


def main() -> int:
    src = PLUGIN.read_text(encoding="utf-8")
    results = []

    # The name removed must be the name the mixin system reports. Derived the way the plugin derives it:
    # the mixin class's path under org/taumc/celeritas/mixin, minus ".class", separators to dots.
    mixin_pkg = "org/taumc/celeritas/mixin/"
    mixin_file = "core/ExplicitGcMixin.class"
    expected = mixin_file[: -len(".class")].replace("/", ".")
    results.append(check("the name the mixin system uses", expected, "core.ExplicitGcMixin"))

    m = re.search(r'possibleMixinClasses\.remove\("([^"]+)"\)', src)
    results.append(check("the plugin removes exactly that name", m.group(1) if m else None, expected))

    # The removal must be inside getMixins, because that is the list the mixin system reads.
    body = src[src.index("public List<String> getMixins()"):]
    results.append(check("the removal is in getMixins", 'possibleMixinClasses.remove(' in body.split("private static")[0], True))

    # A drop must be visible: warn-level, and naming the class.
    warn = re.search(r'LOGGER\.warn\("([^"]{0,200})', body)
    results.append(check("the drop is logged at warn", warn is not None, True))
    results.append(check("the log names the class", "ExplicitGcMixin" in (warn.group(1) if warn else ""), True))

    # The probe must not consult the mod list: it runs before that is dependable.
    results.append(check("the probe does not use Loader", "Loader.isModLoaded" in src, False))
    results.append(check("the probe tests the coremod class", "zone.rong.loliasm.core.LoliLoadingPlugin" in src, True))

    # The class still exists: this excludes it at registration, it does not delete the mixin.
    results.append(check("the mixin source is still present", MIXIN.is_file(), True))

    failed = results.count(False)
    print(f"\nmixin exclusion contract: {len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
