#!/usr/bin/env python3
"""Claude pilot sweep status. Run:  python3 runs/status.py   (from repo root)"""
import json, glob, os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
runs = sorted(glob.glob(os.path.join(ROOT, "runs", "*claude-opus*t1*")))  # Opus-only sweep

done, inflight = [], []
for d in runs:
    (done if os.path.exists(os.path.join(d, "run.json")) else inflight).append(d)

print("=== COMPLETED CELLS ===")
if not done:
    print("  (none graded yet)")
for d in done:
    r = json.load(open(os.path.join(d, "run.json")))
    c, fn, b = r["cell"], r["functional"], r["budget_spent"]
    acc = f"{fn['acceptance_passed']}/{fn['acceptance_total']}" if fn['acceptance_total'] else "—/—"
    cost = f"{b['cost_usd']:.2f}" if b['cost_usd'] is not None else "?"
    print(f"  {c['domain']:16}{c['arm']:14} "
          f"build={str(fn['builds_cleanly']):5} boot={str(fn['boots_cleanly']):5} "
          f"acc={acc:<7} turns={b['turns']} cost=${cost}")

print("\n=== IN FLIGHT ===")
if not inflight:
    print("  (none)")
for d in inflight:
    t = os.path.join(d, "agent", "transcript.jsonl")
    tools, last = {}, ""
    if os.path.exists(t):
        for line in open(t):
            try: ev = json.loads(line)
            except Exception: continue
            if ev.get("type") == "assistant":
                for blk in (ev.get("message") or {}).get("content") or []:
                    if blk.get("type") == "tool_use":
                        tools[blk["name"]] = tools.get(blk["name"], 0) + 1
                    if blk.get("type") == "text" and blk.get("text", "").strip():
                        last = blk["text"].strip()
    n = subprocess.run(["git", "-C", os.path.join(d, "workspace"), "rev-list", "--count", "HEAD"],
                       capture_output=True, text=True).stdout.strip() or "?"
    print(f"  {os.path.basename(d)}")
    print(f"    tools={dict(sorted(tools.items(), key=lambda x:-x[1]))}  commits={n}")
    if last: print(f"    last: {last[:160]}")

print("\n=== PROCS ===")
ps = subprocess.run(["pgrep", "-fl", "harness run"], capture_output=True, text=True).stdout
ps += subprocess.run(["pgrep", "-fl", "_pilot_queue"], capture_output=True, text=True).stdout
print(ps.strip() or "  (no background harness/queue running)")
print(f"\n{len(done)}/9 cells complete.")
