# PROJECT_STATUS

Last updated: 2026-04-22

## Submission-readiness snapshot

| Subsystem | Status | Evidence | What remains |
|---|---|---|---|
| Backend API (`backend/main.py`) | **Partial (strong)** | Health/endpoints/routing/user/journey/ticket/payment/disruption handlers implemented | Keep docs/tests aligned with canonical backend only |
| Backend tests | **Partial** | `backend/test/test_api_python.py` exists and runs | Add CI (now added), extend edge-case coverage |
| iOS app (Phase 1) | **Done (Phase 1)** | Local SQLite + parser + BFS/Dijkstra + basic search UI | Phase 2 integration with backend APIs |
| iOS↔Backend integration | **Missing** | No networking layer in `TrainApp/TrainApp` | Build API client + booking/account flow screens |
| Data pipeline / scraper assets | **Partial** | `web scraping tools/` includes DB + search + scrape outputs | Clarify source-of-truth in docs |
| Repository clarity | **Improved, still partial** | README + ADR + API contract added | Remove or archive duplicate legacy docs over time |
| CI/CD | **Done (baseline)** | `.github/workflows/backend-tests.yml` | Add lint/type checks if desired |

## What is now canonical

- Backend: `backend/main.py`
- API contract reference: `docs/API_CONTRACT.md`
- Legacy folders are **deprecated**:
  - `API+SERVER_Draft/`
  - `DB+API_FULL/`

## High-priority next steps (ordered)

1. Implement minimal iOS networking client for auth + route + ticket/payment calls.
2. Create one demo runbook with deterministic scenario and expected outputs.
3. Trim stale/conflicting docs in legacy folders (or move to `archive/`).
4. Expand backend tests for failure paths (invalid payloads, missing IDs, edge cases).

## Definition of done (near-term)

- [x] Canonical backend declared
- [x] Root README rewritten with working setup
- [x] Bootstrap/test scripts added
- [x] CI backend tests added
- [x] Deprecation notices added to legacy folders
- [ ] iOS backend integration slice complete
- [ ] End-to-end demo script complete
