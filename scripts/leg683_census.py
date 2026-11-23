#!/usr/bin/env python3
"""leg683_census.py — item683 verification census leg harness (PC-side).

Purpose: run the item683 fix-verification census in the terrain2 private copy
(Nostalgic Hardcore pack, entry point (24849.5, 200, 34436.5), -Xmx8192m via
the terrain2 driver) and aggregate the paired census output:

  * CELTIUM_PRESENT_CENSUS  (item683 present-side exclusive decomposition)
  * CELTIUM_PREPARE_NULL    (item701 WorldSlice.prepare null-reason census)

Design record: ../item701-prep.md (jar basis = secretary ruling #4992,
verdict rules pre-registered there, section 5).

Subcommands:
  preflight  resource/precondition checks (RAM, JVM count, paths)
  prep       swap the leg jar into the private copy + rewrite relauncher args
  run        launch the driver under a systemd --user unit and monitor to t+420
  aggregate  parse a latest.log and emit per-sub-form counts + verdict inputs
  restore    put the shipped jar and relauncher.json back

Python 3 stdlib only. Intended to run ON the PC. All paths are parameters;
defaults follow the terrain2 conventions used by biyu-terrain-2 and
terrain-present-683-2.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

# ---------------------------------------------------------------------------
# Constants (measured originals; see item701-prep.md section 2)

SHIPPED_JAR_SHA256 = "0c0c6a7573c27fb29e6004dd0b0dab6c26682bb756fff6433e59abe88453e74e"

# Arm-1 (pre-fix) final stable census line, terrain-present-683-2.md section 4.
ARM1_FINAL = {
    "frame": 80, "visible": 898, "drawn": 212, "queued": 4, "building": 4,
    "wedged": 0, "terminalBuilt": 678, "terminalUnbuilt": 0,
    "mirrorMismatch": 0, "graphSections": 3600,
}

# Both censuses must be present in the leg jar (checked as class-byte strings,
# same method as terrain-present-683-3 used for the 683 instrument jar).
JAR_MARKERS = [
    b"CELTIUM_PRESENT_CENSUS",
    b"celtium.debug.presentCensus",
    b"CELTIUM_PREPARE_NULL",
    b"celtium.debug.prepareNullCensus",
]

# relauncher.json args managed by prep (683 arm-1 form + the 701 property).
MANAGED_ARGS = [
    "-Dceltium.debug.logLinesPerTag=100000",
    "-Dceltium.debug.listInputLog=200",
    "-Dceltium.debug.listSizeLog=400",
    "-Dceltium.debug.presentCensus=48",
    "-Dceltium.debug.prepareNullCensus=1000",
    "-Dceltium.debug.buildFlow=true",
]
MANAGED_KEYS = {a.split("=", 1)[0] for a in MANAGED_ARGS}

MIN_AVAILABLE_KIB = 6 * 1024 * 1024  # 6 GiB, pre-registered start condition

PRESENT_RE = re.compile(
    r"CELTIUM_PRESENT_CENSUS frame=(\d+) visible=(\d+) drawn=(\d+) queued=(\d+)"
    r" building=(\d+) wedged=(\d+) terminalBuilt=(\d+) terminalUnbuilt=(\d+)"
    r" mirrorMismatch=(\d+) graphSections=(\d+)")
PREPARE_NULL_RE = re.compile(
    r"CELTIUM_PREPARE_NULL calls=(\d+) contexts=(\d+) nulls=(\d+)"
    r" blankChunk=(\d+) slotAbsent=(\d+) slotEmpty=(\d+) yOutOfRange=(\d+)"
    r" legitimateEmpty=(\d+) suspicious=(\d+)")
CHUNK_ADD_RE = re.compile(r"CELTIUM_CHUNK_ADD.*known=(\d+) ready=(\d+)")
VISIBLE_RE = re.compile(r"CELTIUM_VISIBLE.*count=(\d+)")
LOGIN_MARK = "logged in with entity id"


def now_iso():
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def stamp():
    return datetime.datetime.now().strftime("%Y%m%d-%H%M%S")


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def count_game_jvms():
    """Count processes that look like the game JVM (launchwrapper/minecraft)."""
    me = str(os.getpid())
    hits = []
    for pid in os.listdir("/proc"):
        if not pid.isdigit() or pid == me:
            continue
        try:
            with open(os.path.join("/proc", pid, "cmdline"), "rb") as f:
                cmd = f.read().lower()
        except OSError:
            continue
        if b"launchwrapper" in cmd or b"minecraft" in cmd:
            hits.append(int(pid))
    return hits


def mem_available_kib():
    with open("/proc/meminfo") as f:
        for line in f:
            if line.startswith("MemAvailable:"):
                return int(line.split()[1])
    return 0


def fail(msg):
    print("FAIL: " + msg)
    sys.exit(1)


# ---------------------------------------------------------------------------
# preflight

def cmd_preflight(args):
    ok = True

    def check(label, good, detail=""):
        nonlocal ok
        print(("PASS " if good else "FAIL ") + label + (" — " + detail if detail else ""))
        ok = ok and good

    avail = mem_available_kib()
    check("MemAvailable >= 6 GiB", avail >= MIN_AVAILABLE_KIB,
          "actual %.2f GiB" % (avail / 1024.0 / 1024.0))
    jvms = count_game_jvms()
    check("game JVM count == 0", len(jvms) == 0, "pids=%s" % jvms)
    check("private copy minecraft dir exists", os.path.isdir(args.mc_dir), args.mc_dir)
    mods = os.path.join(args.mc_dir, "mods")
    check("mods dir exists", os.path.isdir(mods), mods)
    if os.path.isdir(mods):
        jars = [n for n in os.listdir(mods) if n.startswith("celtium-forge-")]
        check("exactly one celtium jar in mods", len(jars) == 1, str(jars))
    check("relauncher.json exists",
          os.path.isfile(os.path.join(args.mc_dir, "relauncher.json")))
    check("driver exists", os.path.isfile(args.driver), args.driver)
    if args.jar:
        check("leg jar exists", os.path.isfile(args.jar), args.jar)
    print("NOTE: fixture acquisition is a lanechat act (after item697); "
          "this harness does not acquire it.")
    sys.exit(0 if ok else 1)


# ---------------------------------------------------------------------------
# prep

def jar_missing_markers(jar_path):
    """Return the list of census markers NOT found in the jar's entries."""
    remaining = set(JAR_MARKERS)
    with zipfile.ZipFile(jar_path) as z:
        for info in z.infolist():
            if not remaining:
                break
            if info.is_dir() or info.file_size == 0:
                continue
            try:
                data = z.read(info)
            except Exception:
                continue
            for m in list(remaining):
                if m in data:
                    remaining.discard(m)
    return sorted(remaining)


def find_fetch_updates(obj):
    """Return the value of any 'fetchUpdates' key found in nested JSON."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "fetchUpdates":
                return v
            r = find_fetch_updates(v)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_fetch_updates(v)
            if r is not None:
                return r
    return None


def cmd_prep(args):
    if not os.path.isfile(args.jar):
        fail("leg jar not found: %s" % args.jar)
    digest = sha256_of(args.jar)
    print("leg jar sha256 = %s" % digest)
    if digest != args.sha256:
        fail("sha256 mismatch: expected %s" % args.sha256)
    missing = jar_missing_markers(args.jar)
    if missing:
        fail("jar is missing census markers: %s"
             % [m.decode() for m in missing])
    print("PASS jar contains both censuses (4 markers)")

    mods = os.path.join(args.mc_dir, "mods")
    jars = [n for n in os.listdir(mods) if n.startswith("celtium-forge-")]
    if len(jars) != 1:
        fail("expected exactly one celtium jar in mods, found %s" % jars)
    placed_name = jars[0]
    src_jar = os.path.join(mods, placed_name)
    os.makedirs(args.backup_dir, exist_ok=True)
    ts = stamp()
    jar_backup = os.path.join(
        args.backup_dir, "%s.bak-%s-%s" % (placed_name, args.arm, ts))
    if os.path.exists(jar_backup):
        fail("backup already exists: %s" % jar_backup)
    shutil.copy2(src_jar, jar_backup)
    print("mods jar backed up -> %s (sha256 %s)"
          % (jar_backup, sha256_of(jar_backup)))
    shutil.copy2(args.jar, src_jar)
    placed_sha = sha256_of(src_jar)
    if placed_sha != args.sha256:
        fail("placed jar sha mismatch after copy: %s" % placed_sha)
    print("leg jar placed as mods/%s, sha verified" % placed_name)

    relaunch = os.path.join(args.mc_dir, "relauncher.json")
    with open(relaunch) as f:
        raw = f.read()
    try:
        cfg = json.loads(raw)
    except json.JSONDecodeError as e:
        fail("relauncher.json is not valid JSON (%s); edit manually per "
             "item701-prep.md section 4" % e)
    arg_list = cfg.get("args")
    if not isinstance(arg_list, list):
        fail("relauncher.json has no top-level 'args' list; edit manually "
             "per item701-prep.md section 4 (file left untouched)")
    rel_backup = os.path.join(
        args.backup_dir, "relauncher.json.bak-%s-%s" % (args.arm, ts))
    if os.path.exists(rel_backup):
        fail("backup already exists: %s" % rel_backup)
    shutil.copy2(relaunch, rel_backup)
    kept = [a for a in arg_list
            if not (isinstance(a, str)
                    and a.split("=", 1)[0] in MANAGED_KEYS)]
    cfg["args"] = kept + MANAGED_ARGS
    with open(relaunch, "w") as f:
        json.dump(cfg, f, indent=2)
        f.write("\n")
    fu = find_fetch_updates(cfg)
    if fu is not False:
        print("WARN: fetchUpdates is %r (expected false); fix before launch"
              % (fu,))
    print("relauncher.json backed up -> %s; managed args set:" % rel_backup)
    for a in MANAGED_ARGS:
        print("  " + a)

    manifest = {
        "arm": args.arm,
        "prepared_at": now_iso(),
        "mc_dir": args.mc_dir,
        "jar_source": os.path.abspath(args.jar),
        "jar_sha256": args.sha256,
        "provenance": args.provenance,
        "placed_name": placed_name,
        "mods_jar_backup": jar_backup,
        "relauncher_backup": rel_backup,
        "managed_args": MANAGED_ARGS,
    }
    mpath = os.path.join(args.backup_dir, "prep-manifest-%s.json" % args.arm)
    with open(mpath, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print("manifest -> %s" % mpath)
    print(json.dumps(manifest, indent=2))


# ---------------------------------------------------------------------------
# run

def unit_active(unit):
    r = subprocess.run(["systemctl", "--user", "is-active", unit],
                       capture_output=True, text=True)
    return r.stdout.strip() == "active"


def tail_lines(path, n=400):
    try:
        with open(path, "rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - 1 << 18))
            data = f.read().decode("utf-8", "replace")
        return data.splitlines()[-n:]
    except OSError:
        return []


def cmd_run(args):
    log = os.path.join(args.mc_dir, "logs", "latest.log")
    if os.path.exists(log):
        prev = log + ".pre-" + stamp()
        os.rename(log, prev)
        print("previous latest.log -> %s" % prev)
    hs_before = {n for n in os.listdir(args.mc_dir)
                 if n.startswith("hs_err_pid")}
    cmd = ["systemd-run", "--user", "--collect", "--unit=" + args.unit,
           "python3", args.driver, args.driver_arg]
    print("launch: %s" % " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        fail("systemd-run failed: %s" % (r.stderr.strip() or r.stdout.strip()))
    print("unit %s started; waiting for login line %r" % (args.unit, LOGIN_MARK))

    t0 = None
    deadline = time.time() + args.login_timeout
    last_census = ""
    while time.time() < deadline:
        if not unit_active(args.unit):
            fail("unit went inactive before login; inspect the log and the "
                 "driver gates (OptiFine Continue screen is manual)")
        for line in tail_lines(log):
            if LOGIN_MARK in line:
                t0 = time.time()
                print("login detected (t0): %s" % line.strip()[:160])
                break
        if t0:
            break
        time.sleep(args.poll)
    if not t0:
        fail("login not detected within %ds" % args.login_timeout)

    end = t0 + args.observe
    crashed = []
    while time.time() < end:
        hs_now = {n for n in os.listdir(args.mc_dir)
                  if n.startswith("hs_err_pid")}
        crashed = sorted(hs_now - hs_before)
        lines = tail_lines(log)
        for line in lines:
            if "CELTIUM_PRESENT_CENSUS" in line:
                last_census = line.strip()
        elapsed = time.time() - t0
        print("t+%.0fs unit=%s %s%s" % (
            elapsed, "active" if unit_active(args.unit) else "INACTIVE",
            last_census[:150],
            (" hs_err=" + ",".join(crashed)) if crashed else ""))
        if not unit_active(args.unit):
            print("unit inactive at t+%.0fs (driver stop or crash)" % elapsed)
            break
        time.sleep(args.poll)
    print("observation done. log = %s" % log)
    if crashed:
        print("new hs_err files (evidence, not this leg's verdict target): %s"
              % crashed)
    print("next: aggregate --log %s" % log)


# ---------------------------------------------------------------------------
# aggregate

def parse_log(path):
    present, prepare = [], []
    chunk_add, visible_last, keepup = None, None, 0
    with open(path, errors="replace") as f:
        for line in f:
            m = PRESENT_RE.search(line)
            if m:
                keys = ["frame", "visible", "drawn", "queued", "building",
                        "wedged", "terminalBuilt", "terminalUnbuilt",
                        "mirrorMismatch", "graphSections"]
                present.append(dict(zip(keys, (int(g) for g in m.groups()))))
                continue
            m = PREPARE_NULL_RE.search(line)
            if m:
                keys = ["calls", "contexts", "nulls", "blankChunk",
                        "slotAbsent", "slotEmpty", "yOutOfRange",
                        "legitimateEmpty", "suspicious"]
                prepare.append(dict(zip(keys, (int(g) for g in m.groups()))))
                continue
            m = CHUNK_ADD_RE.search(line)
            if m:
                chunk_add = {"known": int(m.group(1)), "ready": int(m.group(2))}
                continue
            m = VISIBLE_RE.search(line)
            if m:
                visible_last = int(m.group(1))
                continue
            if "Can't keep up" in line:
                keepup += 1
    return present, prepare, chunk_add, visible_last, keepup


def cmd_aggregate(args):
    present, prepare, chunk_add, visible_last, keepup = parse_log(args.log)
    if not present:
        fail("no CELTIUM_PRESENT_CENSUS lines in %s" % args.log)
    if not prepare:
        fail("no CELTIUM_PREPARE_NULL lines in %s" % args.log)
    final = present[-1]
    pn = prepare[-1]

    identity_bad = []
    for p in present:
        s = (p["drawn"] + p["queued"] + p["building"] + p["wedged"]
             + p["terminalBuilt"] + p["terminalUnbuilt"])
        if s != p["visible"]:
            identity_bad.append((p["frame"], p["visible"], s))

    def pct(n, d):
        return (100.0 * n / d) if d else 0.0

    out = []
    out.append("# leg683 census aggregate — %s" % args.log)
    out.append("")
    out.append("## present census (final stable line, frame=%d)" % final["frame"])
    out.append("")
    out.append("| key | fixed arm | arm1 (pre-fix) | delta |")
    out.append("|---|---|---|---|")
    for k in ["visible", "drawn", "queued", "building", "wedged",
              "terminalBuilt", "terminalUnbuilt", "mirrorMismatch",
              "graphSections"]:
        out.append("| %s | %d | %d | %+d |"
                   % (k, final[k], ARM1_FINAL[k], final[k] - ARM1_FINAL[k]))
    out.append("")
    out.append("present lines: %d (first frame=%d, last frame=%d); "
               "queued trajectory first=%d last=%d"
               % (len(present), present[0]["frame"], final["frame"],
                  present[0]["queued"], final["queued"]))
    if identity_bad:
        out.append("WARN decomposition identity broken at %s"
                   % (identity_bad[:5],))
    else:
        out.append("decomposition identity holds on all lines "
                   "(drawn+queued+building+wedged+terminalBuilt+"
                   "terminalUnbuilt == visible)")
    out.append("")
    out.append("## prepare-null census (last snapshot; call counts, "
               "not section counts)")
    out.append("")
    out.append("calls=%d contexts=%d nulls=%d"
               % (pn["calls"], pn["contexts"], pn["nulls"]))
    out.append("")
    out.append("| sub-form | count | share of nulls | class |")
    out.append("|---|---|---|---|")
    out.append("| blankChunk | %d | %.1f%% | suspicious (data not arrived) |"
               % (pn["blankChunk"], pct(pn["blankChunk"], pn["nulls"])))
    out.append("| slotAbsent | %d | %.1f%% | legitimate empty |"
               % (pn["slotAbsent"], pct(pn["slotAbsent"], pn["nulls"])))
    out.append("| slotEmpty | %d | %.1f%% | legitimate empty |"
               % (pn["slotEmpty"], pct(pn["slotEmpty"], pn["nulls"])))
    out.append("| yOutOfRange | %d | %.1f%% | suspicious (separate bug) |"
               % (pn["yOutOfRange"], pct(pn["yOutOfRange"], pn["nulls"])))
    out.append("| legitimateEmpty | %d | %.1f%% | slotAbsent+slotEmpty |"
               % (pn["legitimateEmpty"], pct(pn["legitimateEmpty"], pn["nulls"])))
    out.append("| suspicious | %d | %.1f%% | blankChunk+yOutOfRange |"
               % (pn["suspicious"], pct(pn["suspicious"], pn["nulls"])))
    out.append("")
    out.append("snapshots: %d (cadence: nulls==1, every 100 nulls, first "
               "suspicious; last snapshot is the session total as printed)"
               % len(prepare))
    out.append("")
    out.append("## context")
    out.append("")
    out.append("- CELTIUM_CHUNK_ADD final: %s" % (chunk_add,))
    out.append("- CELTIUM_VISIBLE last count: %s (arm1: 758-768)"
               % (visible_last,))
    out.append("- Can't keep up lines: %d" % keepup)
    out.append("")
    out.append("## pre-registered rule matching (item701-prep.md section 5; "
               "the leg lane judges)")
    out.append("")
    collapsed = final["terminalBuilt"] <= ARM1_FINAL["terminalBuilt"] // 3
    drawn_up = final["drawn"] >= ARM1_FINAL["drawn"] * 2
    out.append("- rule 1 (fix effective): terminalBuilt %d vs arm1 678 "
               "(<=1/3: %s); drawn %d vs arm1 212 (>=2x: %s)"
               % (final["terminalBuilt"], collapsed, final["drawn"], drawn_up))
    dom = max(("blankChunk", pn["blankChunk"]),
              ("legitimateEmpty", pn["legitimateEmpty"]),
              key=lambda kv: kv[1])[0]
    out.append("- rule 2 (dominant null sub-form): %s" % dom)
    out.append("- rule 3 (yOutOfRange > 0 -> file separately): %s"
               % (pn["yOutOfRange"] > 0))
    out.append("- rule 5 (wedged != 0 -> candidate B; mirrorMismatch != 0 "
               "-> candidate D): wedged=%d mirrorMismatch=%d"
               % (final["wedged"], final["mirrorMismatch"]))
    text = "\n".join(out) + "\n"
    sys.stdout.write(text)
    if args.out:
        with open(args.out, "w") as f:
            f.write(text)
        print("aggregate -> %s" % args.out, file=sys.stderr)
    if args.json_out:
        with open(args.json_out, "w") as f:
            json.dump({"present_final": final, "present_lines": len(present),
                       "prepare_null_last": pn,
                       "prepare_snapshots": len(prepare),
                       "chunk_add_final": chunk_add,
                       "visible_last": visible_last, "cant_keep_up": keepup,
                       "identity_violations": identity_bad},
                      f, indent=2)
        print("json -> %s" % args.json_out, file=sys.stderr)


# ---------------------------------------------------------------------------
# restore

def cmd_restore(args):
    with open(args.manifest) as f:
        man = json.load(f)
    mc_dir = man["mc_dir"]
    mods_jar = os.path.join(mc_dir, "mods", man["placed_name"])
    shutil.copy2(man["mods_jar_backup"], mods_jar)
    digest = sha256_of(mods_jar)
    print("mods jar restored: sha256 = %s" % digest)
    if digest != args.shipped_sha:
        fail("restored jar is NOT the shipped jar (expected %s)"
             % args.shipped_sha)
    shutil.copy2(man["relauncher_backup"],
                 os.path.join(mc_dir, "relauncher.json"))
    print("relauncher.json restored from %s" % man["relauncher_backup"])
    jvms = count_game_jvms()
    print("game JVMs now: %s" % jvms)
    if jvms:
        fail("game JVM still running: %s" % jvms)
    print("restore complete (arm %s, prepared %s)"
          % (man["arm"], man["prepared_at"]))


# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    def common(p):
        p.add_argument("--mc-dir",
                       default="/mnt/storage/terrain2/celtium/minecraft")

    p = sub.add_parser("preflight")
    common(p)
    p.add_argument("--driver", default="/tmp/terrain2_driver_ctl.py")
    p.add_argument("--jar", default=None)
    p.set_defaults(fn=cmd_preflight)

    p = sub.add_parser("prep")
    common(p)
    p.add_argument("--jar", required=True)
    p.add_argument("--sha256", required=True)
    p.add_argument("--arm", default="fixed")
    p.add_argument("--provenance", required=True,
                   help="base tip + cherry-pick list, recorded in the manifest")
    p.add_argument("--backup-dir", default="/mnt/storage/terrain683-jars")
    p.set_defaults(fn=cmd_prep)

    p = sub.add_parser("run")
    common(p)
    p.add_argument("--driver", default="/tmp/terrain2_driver_ctl.py")
    p.add_argument("--driver-arg", default="celtium")
    p.add_argument("--unit", default="terrain683-verify")
    p.add_argument("--observe", type=int, default=420)
    p.add_argument("--login-timeout", type=int, default=1800)
    p.add_argument("--poll", type=int, default=5)
    p.set_defaults(fn=cmd_run)

    p = sub.add_parser("aggregate")
    p.add_argument("--log", required=True)
    p.add_argument("--out", default=None)
    p.add_argument("--json-out", default=None)
    p.set_defaults(fn=cmd_aggregate)

    p = sub.add_parser("restore")
    p.add_argument("--manifest", required=True)
    p.add_argument("--shipped-sha", default=SHIPPED_JAR_SHA256)
    p.set_defaults(fn=cmd_restore)

    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
