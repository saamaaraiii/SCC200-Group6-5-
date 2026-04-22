# Demo Runbook (Current)

## 1) Start backend

```bash
./scripts/bootstrap_backend.sh
# or separately:
source .venv/bin/activate
python backend/main.py
```

Expected: Flask server running on `http://127.0.0.1:8000`.

## 2) Quick API smoke checks

```bash
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/api/endpoints
curl "http://127.0.0.1:8000/api/routes/find?from=Preston&to=Lancaster"
```

## 3) User → journey → ticket flow (demo-safe)

1. Register user: `POST /api/users/register`
2. Login: `POST /api/users/login`
3. Create journey: `POST /api/journeys`
4. Create ticket: `POST /api/tickets`
5. Create payment: `POST /api/payments`

## 4) iOS app demo (Phase 1)

Open:
`TrainApp/TrainApp/TrainApp.xcodeproj`

Demonstrate:
- station selection
- BFS vs Dijkstra
- route output

## Notes

- Legacy API folders are deprecated and not used for live demo.
- Current demo is backend-complete + iOS Phase 1 pathfinding; full iOS backend integration is next milestone.
