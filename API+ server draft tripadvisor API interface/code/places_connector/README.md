# Places Connector

Connects the Lancashire places database (restaurants, cafes, pubs, hotels, attractions) directly into the existing `api_server.py`.

## How to integrate (2 lines)

Open `api_server.py` and add after the Flask app is created:

```python
from places_connector import register_places_routes
register_places_routes(app)
```

That's it. Five new endpoints are immediately live.

## New Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/places/nearby` | Places near Lancaster station (or any lat/lon) |
| `GET /api/places/search?q=pizza` | Keyword search |
| `GET /api/places/semantic?q=romantic dinner` | AI semantic search |
| `GET /api/places/categories` | All categories with counts |
| `GET /api/places/stats` | Database summary |

## Example calls

```bash
# Restaurants within 500m of Lancaster station
GET /api/places/nearby?category=restaurants&radius=0.5

# Search for Indian food
GET /api/places/search?q=Indian&town=Lancaster

# AI search — understands natural language
GET /api/places/semantic?q=family day out with kids
GET /api/places/semantic?q=cheap lunch near station
GET /api/places/semantic?q=traditional British pub

# Only return specific fields (reduces payload size)
GET /api/places/nearby?radius=1&fields=name,category,address,distance_km,phone
```

## Setup

1. Copy `places_connector.py` alongside `api_server.py`
2. Set the database path (optional — defaults work if folder structure matches):
   ```bash
   export LANCASHIRE_DB=/path/to/lancashire.db
   ```
3. Add to `requirements.txt`:
   ```
   sentence-transformers  # only needed for /api/places/semantic
   ```

## Why this matters for the app

When a user plans a journey to Lancaster, the app can show:
- **Restaurants near the station** on arrival
- **Hotels** if they're staying overnight  
- **Things to do** (attractions, parks, museums) at the destination
- **Pubs and cafes** for a quick stop

The semantic search is especially useful — a user could type *"somewhere to eat with kids"* and get genuinely relevant results, not just keyword matches.

## Caching

All responses are cached in memory for 5 minutes. Identical queries within that window return instantly without hitting the database.
