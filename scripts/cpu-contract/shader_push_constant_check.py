#!/usr/bin/env python3
"""Fails when a shader's push-constant block declares overlapping members.

Why this exists
---------------
glslangValidator accepts a push-constant block whose members overlap - two members
may share bytes, which the scalar block layout would allow. The validation layer's
spirv-val does not: it rejects the module at `vkCreateShaderModule` with

    Structure member N at offset X overlaps previous member ending at offset Y ...
    in the PushConstant storage class using standard layout rules

and under the GL-less window that is not a warning: every pipeline that uses the
shader fails to create, so the frame renders nothing at all while the build, the
counters and the logs all look healthy. That happened twice in one session -
`immediate_position_tex.vert` kept a dead third model-view column while
`u_ColorA` was added at 96, and `immediate_tex_color.frag` declared a second
member at 96 next to `u_MvColumn2` - and both times the symptom was "every leg is
black", which costs a whole round to re-diagnose.

This is a CPU contract check: it reads the shader sources, computes each member's
byte range from the declared type and the explicit `layout(offset = N)` where one
is present, and fails if any member starts before the previous one ends. Run it
before a build when a push-constant block changes.

Exit status: 0 when every block is consistent, 1 when one is not.
"""

import glob
import os
import re
import sys

SIZES = {
    "float": 4,
    "int": 4,
    "uint": 4,
    "bool": 4,
    "vec2": 8,
    "ivec2": 8,
    "uvec2": 8,
    "vec3": 12,
    "ivec3": 12,
    "uvec3": 12,
    "vec4": 16,
    "ivec4": 16,
    "uvec4": 16,
    "mat4": 64,
    "mat3": 36,
}

BLOCK = re.compile(r"layout\(push_constant\)\s+uniform\s+\w+\s*\{(.*?)\}\s*\w+\s*;", re.S)
OFFSET = re.compile(r"layout\(offset\s*=\s*(\d+)\)")
MEMBER = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;")
ARRAY = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\[\s*(\d+)\s*\]\s*;")


def members_of(body):
    """(start, end, name, declared_offset) for each member, in declaration order."""
    out = []
    offset = 0
    explicit = None
    for raw in body.split("\n"):
        line = re.sub(r"//.*", "", raw)
        match = OFFSET.search(line)
        if match:
            explicit = int(match.group(1))
        array = ARRAY.search(line)
        if array:
            base = SIZES.get(array.group(1))
            if base is None:
                continue
            count = int(array.group(3))
            start = explicit if explicit is not None else offset
            out.append((start, start + base * count - 1, array.group(2), explicit is not None))
            offset = start + base * count
            explicit = None
            continue
        member = MEMBER.search(line)
        if member:
            base = SIZES.get(member.group(1))
            if base is None:
                continue
            start = explicit if explicit is not None else offset
            out.append((start, start + base - 1, member.group(2), explicit is not None))
            offset = start + base
            explicit = None
    return out


def check(path):
    with open(path, "r", encoding="utf-8") as handle:
        source = handle.read()
    problems = []
    for block in BLOCK.finditer(source):
        parsed = members_of(block.group(1))
        for index in range(1, len(parsed)):
            previous = parsed[index - 1]
            current = parsed[index]
            if current[0] <= previous[1]:
                problems.append(
                    "%s: member %s at offset %d overlaps %s ending at %d"
                    % (os.path.basename(path), current[2], current[0], previous[2], previous[1])
                )
        if parsed:
            # The block's end is what the declared push range has to cover; a pipeline that declares less
            # than this draws with a member it never wrote.
            end = max(member[1] for member in parsed) + 1
            if end > 256:
                problems.append(
                    "%s: push block ends at %d bytes, past the 128 bytes every Vulkan device guarantees "
                    "and past the 256 this workspace measures" % (os.path.basename(path), end)
                )
    return problems


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "celtium-vulkan/src/main/shaders"
    paths = sorted(glob.glob(os.path.join(root, "*.vert")) + glob.glob(os.path.join(root, "*.frag")))
    if not paths:
        print("no shaders under %s" % root)
        return 1
    failures = []
    for path in paths:
        failures.extend(check(path))
    for failure in failures:
        print("FAIL " + failure)
    print("checked %d shaders, %d problem(s)" % (len(paths), len(failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
