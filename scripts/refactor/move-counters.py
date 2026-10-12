#!/usr/bin/env python3
"""Move a set of backend counters into a domain statistics class (refactoring plan R2).

Used by the stages after Font, Model and Texture, where the pattern is settled: the counter's
declaration leaves the backend, the class owns it behind a named note method and a getter, every
write site calls the note method, and every read goes through the getter. Field names in the
verification line are the getters' names, so the line's text does not change.

    move-counters.py <ClassName> <counter>[:<Kind>] ...

Kind is `long` (default), `int`, `String` or `double`. A `String` gets `setX(String)` rather than
a note method, because it is a value and not a count.

The script refuses to run if a counter it was given is not declared in the backend, so a typo
cannot silently do nothing. It does not build - the caller builds after each stage, which is the
rule this refactoring learned the hard way.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    class_name = sys.argv[1]
    spec = []
    for arg in sys.argv[2:]:
        name, _, kind = arg.partition(":")
        spec.append((name, kind or "long"))

    path = ROOT / ("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/%s.java" % class_name)
    source = BACKEND.read_text(encoding="utf-8")
    body = [
        "package org.soutalab.celtium.vulkan;",
        "",
        "/**",
        " * The %s domain's counters (refactoring plan R2)." % class_name.replace("Statistics", "").lower(),
        " *",
        " * <p>Each counter's declaration left the backend, every writer calls a named method here, and the line reads",
        " * the getter - so the field names and their order in the verification line are unchanged while the counter's",
        " * owner is this class.",
        " */",
        "public final class %s {" % class_name,
        "",
    ]
    field = class_name[0].lower() + class_name[1:]
    moved = []
    for name, kind in spec:
        decl = re.compile(r"^    private (?:static )?(?:final )?[\w<>.\[\], ?]+ %s(?: = [^;]*)?;\n" % re.escape(name), re.M)
        if not decl.search(source):
            raise SystemExit("not declared in the backend: %s" % name)
        source = decl.sub("", source, count=1)
        body += ["    private %s %s;" % (kind, name), ""]
        note = "note" + name[0].upper() + name[1:]
        setter = "set" + name[0].upper() + name[1:]
        body += ["    /** Records %s. */" % name]
        if kind == "String":
            body += ["    void %s(String value) {" % setter,
                     "        this.%s = value;" % name,
                     "    }", ""]
        else:
            body += ["    void %s() {" % note,
                     "        this.%s++;" % name,
                     "    }", ""]
        body += ["    /** {@return %s}. */" % name,
                 "    %s %s() {" % (kind, name),
                 "        return this.%s;" % name,
                 "    }", ""]

        def rewrite(m):
            rest = source[m.end():]
            if kind != "String" and rest.startswith("++"):
                return "this.%s.%s()" % (field, note)
            if rest.startswith(" = "):
                end = source.index(";", m.end())
                value = source[m.end() + 3:end]
                return "this.%s.%s(%s)" % (field, setter, value) if kind == "String" \
                    else "this.%s.%s(%s)" % (field, setter, value)
            return "this.%s.%s()" % (field, name)

        pattern = re.compile(r"this\.%s\b" % re.escape(name))
        # for the increment form the `++` has to be consumed as well
        source = re.sub(r"this\.%s\+\+" % re.escape(name), "this.%s.%s()" % (field, note), source)
        source = re.sub(r"this\.%s = ([^;]+);" % re.escape(name),
                        lambda m: "this.%s.%s(%s);" % (field, setter, m.group(1)), source)
        source = pattern.sub("this.%s.%s()" % (field, name), source)
        moved.append(name)

    body += ["}", ""]
    anchor = "    private final PublishedFrameTextures publishedTextures = new PublishedFrameTextures();"
    if "private final %s %s" % (class_name, field) not in source:
        if anchor not in source:
            raise SystemExit("owner field anchor not found")
        source = source.replace(anchor, anchor + """

    /** The %s domain's counters (refactoring plan R2). */
    private final %s %s = new %s();""" % (class_name.replace("Statistics", "").lower(), class_name, field, class_name), 1)
    BACKEND.write_text(source, encoding="utf-8")
    path.write_text("\n".join(body), encoding="utf-8")
    print("moved %d counters into %s: %s" % (len(moved), class_name, ", ".join(moved)))


if __name__ == "__main__":
    main()
