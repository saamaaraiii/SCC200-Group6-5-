# Architecture Decision Record (ADR)

## ADR-001 — Canonical backend and legacy folder policy

### Status
Accepted

### Decision
For submission and ongoing development, the **canonical backend** is:

- `backend/main.py`

The following folders are retained as historical snapshots only:

- `API+SERVER_Draft/`
- `DB+API_FULL/`

These folders are deprecated and should not receive new feature work.

### Rationale
The repository contains multiple overlapping API implementations with different endpoint contracts. Defining a single canonical implementation removes ambiguity for maintainers and assessors.

### Consequences
- Documentation and test references should target `backend/main.py`.
- Legacy docs remain available for traceability, but are non-authoritative.
