# SCC200 Group 6-5 — Submission Readiness Repo

This repository contains the Group 6-5 coursework project artifacts for SCC200.

## What is the canonical implementation?

- **Canonical backend:** `backend/main.py`
- **Canonical backend docs/tests:** `backend/README.md`, `backend/test/test_api_python.py`
- **iOS app (Phase 1):** `TrainApp/TrainApp/...`

Legacy draft folders are preserved for reference only and are marked deprecated:
- `API+SERVER_Draft/`
- `DB+API_FULL/`

See `ARCHITECTURE_DECISION.md` and `PROJECT_STATUS.md`.

---

## Quick start (backend)

### Option A: one command bootstrap

```bash
./scripts/bootstrap_backend.sh
```

This creates a virtualenv, installs dependencies, and runs tests.

### Option B: manual

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

Backend default URL: `http://127.0.0.1:8000`

---

## Run tests

```bash
./scripts/test_backend.sh
```

Expected: backend API tests pass (`backend/test/test_api_python.py`).

---

## Repository map

- `backend/` — active Flask backend and tests
- `TrainApp/` — iOS SwiftUI app (Phase 1 pathfinding UI)
- `web scraping tools/` — data scraping/search assets
- `docs/` — architecture + API contract notes
- `scripts/` — bootstrap/test convenience scripts
- `API+SERVER_Draft/`, `DB+API_FULL/` — archived/deprecated reference implementations

---

## Current project state

A concise done/partial/missing matrix is maintained in:
- `PROJECT_STATUS.md`

---

## Notes for assessors/demo

- This repo intentionally preserves legacy drafts for traceability.
- Submission/demo should target **canonical backend + current iOS app**.
- If functionality appears duplicated, prefer `backend/main.py` implementation.
