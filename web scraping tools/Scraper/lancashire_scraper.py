"""
Lancashire Places Scraper — Powered by OpenStreetMap (Overpass API)
Scrapes restaurants, attractions, hotels, pubs, cafes and more in Lancashire.
Completely free. No API key. No signup. No proxies needed.

Supports incremental updates — re-run anytime to add new places and update
changed details without duplicating existing records.

Data includes: name, address, GPS coords, phone, website, cuisine,
               opening hours, rating (where available), category.

Usage:
    python lancashire_scraper.py                      # full scrape / update all
    python lancashire_scraper.py --type restaurants   # one category
    python lancashire_scraper.py --type all --output results/
    python lancashire_scraper.py --fresh              # ignore existing, start clean
"""

import json
import csv
import time
import argparse
import os
import re
import sys
import sqlite3
import glob
import math
from datetime import datetime

import requests

# ── Config ────────────────────────────────────────────────────────────────────

OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
OVERPASS_URL = OVERPASS_ENDPOINTS[0]

# Lancashire bounding box (roughly)
# south, west, north, east
LANCASHIRE_BBOX = (53.6, -2.9, 54.1, -2.1)

# Alternatively: use the named area (more precise, slower)
LANCASHIRE_AREA = "Lancashire, England"

# Category → OSM tags to query
CATEGORIES = {
    "restaurants": [
        ('amenity', 'restaurant'),
        ('amenity', 'fast_food'),
    ],
    "cafes": [
        ('amenity', 'cafe'),
        ('amenity', 'bakery'),
    ],
    "pubs": [
        ('amenity', 'pub'),
        ('amenity', 'bar'),
    ],
    "attractions": [
        ('tourism', 'attraction'),
        ('tourism', 'museum'),
        ('tourism', 'gallery'),
        ('tourism', 'theme_park'),
        ('historic', 'castle'),
        ('historic', 'monument'),
        ('leisure', 'park'),
        ('leisure', 'nature_reserve'),
    ],
    "hotels": [
        ('tourism', 'hotel'),
        ('tourism', 'motel'),
        ('tourism', 'hostel'),
        ('tourism', 'guest_house'),
        ('tourism', 'bed_and_breakfast'),
    ],
}


# ── Overpass query ────────────────────────────────────────────────────────────

def build_query(tags: list[tuple], bbox: tuple) -> str:
    """Build an Overpass QL query for a list of (key, value) tag pairs."""
    south, west, north, east = bbox
    bb = f"{south},{west},{north},{east}"
    parts = []
    for key, val in tags:
        parts.append(f'  node["{key}"="{val}"]({bb});')
        parts.append(f'  way["{key}"="{val}"]({bb});')
    query = "[out:json][timeout:60];\n(\n" + "\n".join(parts) + "\n);\nout center tags;"
    return query


def run_query(query: str, retries: int = 3) -> list[dict]:
    """Submit an Overpass query, trying multiple endpoints on failure."""
    for endpoint in OVERPASS_ENDPOINTS:
        for attempt in range(retries):
            try:
                resp = requests.post(
                    endpoint,
                    data={"data": query},
                    timeout=90,
                    headers={"User-Agent": "LancashireScraper/1.0 (educational project)"},
                )
                if resp.status_code == 200:
                    data = resp.json()
                    return data.get("elements", [])
                elif resp.status_code == 429:
                    wait = 30 * (attempt + 1)
                    print(f"  Rate limited on {endpoint}. Waiting {wait}s...")
                    time.sleep(wait)
                else:
                    print(f"  HTTP {resp.status_code} from {endpoint}, trying next...")
                    break  # Try next endpoint
            except requests.Timeout:
                print(f"  Timeout on {endpoint} (attempt {attempt + 1})...")
                time.sleep(5)
            except Exception as e:
                print(f"  Error on {endpoint}: {e}")
                break
    return []


# ── Element parser ────────────────────────────────────────────────────────────

def parse_element(el: dict, category: str) -> dict:
    """Convert a raw Overpass element to a clean dict."""
    tags = el.get("tags", {})

    # Coordinates
    if el["type"] == "node":
        lat, lon = el.get("lat"), el.get("lon")
    else:
        center = el.get("center", {})
        lat, lon = center.get("lat"), center.get("lon")

    # Build address
    addr_parts = [
        tags.get("addr:housenumber", ""),
        tags.get("addr:street", ""),
        tags.get("addr:city", "") or tags.get("addr:town", "") or tags.get("addr:village", ""),
        tags.get("addr:postcode", ""),
    ]
    address = ", ".join(p for p in addr_parts if p)

    # Opening hours — clean up slightly
    hours = tags.get("opening_hours", "")

    # Phone
    phone = tags.get("phone", "") or tags.get("contact:phone", "")

    # Website
    website = tags.get("website", "") or tags.get("contact:website", "") or tags.get("url", "")

    # Cuisine / type details
    cuisine = tags.get("cuisine", "")
    amenity = tags.get("amenity", "") or tags.get("tourism", "") or tags.get("historic", "") or tags.get("leisure", "")

    # Stars / rating (hotels)
    stars = tags.get("stars", "")

    # Wheelchair / accessibility
    wheelchair = tags.get("wheelchair", "")

    return {
        "category":     category,
        "name":         tags.get("name", "Unnamed"),
        "type":         amenity,
        "cuisine":      cuisine,
        "address":      address,
        "postcode":     tags.get("addr:postcode", ""),
        "town":         tags.get("addr:city", "") or tags.get("addr:town", "") or tags.get("addr:village", ""),
        "latitude":     lat,
        "longitude":    lon,
        "phone":        phone,
        "website":      website,
        "opening_hours": hours,
        "stars":        stars,
        "wheelchair":   wheelchair,
        "osm_id":       el.get("id"),
        "osm_type":     el["type"],
        "scraped_at":   datetime.now().isoformat(),
    }


# ── Scrape category ───────────────────────────────────────────────────────────

EMOJI = {
    "restaurants": "🍽",
    "cafes":       "☕",
    "pubs":        "🍺",
    "attractions": "🎡",
    "hotels":      "🏨",
}


def scrape_category(category: str) -> list[dict]:
    tags = CATEGORIES[category]
    print(f"\n{EMOJI.get(category,'📍')}  Scraping {category}...")
    query = build_query(tags, LANCASHIRE_BBOX)
    elements = run_query(query)

    seen_ids = set()
    results = []
    for el in elements:
        eid = el.get("id")
        if eid in seen_ids:
            continue
        seen_ids.add(eid)
        tags_data = el.get("tags", {})
        # Skip unnamed entries with no useful data
        if not tags_data.get("name") and not tags_data.get("addr:street"):
            continue
        row = parse_element(el, category)
        results.append(row)

    print(f"  ✓ Found {len(results)} {category}")
    return results


# ── Incremental update ────────────────────────────────────────────────────────

def load_existing(filepath: str) -> dict[int, dict]:
    """Load existing JSON data keyed by OSM ID."""
    if not os.path.exists(filepath):
        return {}
    with open(filepath, encoding="utf-8") as f:
        data = json.load(f)
    return {row["osm_id"]: row for row in data if row.get("osm_id")}


def merge(existing: dict[int, dict], fresh: list[dict]) -> tuple[list[dict], int, int]:
    """
    Merge fresh scraped data into existing records.
    Returns (merged_list, new_count, updated_count).
    """
    new_count = 0
    updated_count = 0
    merged = dict(existing)  # copy

    for row in fresh:
        oid = row.get("osm_id")
        if not oid:
            continue

        if oid not in merged:
            # Brand new place
            new_count += 1
            merged[oid] = row
        else:
            # Check if anything meaningful changed
            old = merged[oid]
            changed = any(
                row.get(k) != old.get(k)
                for k in ("name", "address", "phone", "website", "opening_hours", "type", "cuisine")
            )
            if changed:
                updated_count += 1
                # Preserve original first_seen, update everything else
                row["first_seen"] = old.get("first_seen", old.get("scraped_at", ""))
                row["last_updated"] = row["scraped_at"]
                merged[oid] = row
            else:
                # Just refresh the last_checked timestamp
                merged[oid]["last_checked"] = row["scraped_at"]

    # Add first_seen to any new records
    for oid, row in merged.items():
        if "first_seen" not in row:
            row["first_seen"] = row.get("scraped_at", "")

    return list(merged.values()), new_count, updated_count


# ── Output ────────────────────────────────────────────────────────────────────

def save_csv(data: list[dict], filepath: str):
    if not data:
        return
    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(data[0].keys()), extrasaction="ignore")
        writer.writeheader()
        writer.writerows(data)
    print(f"  💾 CSV: {filepath} ({len(data)} rows)")


def save_json(data: list[dict], filepath: str):
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  💾 JSON: {filepath} ({len(data)} items)")


# ── Summary ───────────────────────────────────────────────────────────────────

def print_summary(data: list[dict]):
    if not data:
        return
    print("\n📊 Summary:")
    by_cat = {}
    by_town = {}
    for row in data:
        by_cat[row["category"]] = by_cat.get(row["category"], 0) + 1
        town = row.get("town") or "Unknown"
        by_town[town] = by_town.get(town, 0) + 1

    for cat, count in sorted(by_cat.items(), key=lambda x: -x[1]):
        print(f"  {cat:15} {count:4} places")

    print("\n  Top towns:")
    for town, count in sorted(by_town.items(), key=lambda x: -x[1])[:10]:
        if town and town != "Unknown":
            print(f"    {town:20} {count:4}")


# ── Database update ───────────────────────────────────────────────────────────

DB_SCHEMA = """
CREATE TABLE IF NOT EXISTS places (
    osm_id INTEGER PRIMARY KEY, osm_type TEXT, category TEXT, type TEXT,
    name TEXT, cuisine TEXT, address TEXT, postcode TEXT, town TEXT,
    latitude REAL, longitude REAL, phone TEXT, website TEXT,
    opening_hours TEXT, stars REAL, wheelchair TEXT,
    first_seen TEXT, last_updated TEXT, last_checked TEXT, scraped_at TEXT
);
CREATE VIRTUAL TABLE IF NOT EXISTS places_fts USING fts5(
    name, cuisine, address, town, content=places, content_rowid=osm_id
);
CREATE TRIGGER IF NOT EXISTS places_ai AFTER INSERT ON places BEGIN
    INSERT INTO places_fts(rowid, name, cuisine, address, town)
    VALUES (new.osm_id, new.name, new.cuisine, new.address, new.town);
END;
CREATE TRIGGER IF NOT EXISTS places_au AFTER UPDATE ON places BEGIN
    INSERT INTO places_fts(places_fts, rowid, name, cuisine, address, town)
    VALUES ('delete', old.osm_id, old.name, old.cuisine, old.address, old.town);
    INSERT INTO places_fts(rowid, name, cuisine, address, town)
    VALUES (new.osm_id, new.name, new.cuisine, new.address, new.town);
END;
CREATE INDEX IF NOT EXISTS idx_category ON places(category);
CREATE INDEX IF NOT EXISTS idx_town     ON places(town);
CREATE INDEX IF NOT EXISTS idx_stars    ON places(stars);
"""

def update_database(data: list[dict], db_path: str):
    """Upsert scraped records into the SQLite search database."""
    conn = sqlite3.connect(db_path)
    conn.executescript(DB_SCHEMA)

    new_count = updated_count = 0
    for r in data:
        osm_id = r.get("osm_id")
        if not osm_id:
            continue
        exists = conn.execute("SELECT 1 FROM places WHERE osm_id=?", (osm_id,)).fetchone()
        row = (
            osm_id, r.get("osm_type"), r.get("category"), r.get("type"),
            r.get("name"), r.get("cuisine"), r.get("address"), r.get("postcode"),
            r.get("town"), r.get("latitude"), r.get("longitude"),
            r.get("phone"), r.get("website"), r.get("opening_hours"),
            float(r["stars"]) if r.get("stars") else None,
            r.get("wheelchair"), r.get("first_seen"), r.get("last_updated"),
            r.get("last_checked"), r.get("scraped_at"),
        )
        if not exists:
            conn.execute("INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", row)
            new_count += 1
        else:
            conn.execute("""UPDATE places SET
                osm_type=?,category=?,type=?,name=?,cuisine=?,address=?,postcode=?,
                town=?,latitude=?,longitude=?,phone=?,website=?,opening_hours=?,
                stars=?,wheelchair=?,first_seen=?,last_updated=?,last_checked=?,scraped_at=?
                WHERE osm_id=?""", row[1:] + (osm_id,))
            updated_count += 1

    conn.commit()
    total = conn.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    conn.close()
    print(f"  🗄  DB updated: {new_count} new, {updated_count} upserted — {total} total in database")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Lancashire Places Scraper (OpenStreetMap)")
    parser.add_argument(
        "--type",
        choices=list(CATEGORIES.keys()) + ["all"],
        default="all",
        help="Category to scrape (default: all)",
    )
    parser.add_argument(
        "--output",
        default="scraper_output",
        help="Output directory (default: scraper_output/)",
    )
    parser.add_argument(
        "--format",
        choices=["both", "csv", "json"],
        default="both",
    )
    parser.add_argument(
        "--fresh",
        action="store_true",
        help="Ignore existing data and start clean",
    )
    parser.add_argument(
        "--db",
        default=None,
        help="Path to SQLite database to auto-update (default: <output>/lancashire.db)",
    )
    parser.add_argument(
        "--no-db",
        action="store_true",
        help="Skip database update (just save JSON/CSV)",
    )
    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)
    cats = list(CATEGORIES.keys()) if args.type == "all" else [args.type]
    all_merged = []
    db_path = args.db or os.path.join(args.output, "lancashire.db")

    print("=" * 60)
    print("  Lancashire Places Scraper (OpenStreetMap)")
    print(f"  Mode: {'fresh' if args.fresh else 'incremental update'}")
    print(f"  Category: {args.type}")
    print(f"  Output: {args.output}/")
    if not args.no_db:
        print(f"  Database: {db_path}")
    print("=" * 60)

    for cat in cats:
        # Fixed filenames (no timestamp) so re-runs update the same file
        json_path = os.path.join(args.output, f"lancashire_{cat}.json")
        csv_path  = os.path.join(args.output, f"lancashire_{cat}.csv")

        # Load existing data unless --fresh
        existing = {} if args.fresh else load_existing(json_path)
        if existing:
            print(f"\n  📂 Loaded {len(existing)} existing {cat} records")

        # Scrape fresh data
        fresh = scrape_category(cat)

        # Merge
        merged, new_count, updated_count = merge(existing, fresh)
        all_merged.extend(merged)

        if existing:
            print(f"  🔄 {new_count} new, {updated_count} updated, {len(merged) - new_count - updated_count} unchanged")
        
        if merged:
            if args.format in ("both", "json"):
                save_json(merged, json_path)
            if args.format in ("both", "csv"):
                save_csv(merged, csv_path)
            if not args.no_db:
                update_database(merged, db_path)

        time.sleep(2)  # Polite pause between categories

    # Combined file (all categories together)
    if len(cats) > 1 and all_merged:
        if args.format in ("both", "json"):
            save_json(all_merged, os.path.join(args.output, "lancashire_all.json"))
        if args.format in ("both", "csv"):
            save_csv(all_merged,  os.path.join(args.output, "lancashire_all.csv"))

    print_summary(all_merged)
    print(f"\n✅ Done. Total records: {len(all_merged)}")
    if not args.no_db:
        print(f"🗄  Search database: {db_path}")
        # Auto-generate semantic embeddings for any new records
        try:
            import sys
            elastic_dir = os.path.join(os.path.dirname(__file__), "lancashire-elastic")
            if os.path.isdir(elastic_dir) and elastic_dir not in sys.path:
                sys.path.insert(0, elastic_dir)
            from semantic import build_embeddings
            print(f"\n🤖 Updating semantic search embeddings...")
            build_embeddings(db_path)
        except ImportError:
            pass  # semantic module not available, skip silently
        print(f"\n   Run: python lancashire-elastic/search.py --db {db_path} --stats")


if __name__ == "__main__":
    main()
