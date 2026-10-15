#!/usr/bin/env python3
"""Fail closed on frames that were never presented; do not report bogus FPS."""
import json, pathlib, re, sys
if len(sys.argv) != 2:
    raise SystemExit('usage: bench_validity_gate.py <run-directory>')
root = pathlib.Path(sys.argv[1]); report = root/'logs/celtium-validation.json'; log = root/'validation-console.log'
if not report.is_file() or not log.is_file():
    raise SystemExit('INVALID: missing fixture report or console log')
data = json.loads(report.read_text()); text = log.read_text(errors='replace')
counts = [int(x) for x in re.findall(r'CELTIUM_PRESENT hookCalls=\d+ attempts=\d+ presented=(\d+)', text)]
frames = max(counts, default=0)
world = data.get('worldFrames', 0)
image = data.get('pictureFile_world')
image_ok = bool(image and (root/image).is_file())
errors=[]
process_file = root/'validation-process.json'
process = json.loads(process_file.read_text()) if process_file.is_file() else {}
if process.get('fixtureStatus') != 'PASSED': errors.append('fixture status='+str(process.get('fixtureStatus')))
if process.get('validationClean') is not True: errors.append('Vulkan validation not clean')
if process.get('gpuRequirementMet') is not True: errors.append('GPU mismatch or unverified')
if not world: errors.append('no world frames')
if frames < 5: errors.append(f'presented frames={frames} (<5)')
if not image_ok: errors.append(f'presented image missing: {image}')
if data.get('frameIntervalSampleCount',0)<30: errors.append('insufficient frame samples')
if errors:
    print('INVALID BENCHMARK:', '; '.join(errors)); sys.exit(2)
print('VALID PRESENTATION EVIDENCE', 'presented>=',frames,'world=',world,'p50/p95/p99=',*[data.get('frameInterval'+p+'Ms') for p in ('P50','P95','P99')])
