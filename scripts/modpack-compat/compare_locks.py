#!/usr/bin/env python3
"""Compare two immutable CurseForge manifest locks without needing proprietary pack ZIPs."""
import argparse
import json
from pathlib import Path


def compare(left, right):
    lm = {x['projectID']: x['fileID'] for x in left['manifestMods']}
    rm = {x['projectID']: x['fileID'] for x in right['manifestMods']}
    shared = set(lm) & set(rm)
    same = {project for project in shared if lm[project] == rm[project]}
    versions = [{'projectID': p, 'leftFileID': lm[p], 'rightFileID': rm[p]}
                for p in sorted(shared - same)]
    return {'sharedProjectCount': len(shared), 'sameExactFileCount': len(same),
            'differingFileCount': len(versions), 'leftOnlyCount': len(set(lm) - set(rm)),
            'rightOnlyCount': len(set(rm) - set(lm)), 'differentFiles': versions}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('left', type=Path)
    parser.add_argument('right', type=Path)
    args = parser.parse_args()
    left = json.loads(args.left.read_text())
    right = json.loads(args.right.read_text())
    print(json.dumps(compare(left, right), indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
