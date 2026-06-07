"""PERFORMANCE KPI: fixed k6 workload + memory sampling.

k6: local binary if on PATH (version recorded), else the pinned docker image
(linux replication path). Memory: host `ps -o rss=` sampling of the app JVM
+ docker stats for the infra containers + actuator JVM heap.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import threading
import time

import httpx

from ..config import REPO
from ..workspace import Workspace, sh


def _k6_cmd(w: Workspace, summary: str) -> list[str]:
    script = REPO / "perf" / w.cell.domain / "workload.js"
    envs = {"BASE_URL": f"http://localhost:{w.app_port}",
            "WARMUP": f"{w.cell.perf['warmup_seconds']}s",
            "DURATION": f"{w.cell.perf['duration_seconds']}s",
            "VUS": str(w.cell.perf["vus"])}
    if shutil.which("k6"):
        cmd = ["k6", "run", "--summary-export", summary]
        for k, v in envs.items():
            cmd += ["-e", f"{k}={v}"]
        return cmd + [str(script)]
    # docker fallback (linux): host networking
    image = "grafana/k6:0.57.0"
    cmd = ["docker", "run", "--rm", "--network", "host",
           "-v", f"{script.parent}:/scripts:ro", "-v", f"{w.root / 'reports'}:/out"]
    for k, v in envs.items():
        cmd += ["-e", f"{k}={v}"]
    return cmd + [image, "run", "--summary-export", "/out/k6-summary.json", "/scripts/workload.js"]


class _MemSampler(threading.Thread):
    def __init__(self, pids: list[int], compose_project: str):
        super().__init__(daemon=True)
        self.pids = pids  # one (single) or several (micro: all service JVMs)
        self.project = compose_project
        self.app_rss: list[float] = []
        self.infra_mb: list[float] = []
        self.stop_flag = threading.Event()

    def run(self) -> None:
        while not self.stop_flag.is_set():
            if self.pids:
                total = 0.0
                for pid in self.pids:
                    r = subprocess.run(["ps", "-o", "rss=", "-p", str(pid)],
                                       capture_output=True, text=True)
                    if r.returncode == 0 and r.stdout.strip():
                        total += int(r.stdout.strip()) / 1024.0  # KiB -> MiB
                if total:
                    self.app_rss.append(total)
            r = subprocess.run(
                ["docker", "stats", "--no-stream", "--format", "{{.Name}} {{.MemUsage}}"],
                capture_output=True, text=True)
            total = 0.0
            for line in r.stdout.strip().splitlines():
                if not line.startswith(self.project):
                    continue
                usage = line.split()[1]  # e.g. 123.4MiB
                val = float("".join(c for c in usage if c.isdigit() or c == "."))
                total += val * (1024 if "GiB" in usage else 1)
            if total:
                self.infra_mb.append(total)
            self.stop_flag.wait(5)


def _jvm_heap_mb(port: int) -> float | None:
    try:
        r = httpx.get(f"http://localhost:{port}/actuator/metrics/jvm.memory.used",
                      params={"tag": "area:heap"}, timeout=5)
        if r.status_code == 200:
            return round(r.json()["measurements"][0]["value"] / (1024 * 1024), 1)
    except Exception:
        pass
    return None


def _endpoint_stats(summary: dict, endpoint: str) -> dict | None:
    m = summary.get("metrics", {}).get(
        f"http_req_duration{{endpoint:{endpoint},phase:load}}") or \
        summary.get("metrics", {}).get(f"http_req_duration{{endpoint:{endpoint}}}")
    if not m:
        return None
    return {"endpoint": endpoint, "p50_ms": m.get("med", 0.0),
            "p95_ms": m.get("p(95)", 0.0), "p99_ms": m.get("p(99)", m.get("p(95)", 0.0)),
            "rps": 0.0, "error_rate": 0.0}


def measure(w: Workspace, app_pids: list[int] | int | None) -> dict:
    pids = app_pids if isinstance(app_pids, list) else ([app_pids] if app_pids else [])
    summary_path = w.root / "reports" / "k6-summary.json"
    sampler = _MemSampler(pids, w.compose_project)
    sampler.start()
    started = time.monotonic()
    r = sh(_k6_cmd(w, str(summary_path)), cwd=REPO, check=False, timeout=3600)
    elapsed = time.monotonic() - started
    sampler.stop_flag.set()
    sampler.join(timeout=10)
    (w.root / "logs" / "k6.log").write_text(r.stdout + "\n--- stderr ---\n" + r.stderr)

    out: dict = {"measured": False, "skip_reason": None, "endpoints": [],
                 "k6_wall_seconds": round(elapsed, 1)}
    if not summary_path.exists():
        out["skip_reason"] = f"k6 produced no summary (exit {r.returncode})"
    else:
        s = json.loads(summary_path.read_text())
        dur = s.get("metrics", {}).get("http_req_duration", {})
        reqs = s.get("metrics", {}).get("http_reqs", {})
        fails = s.get("metrics", {}).get("http_req_failed", {})
        out.update(
            measured=True,
            overall_p95_ms=dur.get("p(95)"),
            overall_rps=reqs.get("rate"),
            overall_error_rate=fails.get("value"),
            endpoints=[e for e in (
                _endpoint_stats(s, "place_order"), _endpoint_stats(s, "get_order"),
                _endpoint_stats(s, "get_product"), _endpoint_stats(s, "get_stats"),
            ) if e],
        )
    out["mem_app_rss_mb_peak"] = round(max(sampler.app_rss), 1) if sampler.app_rss else None
    out["mem_app_rss_mb_avg"] = round(sum(sampler.app_rss) / len(sampler.app_rss), 1) \
        if sampler.app_rss else None
    out["mem_jvm_heap_mb"] = _jvm_heap_mb(w.app_port)
    infra_peak = max(sampler.infra_mb) if sampler.infra_mb else 0.0
    if out["mem_app_rss_mb_peak"] is not None:
        out["mem_total_stack_mb"] = round(out["mem_app_rss_mb_peak"] + infra_peak, 1)
    (w.root / "metrics" / "perf.json").write_text(json.dumps(out, indent=2))
    return out
