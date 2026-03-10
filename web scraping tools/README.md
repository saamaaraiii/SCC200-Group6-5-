# Lancashire Places — Search Database

Pure Python search and analytics over **3,130 places** across Lancashire. No Java, no Docker, no server — just Python and SQLite.

## Quick Start

```bash
# 1. Build the database from scraped data
python build_db.py --data ../lancashire-scraper/output

# 2. Search
python search.py --query "pizza"
python search.py --query "Italian" --category restaurants --town Preston
python search.py --category hotels --min-stars 4
python search.py --near 54.04,-2.80 --radius 5
python search.py --stats
```

## Features

- **Full-text search** — searches name, cuisine, address, town
- **Geo queries** — find places within X km of any location
- **Filters** — by category, town, cuisine, star rating
- **Stats & aggregations** — breakdowns by town, category, cuisine
- **Interactive mode** — just run `python search.py` with no args

## Example Queries

```bash
# Text search
python search.py --query "Indian restaurant"
python search.py --query "pizza" --town Lancaster

# Filters
python search.py --category restaurants --town Blackburn
python search.py --category hotels --min-stars 4
python search.py --category pubs --town Preston

# Geo search (lat,lon)
python search.py --near 53.75,-2.48 --radius 2   # near Blackburn
python search.py --near 54.04,-2.80 --radius 5   # near Lancaster

# Stats
python search.py --stats
```

## Files

| File | Description |
|------|-------------|
| `build_db.py` | Builds/updates the SQLite database from scraped JSON |
| `search.py` | Search and analytics CLI |
| `lancashire.db` | Pre-built database (3,130 places, ready to use) |
| `docker-compose.yml` | Optional: full Elasticsearch + Kibana stack |
| `index.py` | Optional: index into Elasticsearch instead |

## Re-running

Safe to re-run `build_db.py` at any time after a scraper update — upserts by OSM ID:
```bash
python build_db.py           # incremental update
python build_db.py --fresh   # full rebuild
```

---

## REST API

```bash
pip install fastapi uvicorn
python api.py --db ../scraper_output/lancashire.db
```

API runs at **http://localhost:8000** — interactive docs at **http://localhost:8000/docs**

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/places` | List/filter places |
| GET | `/places/search?q=pizza` | Full-text search |
| GET | `/places/nearby?lat=54.04&lon=-2.80&radius=5` | Places within radius |
| GET | `/places/{osm_id}` | Single place by ID |
| GET | `/stats` | Database statistics |
| GET | `/categories` | All categories with counts |
| GET | `/towns` | All towns with counts |
| GET | `/cuisines` | All cuisines with counts |

### Example calls

```bash
# Search
curl "http://localhost:8000/places/search?q=Italian&town=Lancaster"

# Filter
curl "http://localhost:8000/places?category=restaurants&town=Preston&limit=20"

# Nearby (Lancaster city centre, 5km)
curl "http://localhost:8000/places/nearby?lat=54.04&lon=-2.80&radius=5"

# Hotels 4+ stars
curl "http://localhost:8000/places?category=hotels&min_stars=4"

# Stats
curl "http://localhost:8000/stats"
```
