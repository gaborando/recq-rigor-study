"""Variant-suite conftest: reuse the acceptance suite's fixtures/helpers.

Loaded from an explicit file path under a distinct module name so it can never
collide with this conftest itself, and so the variant suite needs no PYTHONPATH.
"""

import importlib.util
import sys
from pathlib import Path

_acceptance = Path(__file__).resolve().parents[2] / "acceptance" / "flight" / "conftest.py"
_spec = importlib.util.spec_from_file_location("acceptance_conftest", _acceptance)
_mod = importlib.util.module_from_spec(_spec)
sys.modules["acceptance_conftest"] = _mod
_spec.loader.exec_module(_mod)

# Re-export every public name (fixtures included — pytest discovers them here).
globals().update({k: v for k, v in vars(_mod).items() if not k.startswith("_")})
