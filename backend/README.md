# Python Backend

Primary backend entrypoint: `backend/main.py`

## Run

```bash
python backend/main.py
```

Server default: `http://localhost:5000`

## Test

```bash
python -m unittest -v backend/test/test_api_python.py
```

## Endpoint Groups

- Health and discovery: `/health`, `/api/endpoints`
- Station facilities: `/api/lancs_fac/*`
- Train and bus live data: `/api/departures*`, `/api/bus/*`
- Delay and weather: `/api/delay_codes*`, `/api/weather`, `/api/predictions/delay`
- Places (TripAdvisor-style): `/api/places/*`
- Taxi: `/api/taxi/*`
- Journey planning: `/api/stations`, `/api/routes/find`
- Accounts and ticketing: `/api/users/*`, `/api/journeys*`, `/api/tickets*`, `/api/payments*`, `/api/delay_claims*`, `/api/disruption_reports*`
- Operator/vehicle data: `/api/operators`, `/api/vehicles/*`
