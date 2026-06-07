"""Harness CLI:  python -m harness run --profile pilot
                python -m harness run --domain ... --arm ... --model ... --task t1
                python -m harness leakcheck | clean"""

from __future__ import annotations

import argparse
import sys

from .config import RUNS, BudgetCfg, CellSpec, expand_profile, load


def cmd_run(args: argparse.Namespace) -> int:
    if args.profile:
        cells = expand_profile(args.profile)
    else:
        if not all([args.domain, args.arm, args.model, args.task]):
            print("either --profile or all of --domain/--arm/--model/--task", file=sys.stderr)
            return 2
        d = load("matrix")["defaults"]
        cells = [CellSpec(args.domain, args.arm, args.model, args.task, args.rep,
                          BudgetCfg(**d["budget"]), d["perf"])]
    print(f"{len(cells)} run(s) planned:")
    for c in cells:
        print(f"  {c.domain} × {c.arm} × {c.model} × {c.task} rep{c.rep}")
    if not args.yes:
        ans = input("These runs SPEND API TOKENS. Proceed? [y/N] ")
        if ans.strip().lower() != "y":
            return 1
    from .runner import run_cell  # late import: heavy deps
    failures = 0
    for c in cells:
        try:
            run_cell(c)
        except Exception as e:  # a failed run is data, but a crashed harness is a bug
            failures += 1
            print(f"RUN CRASHED for {c}: {e}", file=sys.stderr)
    return 1 if failures else 0


def cmd_leakcheck(_: argparse.Namespace) -> int:
    """No variant-suite content may appear in any persisted workspace/transcript."""
    bad = []
    for run_dir in sorted(RUNS.iterdir()):
        if not run_dir.is_dir():
            continue
        for probe in ["test_variant.py"]:
            if list(run_dir.rglob(probe)):
                bad.append(f"{run_dir.name}: contains {probe}")
        t = run_dir / "agent" / "transcript.jsonl"
        if t.exists() and "variant/order-inventory" in t.read_text(errors="ignore"):
            bad.append(f"{run_dir.name}: transcript references the variant suite")
    if bad:
        print("LEAKS FOUND:\n" + "\n".join(bad))
        return 1
    print("leakcheck OK")
    return 0


def cmd_clean(_: argparse.Namespace) -> int:
    import subprocess
    out = subprocess.run(["docker", "compose", "ls", "--format", "json"],
                         capture_output=True, text=True).stdout
    import json as _json
    try:
        projects = [p["Name"] for p in _json.loads(out) if p["Name"].startswith("run-")]
    except Exception:
        projects = []
    for p in projects:
        print(f"tearing down {p}")
        subprocess.run(["docker", "compose", "-p", p, "down", "-v"], check=False)
    print(f"{len(projects)} leftover project(s) cleaned")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(prog="harness")
    sub = ap.add_subparsers(dest="cmd", required=True)

    rp = sub.add_parser("run")
    rp.add_argument("--profile")
    rp.add_argument("--domain")
    rp.add_argument("--arm")
    rp.add_argument("--model")
    rp.add_argument("--task")
    rp.add_argument("--rep", type=int, default=1)
    rp.add_argument("--yes", action="store_true", help="skip the spend confirmation")
    rp.set_defaults(fn=cmd_run)

    sub.add_parser("leakcheck").set_defaults(fn=cmd_leakcheck)
    sub.add_parser("clean").set_defaults(fn=cmd_clean)

    args = ap.parse_args()
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
