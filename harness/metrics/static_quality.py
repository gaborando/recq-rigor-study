"""Static artifact quality: LOC, CK (CBO/WMC/LCOM/RFC/DIT), PMD violations."""

from __future__ import annotations

import csv
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from ..config import REPO
from ..workspace import Workspace, sh

TOOLS = REPO / "tools"


def _loc_java(src: Path) -> tuple[int, int]:
    files = list(src.rglob("*.java"))
    loc = sum(
        sum(1 for ln in f.read_text(errors="ignore").splitlines()
            if ln.strip() and not ln.strip().startswith(("//", "*", "/*")))
        for f in files
    )
    return loc, len(files)


def _ck(src: Path, workdir: Path) -> dict | None:
    jar = TOOLS / "ck.jar"
    if not jar.exists():
        return None
    r = sh(["java", "-jar", str(jar), str(src), "false", "0", "false"],
           cwd=workdir, check=False, timeout=600)
    cls = workdir / "class.csv"
    if r.returncode != 0 or not cls.exists():
        return None
    rows = list(csv.DictReader(open(cls)))
    if not rows:
        return None

    def agg(field: str) -> dict:
        vals = [float(row[field]) for row in rows if row.get(field, "").replace(".", "").isdigit()]
        return {"mean": round(sum(vals) / len(vals), 2), "max": max(vals)} if vals else None

    return {"classes": len(rows), "cbo": agg("cbo"), "wmc": agg("wmc"),
            "lcom": agg("lcom"), "rfc": agg("rfc"), "dit": agg("dit")}


def _pmd(src: Path) -> int | None:
    pmd = next(TOOLS.glob("pmd-bin-*/bin/pmd"), None) or shutil.which("pmd")
    if not pmd:
        return None
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
        out = tf.name
    r = subprocess.run([str(pmd), "check", "-d", str(src), "-R", "category/java/design.xml",
                        "-f", "json", "-r", out, "--no-progress"],
                       capture_output=True, text=True, timeout=600)
    try:
        data = json.loads(Path(out).read_text())
        return sum(len(f.get("violations", [])) for f in data.get("files", []))
    except Exception:
        return None


def compute(w: Workspace) -> dict:
    src = w.dir / "src" / "main" / "java"
    out: dict = {"measured": False}
    if src.exists():
        loc, files = _loc_java(src)
        out.update(measured=True, loc_java=loc, files_java=files)
        with tempfile.TemporaryDirectory() as td:
            ck = _ck(src, Path(td))
        if ck:
            out.update(ck)
        pmd = _pmd(src)
        out["pmd_violations"] = pmd
        if pmd is not None and loc:
            out["pmd_violations_per_kloc"] = round(pmd * 1000 / loc, 2)
    (w.root / "metrics" / "static.json").write_text(json.dumps(out, indent=2))
    return out
