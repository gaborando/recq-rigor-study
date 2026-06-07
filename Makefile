# Thin verbs over the harness. Everything is config-driven (config/*.yaml).

.PHONY: setup tools pilot matrix analyze leakcheck clean

setup:            ## install python deps
	uv sync

tools:            ## fetch pinned CK / PMD / cloc
	./tools/fetch_tools.sh

pilot:            ## run the pilot profile (SPENDS API TOKENS)
	uv run python -m harness run --profile pilot

# Run a single cell, e.g.:
#   make cell CELL="--domain order-inventory --arm arm_b_spring --model claude-sonnet --task t1"
cell:
	uv run python -m harness run $(CELL)

matrix:           ## run the FULL matrix (COST GATE — read config/matrix.yaml first)
	uv run python -m harness run --profile full

analyze:          ## aggregate runs/ -> results.csv -> stats, plots, LaTeX tables
	uv run python -m analysis.aggregate
	uv run python -m analysis.stats
	uv run python -m analysis.plots
	uv run python -m analysis.tables

leakcheck:        ## assert no variant-suite content leaked into any persisted workspace
	uv run python -m harness leakcheck

clean:            ## tear down any leftover per-run docker compose projects
	uv run python -m harness clean
