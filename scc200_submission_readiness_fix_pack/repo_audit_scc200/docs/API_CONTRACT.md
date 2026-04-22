# API Contract (Canonical)

Authoritative source: `backend/main.py`

## Core endpoints (selected)

### Health & discovery
- `GET /health`
- `GET /api/endpoints`

### Station / departures / bus / weather
- `GET /api/lancs_fac`
- `GET /api/departures`
- `GET /api/departures/search`
- `GET /api/bus/live`
- `GET /api/bus/routes`
- `GET /api/weather`

### Routing
- `GET /api/stations`
- `GET /api/routes/find`

### Users / journeys / tickets / payments
- `POST /api/users/register`
- `POST /api/users/login`
- `GET /api/users/<user_id>`
- `POST /api/journeys`
- `GET /api/journeys/<journey_id>`
- `POST /api/tickets`
- `GET /api/tickets/<ticket_id>`
- `POST /api/payments`
- `GET /api/payments/<payment_id>`

### Disruption / delay claims
- `POST /api/delay_claims`
- `GET /api/delay_claims/<claim_id>`
- `POST /api/disruption_reports`
- `GET /api/disruption_reports`

### Taxi support
- `GET /api/taxi/ranks`
- `GET /api/taxi/providers`
- `POST /api/taxi/quote`
- `POST /api/taxi/bookings`

## Notes

- If an endpoint appears in legacy docs but not here, treat it as non-canonical/deprecated.
- For detailed parameter/response behavior, inspect handler logic in `backend/main.py` and backend tests.
