"""
Lancashire Places Connector — SCC200 Group 6-5
===============================================
Extends the existing api_server.py (DB_Scc REST API) with places/experiences
endpoints powered by the Lancashire SQLite database and semantic search engine.

DROP-IN: Copy this file alongside api_server.py and add one line to api_server.py:
    from places_connector import register_places_routes
    register_places_routes(app)

Then the following endpoints are live:
    GET /api/places/nearby          — places near Lancaster station (or any lat/lon)
    GET /api/places/search          — keyword search
    GET /api/places/semantic        — AI semantic search ("romantic dinner", "family day out")
    GET /api/places/categories      — list all categories
    GET /api/places/stats           — database summary

Use case: When a user plans a journey to Lancaster, the app can show
          restaurants, hotels, attractions and pubs near the destination.

Lancaster station coordinates: 54.0466, -2.8007
"""

import sqlite3
import math
import os
import json
import hashlib
import time
from typing import Optional
from flask import request, jsonify

# ── Config ────────────────────────────────────────────────────────────────────

# Path to the Lancashire SQLite database
# Adjust this path to wherever lancashire.db lives on your server
_HERE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get(
    "LANCASHIRE_DB",
    os.path.join(_HERE, "..", "..", "lancashire-elastic", "lancashire.db")
)

# Lancaster station coordinates (default centre point for nearby searches)
LANCASTER_LAT = 54.0466
LANCASTER_LON = -2.8007

# Simple in-memory query cache (avoids re-running identical searches)
_cache: dict = {}
CACHE_TTL = 300  # seconds


# ── Helpers ───────────────────────────────────────────────────────────────────

def _get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def _cache_key(*args) -> str:
    return hashlib.md5(json.dumps(args, sort_keys=True).encode()).hexdigest()


def _cached(key: str):
    entry = _cache.get(key)
    if entry and time.time() - entry["ts"] < CACHE_TTL:
        return entry["data"]
    return None


def _set_cache(key: str, data):
    _cache[key] = {"ts": time.time(), "data": data}


def haversine(lat1, lon1, lat2, lon2) -> float:
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _rows_to_list(rows, limit=None) -> list:
    result = [dict(r) for r in rows]
    return result[:limit] if limit else result


def _fields_filter(rows: list, fields: Optional[str]) -> list:
    """If ?fields=name,town,... is provided, strip other keys."""
    if not fields:
        return rows
    keep = {f.strip() for f in fields.split(",")}
    return [{k: v for k, v in r.items() if k in keep} for r in rows]


# ── Route handlers ─────────────────────────────────────────────────────────────

def places_nearby():
    """
    GET /api/places/nearby
    Find places within a radius of a point.

    Query params:
        lat      float  Latitude  (default: Lancaster station 54.0466)
        lon      float  Longitude (default: Lancaster station -2.8007)
        radius   float  Radius in km (default: 1.0)
        category str    Filter: restaurants / cafes / pubs / hotels / attractions
        limit    int    Max results (default: 20)
        fields   str    Comma-separated fields to return
    """
    try:
        lat      = request.args.get("lat",      LANCASTER_LAT, type=float)
        lon      = request.args.get("lon",      LANCASTER_LON, type=float)
        radius   = request.args.get("radius",   1.0,           type=float)
        category = request.args.get("category", None,          type=str)
        limit    = request.args.get("limit",    20,            type=int)
        fields   = request.args.get("fields",   None,          type=str)

        cache_key = _cache_key("nearby", lat, lon, radius, category, limit)
        cached = _cached(cache_key)
        if cached:
            return jsonify(cached), 200

        # Bounding box pre-filter (fast), then exact haversine
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

        db = _get_db()
        rows = db.execute(sql, params).fetchall()
        db.close()

        results = []
        for r in rows:
            d = haversine(lat, lon, r["latitude"], r["longitude"])
            if d <= radius:
                row = dict(r)
                row["distance_km"] = round(d, 3)
                results.append(row)

        results.sort(key=lambda x: x["distance_km"])
        results = results[:limit]
        results = _fields_filter(results, fields)

        payload = {
            "status": "success",
            "lat": lat, "lon": lon, "radius_km": radius,
            "count": len(results),
            "data": results
        }
        _set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


def places_search():
    """
    GET /api/places/search?q=pizza
    Keyword full-text search across name, cuisine, address, town.

    Query params:
        q        str  Search query (required)
        category str  Optional category filter
        town     str  Optional town filter
        limit    int  Max results (default: 20)
        fields   str  Fields to return
    """
    try:
        q        = request.args.get("q",        "",   type=str)
        category = request.args.get("category", None, type=str)
        town     = request.args.get("town",     None, type=str)
        limit    = request.args.get("limit",    20,   type=int)
        fields   = request.args.get("fields",   None, type=str)

        if not q:
            return jsonify({"status": "error", "message": "q parameter required"}), 400

        cache_key = _cache_key("search", q, category, town, limit)
        cached = _cached(cache_key)
        if cached:
            return jsonify(cached), 200

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

        db = _get_db()
        rows = db.execute(sql, params).fetchall()
        db.close()

        results = _fields_filter(_rows_to_list(rows), fields)
        payload = {
            "status": "success",
            "query": q,
            "count": len(results),
            "data": results
        }
        _set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


def places_semantic():
    """
    GET /api/places/semantic?q=romantic dinner for two
    AI semantic search — understands meaning, not just keywords.

    Examples:
        "family day out with kids"   → attractions, parks
        "cheap lunch near station"   → cafes, fast food
        "traditional British pub"    → pubs
        "budget hotel"               → hotels
        "things to do outdoors"      → parks, nature reserves

    Query params:
        q         str    Natural language query (required)
        category  str    Optional category filter
        limit     int    Max results (default: 20)
        threshold float  Min relevance score 0-1 (default: 0.2)
        fields    str    Fields to return
    """
    try:
        q         = request.args.get("q",         "",   type=str)
        category  = request.args.get("category",  None, type=str)
        limit     = request.args.get("limit",     20,   type=int)
        threshold = request.args.get("threshold", 0.2,  type=float)
        fields    = request.args.get("fields",    None, type=str)

        if not q:
            return jsonify({"status": "error", "message": "q parameter required"}), 400

        # Import semantic module (lazy — only loads model when first called)
        try:
            import sys
            elastic_dir = os.path.join(_HERE, "..", "..", "lancashire-elastic")
            if os.path.isdir(elastic_dir) and elastic_dir not in sys.path:
                sys.path.insert(0, elastic_dir)
            from semantic import semantic_search
        except ImportError:
            return jsonify({
                "status": "error",
                "message": "Semantic search unavailable. Install: pip install sentence-transformers"
            }), 503

        cache_key = _cache_key("semantic", q, category, limit, threshold)
        cached = _cached(cache_key)
        if cached:
            return jsonify(cached), 200

        results = semantic_search(
            db_path=DB_PATH,
            query=q,
            category=category,
            limit=limit,
            threshold=threshold,
        )
        results = _fields_filter(results, fields)

        payload = {
            "status": "success",
            "query": q,
            "mode": "semantic",
            "count": len(results),
            "data": results
        }
        _set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


def places_categories():
    """GET /api/places/categories — list all categories with counts."""
    try:
        db = _get_db()
        rows = db.execute(
            "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
        ).fetchall()
        db.close()
        return jsonify({
            "status": "success",
            "data": _rows_to_list(rows)
        }), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


def places_stats():
    """GET /api/places/stats — summary of the database."""
    try:
        cache_key = _cache_key("stats")
        cached = _cached(cache_key)
        if cached:
            return jsonify(cached), 200

        db = _get_db()
        total = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        by_cat = _rows_to_list(db.execute(
            "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
        ).fetchall())
        top_towns = _rows_to_list(db.execute(
            "SELECT town, COUNT(*) as count FROM places WHERE town != '' GROUP BY town ORDER BY count DESC LIMIT 10"
        ).fetchall())
        db.close()

        payload = {
            "status": "success",
            "data": {
                "total_places": total,
                "by_category": by_cat,
                "top_towns": top_towns,
            }
        }
        _set_cache(cache_key, payload)
        return jsonify(payload), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


# ── Registration ──────────────────────────────────────────────────────────────

def register_places_routes(app):
    """
    Call this from api_server.py to mount all places routes:

        from places_connector import register_places_routes
        register_places_routes(app)
    """
    app.add_url_rule("/api/places/nearby",     "places_nearby",     places_nearby,     methods=["GET"])
    app.add_url_rule("/api/places/search",     "places_search",     places_search,     methods=["GET"])
    app.add_url_rule("/api/places/semantic",   "places_semantic",   places_semantic,   methods=["GET"])
    app.add_url_rule("/api/places/categories", "places_categories", places_categories, methods=["GET"])
    app.add_url_rule("/api/places/stats",      "places_stats",      places_stats,      methods=["GET"])

    print("[places_connector] Routes registered:")
    print("  GET /api/places/nearby     — places near a location")
    print("  GET /api/places/search     — keyword search")
    print("  GET /api/places/semantic   — AI semantic search")
    print("  GET /api/places/categories — category list")
    print("  GET /api/places/stats      — database summary")
