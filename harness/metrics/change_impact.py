"""T2 change impact: diff between the T2 baseline (the seeded T1 tree) and the final tree."""

from __future__ import annotations

import json
import subprocess

from ..workspace import Workspace


def compute(w: Workspace, seeded_from: str | None) -> dict:
    out = {"seeded_from_run_id": seeded_from, "skipped_t1_failed": False,
           "files_touched": None, "diff_loc_added": None, "diff_loc_deleted": None}
    if w.cell.task != "t2":
        return out
    r = subprocess.run(["git", "log", "--format=%H", "--reverse"], cwd=w.dir,
                       capture_output=True, text=True)
    commits = r.stdout.strip().splitlines()
    if len(commits) >= 1:
        base = commits[0]  # baseline = seeded T1 tree + new spec/tests
        numstat = subprocess.run(
            ["git", "diff", "--numstat", base, "HEAD", "--", "src/"],
            cwd=w.dir, capture_output=True, text=True).stdout
        added = deleted = files = 0
        for line in numstat.strip().splitlines():
            parts = line.split("\t")
            if len(parts) == 3 and parts[0].isdigit() and parts[1].isdigit():
                added += int(parts[0])
                deleted += int(parts[1])
                files += 1
        out.update(files_touched=files, diff_loc_added=added, diff_loc_deleted=deleted)
    (w.root / "metrics" / "change_impact.json").write_text(json.dumps(out, indent=2))
    return out
