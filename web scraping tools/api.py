"""
Lancashire Places — REST API

FastAPI server exposing the SQLite search database as a REST API.
Auto-generated docs available at http://localhost:8000/docs

Requirements:
    pip install fastapi uvicorn

Usage:
    python api.py
    python api.py --db ../scraper_output/lancashire.db --port 8000
    uvicorn api:app --reload   (dev mode with auto-reload)
"""

import sqlite3
import math
import os
import argparse
from typing import Optional
from contextlib import contextmanager

from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from semantic import semantic_search, build_embeddings, get_model

# ── Config ────────────────────────────────────────────────────────────────────

DEFAULT_DB = os.path.join(os.path.dirname(__file__), "..", "scraper_output", "lancashire.db")

app = FastAPI(
    title="Lancashire Places API",
    description="""
Search and explore 3,100+ restaurants, cafes, pubs, hotels and attractions across Lancashire.

**Two search modes:**
- `/places/search` — keyword search (fast, exact matches)
- `/places/semantic` — AI semantic search (understands meaning, e.g. *"romantic dinner"*, *"family day out"*, *"cheap lunch"*)
    """,
    version="2.0.0",
)

# Allow all origins (so the API can be called from any frontend)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

DB_PATH = DEFAULT_DB


# ── DB helpers ────────────────────────────────────────────────────────────────

def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def rows_to_list(rows) -> list[dict]:
    return [dict(r) for r in rows]


# ── Geo ───────────────────────────────────────────────────────────────────────

def haversine(lat1, lon1, lat2, lon2) -> float:
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ── Routes ────────────────────────────────────────────────────────────────────

@app.on_event("startup")
async def startup():
    """Pre-load embedding model and build any missing embeddings on startup."""
    if os.path.exists(DB_PATH):
        get_model()
        build_embeddings(DB_PATH)


@app.get("/", tags=["Info"])
def root():
    """API info and available endpoints."""
    return {
        "name": "Lancashire Places API",
        "version": "1.0.0",
        "docs": "/docs",
        "endpoints": {
            "GET /places":           "List/filter places",
            "GET /places/search":    "Keyword search",
            "GET /places/semantic":  "AI semantic search (understands meaning)",
            "GET /places/nearby":    "Find places within a radius",
            "GET /places/{osm_id}":  "Get a single place by ID",
            "GET /stats":            "Database statistics",
            "GET /categories":       "List all categories",
            "GET /towns":            "List all towns with counts",
            "GET /cuisines":         "List all cuisines with counts",
        }
    }


@app.get("/places", tags=["Places"])
def list_places(
    category:  Optional[str]  = Query(None, description="Filter by category: restaurants, cafes, pubs, hotels, attractions"),
    town:      Optional[str]  = Query(None, description="Filter by town (partial match)"),
    cuisine:   Optional[str]  = Query(None, description="Filter by cuisine (partial match)"),
    postcode:  Optional[str]  = Query(None, description="Filter by postcode (partial match)"),
    min_stars: Optional[float]= Query(None, description="Minimum star rating (hotels)"),
    has_phone: bool           = Query(False, description="Only return places with a phone number"),
    has_website: bool         = Query(False, description="Only return places with a website"),
    limit:     int            = Query(50,  ge=1, le=500, description="Max results (default 50, max 500)"),
    offset:    int            = Query(0,   ge=0,          description="Pagination offset"),
):
    """
    List and filter places. Combine any filters together.

    Examples:
    - `/places?category=restaurants&town=Preston`
    - `/places?cuisine=italian&limit=20`
    - `/places?category=hotels&min_stars=4`
    """
    conditions, params = [], []

    if category:
        conditions.append("category = ?")
        params.append(category.lower())
    if town:
        conditions.append("LOWER(town) LIKE ?")
        params.append(f"%{town.lower()}%")
    if cuisine:
        conditions.append("LOWER(cuisine) LIKE ?")
        params.append(f"%{cuisine.lower()}%")
    if postcode:
        conditions.append("LOWER(postcode) LIKE ?")
        params.append(f"%{postcode.lower()}%")
    if min_stars is not None:
        conditions.append("stars >= ?")
        params.append(min_stars)
    if has_phone:
        conditions.append("phone IS NOT NULL AND phone != ''")
    if has_website:
        conditions.append("website IS NOT NULL AND website != ''")

    where = ("WHERE " + " AND ".join(conditions)) if conditions else ""
    db = get_db()

    total = db.execute(f"SELECT COUNT(*) FROM places {where}", params).fetchone()[0]
    rows  = db.execute(
        f"SELECT * FROM places {where} ORDER BY name LIMIT ? OFFSET ?",
        params + [limit, offset]
    ).fetchall()
    db.close()

    return {
        "total":   total,
        "limit":   limit,
        "offset":  offset,
        "results": rows_to_list(rows),
    }


@app.get("/places/search", tags=["Places"])
def search_places(
    q:        str           = Query(..., description="Search query (name, cuisine, address, town)"),
    category: Optional[str] = Query(None, description="Narrow to a specific category"),
    town:     Optional[str] = Query(None, description="Narrow to a specific town"),
    limit:    int           = Query(20, ge=1, le=200),
):
    """
    Full-text search across name, cuisine, address and town.

    Examples:
    - `/places/search?q=pizza`
    - `/places/search?q=Indian&town=Blackburn`
    - `/places/search?q=castle&category=attractions`
    """
    sql = """
        SELECT p.* FROM places p
        JOIN places_fts f ON f.rowid = p.osm_id
        WHERE places_fts MATCH ?
    """
    params = [q]

    if category:
        sql += " AND p.category = ?"
        params.append(category.lower())
    if town:
        sql += " AND LOWER(p.town) LIKE ?"
        params.append(f"%{town.lower()}%")

    sql += f" ORDER BY rank LIMIT {limit}"

    try:
        db = get_db()
        rows = db.execute(sql, params).fetchall()
        db.close()
    except sqlite3.OperationalError as e:
        raise HTTPException(status_code=400, detail=f"Search error: {e}")

    return {
        "query":   q,
        "total":   len(rows),
        "results": rows_to_list(rows),
    }


@app.get("/places/semantic", tags=["Places"])
def semantic_search_endpoint(
    q:         str           = Query(..., description="Natural language query — describe what you're looking for"),
    category:  Optional[str] = Query(None, description="Narrow to a specific category"),
    town:      Optional[str] = Query(None, description="Narrow to a specific town"),
    limit:     int           = Query(20, ge=1, le=100),
    threshold: float         = Query(0.2, ge=0.0, le=1.0, description="Minimum relevance score (0-1). Lower = more results."),
):
    """
    **AI semantic search** — understands the *meaning* of your query, not just keywords.

    Unlike keyword search, this finds places based on concept and context:

    - `"romantic dinner for two"` → upscale restaurants
    - `"family day out with kids"` → museums, theme parks, parks
    - `"cheap lunch"` → cafes, fast food, budget spots
    - `"traditional English pub"` → pubs with character
    - `"coffee and cake"` → cafes, bakeries
    - `"things to do outdoors"` → parks, nature reserves, attractions
    - `"somewhere quiet to work"` → cafes with good atmosphere
    - `"budget hotel"` → affordable accommodation

    Results include a `relevance_score` (0-1) showing how well each place matches.
    """
    if not os.path.exists(DB_PATH):
        raise HTTPException(status_code=503, detail="Database not found")

    results = semantic_search(
        db_path=DB_PATH,
        query=q,
        category=category,
        town=town,
        limit=limit,
        threshold=threshold,
    )
    return {
        "query":     q,
        "mode":      "semantic",
        "total":     len(results),
        "results":   results,
    }


@app.get("/places/nearby", tags=["Places"])
def nearby_places(
    lat:      float         = Query(..., description="Latitude  e.g. 54.0466"),
    lon:      float         = Query(..., description="Longitude e.g. -2.8007"),
    radius:   float         = Query(5.0, description="Radius in km (default 5)"),
    category: Optional[str] = Query(None, description="Filter by category"),
    limit:    int           = Query(20, ge=1, le=200),
):
    """
    Find places within a radius of a lat/lon point.

    Examples:
    - `/places/nearby?lat=54.04&lon=-2.80&radius=5`         (near Lancaster)
    - `/places/nearby?lat=53.75&lon=-2.48&radius=3&category=restaurants`  (near Blackburn)
    """
    # Bounding box pre-filter
    deg_lat = radius / 111.0
    deg_lon = radius / (111.0 * math.cos(math.radians(lat)))

    sql = """
        SELECT * FROM places
        WHERE latitude  BETWEEN ? AND ?
          AND longitude BETWEEN ? AND ?
          AND latitude IS NOT NULL
    """
    params = [lat - deg_lat, lat + deg_lat, lon - deg_lon, lon + deg_lon]

    if category:
        sql += " AND category = ?"
        params.append(category.lower())

    db = get_db()
    rows = db.execute(sql, params).fetchall()
    db.close()

    # Exact haversine filter + sort by distance
    results = []
    for r in rows:
        d = haversine(lat, lon, r["latitude"], r["longitude"])
        if d <= radius:
            row = dict(r)
            row["distance_km"] = round(d, 3)
            results.append(row)

    results.sort(key=lambda x: x["distance_km"])

    return {
        "lat":      lat,
        "lon":      lon,
        "radius_km": radius,
        "total":    len(results),
        "results":  results[:limit],
    }


@app.get("/places/{osm_id}", tags=["Places"])
def get_place(osm_id: int):
    """Get a single place by its OSM ID."""
    db = get_db()
    row = db.execute("SELECT * FROM places WHERE osm_id = ?", (osm_id,)).fetchone()
    db.close()
    if not row:
        raise HTTPException(status_code=404, detail=f"Place {osm_id} not found")
    return dict(row)


@app.get("/stats", tags=["Analytics"])
def stats():
    """Overall database statistics and breakdowns."""
    db = get_db()

    total = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]

    by_category = rows_to_list(db.execute(
        "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
    ).fetchall())

    top_towns = rows_to_list(db.execute(
        "SELECT town, COUNT(*) as count FROM places WHERE town != '' GROUP BY town ORDER BY count DESC LIMIT 15"
    ).fetchall())

    top_cuisines = rows_to_list(db.execute(
        "SELECT cuisine, COUNT(*) as count FROM places WHERE cuisine != '' GROUP BY cuisine ORDER BY count DESC LIMIT 15"
    ).fetchall())

    with_phone   = db.execute("SELECT COUNT(*) FROM places WHERE phone IS NOT NULL AND phone != ''").fetchone()[0]
    with_website = db.execute("SELECT COUNT(*) FROM places WHERE website IS NOT NULL AND website != ''").fetchone()[0]
    with_hours   = db.execute("SELECT COUNT(*) FROM places WHERE opening_hours IS NOT NULL AND opening_hours != ''").fetchone()[0]
    with_stars   = db.execute("SELECT COUNT(*) FROM places WHERE stars IS NOT NULL").fetchone()[0]
    db.close()

    return {
        "total_places":   total,
        "by_category":    by_category,
        "top_towns":      top_towns,
        "top_cuisines":   top_cuisines,
        "with_phone":     with_phone,
        "with_website":   with_website,
        "with_hours":     with_hours,
        "with_stars":     with_stars,
    }


@app.get("/categories", tags=["Analytics"])
def categories():
    """List all available categories with counts."""
    db = get_db()
    rows = db.execute(
        "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
    ).fetchall()
    db.close()
    return rows_to_list(rows)


@app.get("/towns", tags=["Analytics"])
def towns(limit: int = Query(50, ge=1, le=500)):
    """List all towns with place counts."""
    db = get_db()
    rows = db.execute(
        "SELECT town, COUNT(*) as count FROM places WHERE town != '' GROUP BY town ORDER BY count DESC LIMIT ?",
        (limit,)
    ).fetchall()
    db.close()
    return rows_to_list(rows)


@app.get("/cuisines", tags=["Analytics"])
def cuisines():
    """List all cuisines with counts."""
    db = get_db()
    rows = db.execute(
        "SELECT cuisine, COUNT(*) as count FROM places WHERE cuisine != '' GROUP BY cuisine ORDER BY count DESC"
    ).fetchall()
    db.close()
    return rows_to_list(rows)


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    parser = argparse.ArgumentParser(description="Lancashire Places API")
    parser.add_argument("--db",   default=DEFAULT_DB,  help="Path to SQLite database")
    parser.add_argument("--host", default="0.0.0.0",   help="Host (default: 0.0.0.0)")
    parser.add_argument("--port", default=8000, type=int, help="Port (default: 8000)")
    args = parser.parse_args()

    DB_PATH = args.db
    if not os.path.exists(DB_PATH):
        print(f"⚠️  Database not found: {DB_PATH}")
        print("   Run lancashire_scraper.py first to generate it.")
        exit(1)

    print(f"🚀 Lancashire Places API")
    print(f"   Database: {DB_PATH}")
    print(f"   Docs:     http://{args.host}:{args.port}/docs")
    uvicorn.run(app, host=args.host, port=args.port)
