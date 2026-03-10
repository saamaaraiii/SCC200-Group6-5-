"""
Lancashire Places — Search & Analytics

Pure Python search over the SQLite database.
Supports full-text search, filters, geo distance, and aggregations.

Usage:
    python search.py                               # interactive mode
    python search.py --query "pizza"
    python search.py --query "Italian" --category restaurants --town Preston
    python search.py --near 54.04,-2.80 --radius 5
    python search.py --category hotels --min-stars 4
    python search.py --stats
"""

import sqlite3
import argparse
import math
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "lancashire.db")


# ── DB connection ─────────────────────────────────────────────────────────────

def connect(db_path: str) -> sqlite3.Connection:
    if not os.path.exists(db_path):
        raise FileNotFoundError(
            f"Database not found: {db_path}\n"
            "Run build_db.py first."
        )
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


# ── Geo helper ────────────────────────────────────────────────────────────────

def haversine(lat1, lon1, lat2, lon2) -> float:
    """Distance in km between two lat/lon points."""
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat/2)**2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon/2)**2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ── Display ───────────────────────────────────────────────────────────────────

def display(rows, limit=10):
    if not rows:
        print("  No results found.")
        return
    print(f"  Found {len(rows)} result(s)\n")
    for r in rows[:limit]:
        print(f"  📍 {r['name']}  [{r['category']}]")
        if r['address']:    print(f"     {r['address']}")
        if r['town']:       print(f"     Town: {r['town']}  {r['postcode'] or ''}")
        if r['cuisine']:    print(f"     Cuisine: {r['cuisine']}")
        if r['stars']:      print(f"     Stars: {'⭐' * int(r['stars'])} ({r['stars']})")
        if r['phone']:      print(f"     📞 {r['phone']}")
        if r['website']:    print(f"     🌐 {r['website']}")
        if r['opening_hours']: print(f"     🕐 {r['opening_hours']}")
        if r.get('distance'): print(f"     📏 {r['distance']:.1f} km away")
        print()
    if len(rows) > limit:
        print(f"  ... and {len(rows) - limit} more")


# ── Search functions ──────────────────────────────────────────────────────────

def text_search(conn, query: str, category=None, town=None, limit=20) -> list:
    """Full-text search using FTS5."""
    sql = """
        SELECT p.* FROM places p
        JOIN places_fts f ON f.rowid = p.osm_id
        WHERE places_fts MATCH ?
    """
    params = [query]
    if category:
        sql += " AND p.category = ?"
        params.append(category)
    if town:
        sql += " AND LOWER(p.town) LIKE ?"
        params.append(f"%{town.lower()}%")
    sql += f" ORDER BY rank LIMIT {limit}"
    return [dict(r) for r in conn.execute(sql, params).fetchall()]


def filter_search(conn, category=None, town=None, min_stars=None,
                  cuisine=None, has_phone=False, has_website=False, limit=50) -> list:
    """Filter-based search (no text query)."""
    conditions = []
    params = []
    if category:
        conditions.append("category = ?")
        params.append(category)
    if town:
        conditions.append("LOWER(town) LIKE ?")
        params.append(f"%{town.lower()}%")
    if min_stars:
        conditions.append("stars >= ?")
        params.append(min_stars)
    if cuisine:
        conditions.append("LOWER(cuisine) LIKE ?")
        params.append(f"%{cuisine.lower()}%")
    if has_phone:
        conditions.append("phone IS NOT NULL AND phone != ''")
    if has_website:
        conditions.append("website IS NOT NULL AND website != ''")

    where = ("WHERE " + " AND ".join(conditions)) if conditions else ""
    sql = f"SELECT * FROM places {where} ORDER BY name LIMIT {limit}"
    return [dict(r) for r in conn.execute(sql, params).fetchall()]


def geo_search(conn, lat: float, lon: float, radius_km: float, category=None, limit=20) -> list:
    """Find places within a radius using bounding box + haversine filter."""
    # Rough bounding box first (fast), then exact haversine
    deg_lat = radius_km / 111.0
    deg_lon = radius_km / (111.0 * math.cos(math.radians(lat)))

    sql = """
        SELECT * FROM places
        WHERE latitude BETWEEN ? AND ?
          AND longitude BETWEEN ? AND ?
          AND latitude IS NOT NULL
    """
    params = [lat - deg_lat, lat + deg_lat, lon - deg_lon, lon + deg_lon]
    if category:
        sql += " AND category = ?"
        params.append(category)

    rows = conn.execute(sql, params).fetchall()

    # Exact distance filter + sort
    results = []
    for r in rows:
        d = haversine(lat, lon, r["latitude"], r["longitude"])
        if d <= radius_km:
            row = dict(r)
            row["distance"] = d
            results.append(row)

    results.sort(key=lambda x: x["distance"])
    return results[:limit]


def stats(conn):
    """Print summary statistics."""
    print("\n📊 Database Statistics")
    print("=" * 50)

    total = conn.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    print(f"\n  Total places: {total}\n")

    print("  By category:")
    for row in conn.execute(
        "SELECT category, COUNT(*) as n FROM places GROUP BY category ORDER BY n DESC"
    ):
        print(f"    {row['category']:20} {row['n']:5}")

    print("\n  Top 10 towns:")
    for row in conn.execute(
        "SELECT town, COUNT(*) as n FROM places WHERE town != '' GROUP BY town ORDER BY n DESC LIMIT 10"
    ):
        print(f"    {row['town']:25} {row['n']:4}")

    print("\n  Top cuisines:")
    for row in conn.execute(
        "SELECT cuisine, COUNT(*) as n FROM places WHERE cuisine != '' GROUP BY cuisine ORDER BY n DESC LIMIT 10"
    ):
        print(f"    {row['cuisine']:25} {row['n']:4}")

    hotels_with_stars = conn.execute(
        "SELECT COUNT(*) FROM places WHERE stars IS NOT NULL"
    ).fetchone()[0]
    print(f"\n  Hotels with star rating: {hotels_with_stars}")

    with_phone = conn.execute(
        "SELECT COUNT(*) FROM places WHERE phone != '' AND phone IS NOT NULL"
    ).fetchone()[0]
    with_web = conn.execute(
        "SELECT COUNT(*) FROM places WHERE website != '' AND website IS NOT NULL"
    ).fetchone()[0]
    print(f"  With phone number:       {with_phone}")
    print(f"  With website:            {with_web}")


# ── Interactive mode ──────────────────────────────────────────────────────────

def interactive(conn):
    print("\n🔍 Lancashire Places Search")
    print("   Type a search term, or 'quit' to exit")
    print("   Examples: 'pizza', 'Indian Preston', 'castle Lancaster'\n")

    while True:
        try:
            query = input("Search > ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not query or query.lower() in ("quit", "exit", "q"):
            break
        results = text_search(conn, query)
        display(results)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Search the Lancashire places database")
    parser.add_argument("--db",         default=DB_PATH)
    parser.add_argument("--query",      help="Full-text search query")
    parser.add_argument("--category",   help="Filter by category (restaurants/cafes/pubs/hotels/attractions)")
    parser.add_argument("--town",       help="Filter by town")
    parser.add_argument("--cuisine",    help="Filter by cuisine")
    parser.add_argument("--min-stars",  type=float, help="Minimum star rating (hotels)")
    parser.add_argument("--near",       help="Lat,lon — find places near this point e.g. 54.04,-2.80")
    parser.add_argument("--radius",     type=float, default=5.0, help="Radius in km (default: 5)")
    parser.add_argument("--stats",      action="store_true", help="Show database statistics")
    parser.add_argument("--limit",      type=int, default=10)
    args = parser.parse_args()

    conn = connect(args.db)

    if args.stats:
        stats(conn)

    elif args.near:
        lat, lon = map(float, args.near.split(","))
        print(f"\n📍 Places within {args.radius}km of {lat},{lon}")
        print("-" * 50)
        results = geo_search(conn, lat, lon, args.radius, args.category, args.limit)
        display(results, args.limit)

    elif args.query:
        print(f"\n🔍 Results for '{args.query}'")
        if args.category: print(f"   Category: {args.category}")
        if args.town:     print(f"   Town: {args.town}")
        print("-" * 50)
        results = text_search(conn, args.query, args.category, args.town, args.limit)
        display(results, args.limit)

    elif any([args.category, args.town, args.cuisine, args.min_stars]):
        print(f"\n🔍 Filtered results")
        print("-" * 50)
        results = filter_search(conn, args.category, args.town,
                                args.min_stars, args.cuisine, limit=args.limit)
        display(results, args.limit)

    else:
        interactive(conn)

    conn.close()


if __name__ == "__main__":
    main()
