#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -d .venv ]]; then
  echo "[test] .venv missing, creating quickly..."
  python3 -m venv .venv
fi

source .venv/bin/activate
python -m pip install -q -r backend/requirements.txt
python -m unittest -v backend/test/test_api_python.py
