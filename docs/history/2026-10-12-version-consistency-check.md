# The artifact's version is checked in four places, in one command

Owner: **glyph-fix**. The trap (docs/plan.md row 47, §0.365): the development version was rebranded
`2.5.12-celtium.0.1.0-dev.1` -> `0.0.2-alpha-dev`, the old build output stayed on disk, and a helper script that
copied the old path ran a leg against the old jar - a hook that was not in that jar read as "the hook does not fire".
The jar's name, its contents, the build's default and the plan's header table are four places the same version is
written and nothing compared them.

## The check

`scripts/verify_version_consistency.py`, one command, no GPU and no game:

```sh
python3 scripts/verify_version_consistency.py            # newest packaged artifact
python3 scripts/verify_version_consistency.py --jar PATH  # a specific artifact
python3 scripts/verify_version_consistency.py --json      # the same report as JSON
```

It prefers `build-1122/build/libs/<version>/` (what `packageJar` ships) over the intermediates `forge122/build/libs`
also holds, so a `-reobf` or doubled-suffix name is not mistaken for the distributable. Each check prints the value it
measured and, when it fails, the artifact, the field, what was expected and what was found.

| check | reads |
|---|---|
| file name carries a version | `celtium-forge-mc1.12.2-<version>.jar` |
| artifact is a readable zip | the file itself (a truncated artifact is named, not raised) |
| `mcmod.info` version equals the file name | `mcmod.info[0].version` |
| version directory equals the file name | `build-1122/build/libs/<version>/` |
| manifest version equals the file name | `Celtium-Version` / `Implementation-Version`, when present |
| build default is a development build of a named release | `build-1122/build.gradle.kts`'s `orElse(...)` |
| the artifact is a build of the configured default | the default, or a release the plan names |
| the plan's header names the artifact's release | `docs/plan.md`, first 80 lines |
| no artifact of the old `2.5.12-celtium` family | `build-1122/build/libs`, `forge122/build/libs` |

CI runs it in the `minecraft-1122` job after `verify_forge_jar.py`, so drift fails the PR that introduces it.

## Measured, raw

**The normal state** (`python3 scripts/verify_version_consistency.py`, exit 0):

```
ok   file name carries a version            .../0.0.2-alpha-dev/celtium-forge-mc1.12.2-0.0.2-alpha-dev.jar = '0.0.2-alpha-dev'
ok   artifact is a readable zip             ...jar = 'readable'
ok   mcmod.info version equals the file name ...jar = '0.0.2-alpha-dev'
ok   version directory equals the file name ...jar = '0.0.2-alpha-dev'
ok   build default is a development build of a named release  build-1122/build.gradle.kts = '0.0.2-alpha-dev'
ok   the artifact is a build of the configured default  ...jar = '0.0.2-alpha-dev'
ok   the plan's header names the artifact's release  docs/plan.md = ['0.0.2-alpha']
ok   no artifact of the old 2.5.12-celtium family  build-1122/build/libs, forge122/build/libs = []
verified: True
```

**A name that disagrees with the contents** (the jar copied to `celtium-forge-mc1.12.2-9.9.9-fake.jar`, exit 1):

```
FAIL mcmod.info version equals the file name  /tmp/fake-jar/celtium-forge-mc1.12.2-9.9.9-fake.jar = '0.0.2-alpha-dev'  expected '9.9.9-fake'
FAIL version directory equals the file name   /tmp/fake-jar/celtium-forge-mc1.12.2-9.9.9-fake.jar = 'fake-jar'  expected '9.9.9-fake'
FAIL the artifact is a build of the configured default  ... = '9.9.9-fake'  expected '0.0.2-alpha-dev'
FAIL the plan's header names the artifact's release  docs/plan.md = ['0.0.2-alpha']  expected '9.9.9-fake'
verified: False
```

**The regression the row is about** - a jar of the removed family put back under `forge122/build/libs/` (exit 1):

```
FAIL mcmod.info version equals the file name  .../celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar = '0.0.2-alpha-dev'  expected '2.5.12-celtium.0.1.0-dev.1'
FAIL no artifact of the old 2.5.12-celtium family  ... = ['forge122/build/libs/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar']  expected []
verified: False
```

The same run with a **zero-byte** file of that name (the first attempt) ended in a `BadZipFile` traceback; the check
now reports `FAIL artifact is a readable zip ... = 'unreadable: File is not a zip file'` and names the file, because an
unhelpful failure is the shape this script exists to remove. Both dummy files were deleted immediately after the test
and the normal state was re-run: `exit 0`.

## Not established

* That the *released* `0.0.2-alpha.jar` (2,594,729 bytes, the one the plan's header cites) is still byte-identical to
  the artifact this check would pick today - the check compares versions, not bytes; its sha256 is the plan's record.
* The manifest has no version attribute today, so that row is skipped rather than asserted; a future
  `Celtium-Version` would be compared automatically.
