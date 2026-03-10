"""
Lancashire Places — SQLite Database Builder

Pure Python alternative to Elasticsearch. Builds a fast SQLite database
with full-text search, filtering, and geo queries — no Java, no Docker, no server.

Requirements: None (uses Python standard library only)

Usage:
    python build_db.py               # build/update database from scraped JSON
    python build_db.py --fresh       # delete and rebuild from scratch
    python build_db.py --db custom.db
"""

import sqlite3
import json
import os
import glob
import argparse
import math
from datetime import datetime

DB_PATH  = os.path.join(os.path.dirname(__file__), "lancashire.db")
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "lancashire-scraper", "output")


# ── Schema ────────────────────────────────────────────────────────────────────

SCHEMA = """
CREATE TABLE IF NOT EXISTS places (
    osm_id          INTEGER PRIMARY KEY,
    osm_type        TEXT,
    category        TEXT,
    type            TEXT,
    name            TEXT,
    cuisine         TEXT,
    address         TEXT,
    postcode        TEXT,
    town            TEXT,
    latitude        REAL,
    longitude       REAL,
    phone           TEXT,
    website         TEXT,
    opening_hours   TEXT,
    stars           REAL,
    wheelchair      TEXT,
    first_seen      TEXT,
    last_updated    TEXT,
    last_checked    TEXT,
    scraped_at      TEXT
);

-- Full-text search index (searches name, cuisine, address, town)
CREATE VIRTUAL TABLE IF NOT EXISTS places_fts USING fts5(
    name,
    cuisine,
    address,
    town,
    content=places,
    content_rowid=osm_id
);

-- Triggers to keep FTS in sync
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

-- Useful indexes for filtering
CREATE INDEX IF NOT EXISTS idx_category  ON places(category);
CREATE INDEX IF NOT EXISTS idx_town      ON places(town);
CREATE INDEX IF NOT EXISTS idx_postcode  ON places(postcode);
CREATE INDEX IF NOT EXISTS idx_stars     ON places(stars);
"""


# ── Build ─────────────────────────────────────────────────────────────────────

def build(db_path: str, data_dir: str, fresh: bool):
    if fresh and os.path.exists(db_path):
        os.remove(db_path)
        print(f"  🗑  Deleted existing database")

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)

    files = sorted(f for f in glob.glob(os.path.join(data_dir, "lancashire_*.json"))
                   if "_all" not in f)

    if not files:
        print(f"⚠️  No data files found in {data_dir}")
        print("   Run lancashire_scraper.py first.")
        conn.close()
        return

    total_new = total_updated = 0

    for filepath in files:
        cat = os.path.basename(filepath).replace("lancashire_", "").replace(".json", "")
        with open(filepath, encoding="utf-8") as f:
            records = json.load(f)

        new_count = updated_count = 0
        for r in records:
            osm_id = r.get("osm_id")
            if not osm_id:
                continue

            existing = conn.execute(
                "SELECT osm_id FROM places WHERE osm_id = ?", (osm_id,)
            ).fetchone()

            row = (
                osm_id,
                r.get("osm_type"),
                r.get("category"),
                r.get("type"),
                r.get("name"),
                r.get("cuisine"),
                r.get("address"),
                r.get("postcode"),
                r.get("town"),
                r.get("latitude"),
                r.get("longitude"),
                r.get("phone"),
                r.get("website"),
                r.get("opening_hours"),
                float(r["stars"]) if r.get("stars") else None,
                r.get("wheelchair"),
                r.get("first_seen"),
                r.get("last_updated"),
                r.get("last_checked"),
                r.get("scraped_at"),
            )

            if not existing:
                conn.execute("""
                    INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, row)
                new_count += 1
            else:
                conn.execute("""
                    UPDATE places SET
                        osm_type=?, category=?, type=?, name=?, cuisine=?,
                        address=?, postcode=?, town=?, latitude=?, longitude=?,
                        phone=?, website=?, opening_hours=?, stars=?, wheelchair=?,
                        first_seen=?, last_updated=?, last_checked=?, scraped_at=?
                    WHERE osm_id=?
                """, row[1:] + (osm_id,))
                updated_count += 1

        conn.commit()
        total_new += new_count
        total_updated += updated_count
        print(f"  ✓ {cat:15} {new_count:4} new  {updated_count:4} updated")

    total = conn.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    conn.close()

    print(f"\n  💾 Database: {db_path}")
    print(f"  📊 Total records: {total} ({total_new} new, {total_updated} updated)")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Build Lancashire SQLite database")
    parser.add_argument("--db",    default=DB_PATH)
    parser.add_argument("--data",  default=DATA_DIR)
    parser.add_argument("--fresh", action="store_true")
    args = parser.parse_args()

    print("=" * 60)
    print("  Lancashire Places — Database Builder")
    print(f"  Mode: {'fresh' if args.fresh else 'incremental update'}")
    print(f"  DB:   {args.db}")
    print("=" * 60)

    build(args.db, args.data, args.fresh)
    print("\n✅ Done. Run search.py to query the database.")


if __name__ == "__main__":
    main()
