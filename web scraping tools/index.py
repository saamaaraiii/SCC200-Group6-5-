"""
Lancashire Places — Elasticsearch Indexer

Reads scraped JSON data and indexes it into Elasticsearch with:
- Proper field mappings (geo_point for coordinates, keyword for filtering)
- Full-text search on name, address, cuisine
- Fast filtering by category, town, rating, cuisine

Requirements:
    pip install elasticsearch

Usage:
    # Start Elasticsearch first (see docker-compose.yml or README)
    python index.py                          # index all categories
    python index.py --category restaurants   # one category only
    python index.py --host http://localhost:9200
    python index.py --fresh                  # delete & recreate index first
"""

import json
import os
import argparse
import glob
from datetime import datetime
from elasticsearch import Elasticsearch, helpers

# ── Config ────────────────────────────────────────────────────────────────────

INDEX_NAME = "lancashire_places"
DEFAULT_HOST = "http://localhost:9200"
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "lancashire-scraper", "output")

# ── Index mapping ─────────────────────────────────────────────────────────────

MAPPING = {
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
            "analyzer": {
                "english_analyzer": {
                    "type": "english"
                }
            }
        }
    },
    "mappings": {
        "properties": {
            "name": {
                "type": "text",
                "analyzer": "english_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"}
                }
            },
            "category": {"type": "keyword"},
            "type":     {"type": "keyword"},
            "cuisine":  {
                "type": "text",
                "fields": {"keyword": {"type": "keyword"}}
            },
            "address":  {"type": "text"},
            "postcode": {"type": "keyword"},
            "town": {
                "type": "text",
                "fields": {"keyword": {"type": "keyword"}}
            },
            "location": {"type": "geo_point"},   # lat/lon for map/distance queries
            "phone":    {"type": "keyword"},
            "website":  {"type": "keyword"},
            "opening_hours": {"type": "text"},
            "stars":    {"type": "float"},
            "wheelchair": {"type": "keyword"},
            "first_seen":   {"type": "date"},
            "last_updated": {"type": "date"},
            "last_checked": {"type": "date"},
            "scraped_at":   {"type": "date"},
            "osm_id":   {"type": "long"},
            "osm_type": {"type": "keyword"},
        }
    }
}


# ── Helpers ───────────────────────────────────────────────────────────────────

def connect(host: str) -> Elasticsearch:
    es = Elasticsearch(host)
    if not es.ping():
        raise ConnectionError(f"Cannot reach Elasticsearch at {host}\nIs it running? Try: docker compose up -d")
    info = es.info()
    print(f"✅ Connected to Elasticsearch {info['version']['number']} at {host}")
    return es


def setup_index(es: Elasticsearch, fresh: bool):
    if fresh and es.indices.exists(index=INDEX_NAME):
        es.indices.delete(index=INDEX_NAME)
        print(f"  🗑  Deleted existing index '{INDEX_NAME}'")

    if not es.indices.exists(index=INDEX_NAME):
        es.indices.create(index=INDEX_NAME, body=MAPPING)
        print(f"  📋 Created index '{INDEX_NAME}' with mappings")
    else:
        print(f"  📋 Index '{INDEX_NAME}' already exists — will upsert")


def load_json(filepath: str) -> list[dict]:
    with open(filepath, encoding="utf-8") as f:
        return json.load(f)


def to_doc(record: dict) -> dict:
    """Convert a scraped record to an Elasticsearch document."""
    doc = dict(record)

    # Build geo_point from lat/lon
    lat = doc.pop("latitude", None)
    lon = doc.pop("longitude", None)
    if lat is not None and lon is not None:
        try:
            doc["location"] = {"lat": float(lat), "lon": float(lon)}
        except (TypeError, ValueError):
            pass

    # Convert stars to float
    if doc.get("stars"):
        try:
            doc["stars"] = float(doc["stars"])
        except (TypeError, ValueError):
            doc.pop("stars", None)

    # Remove empty strings (cleaner in Kibana)
    doc = {k: v for k, v in doc.items() if v not in ("", None)}

    return doc


def index_file(es: Elasticsearch, filepath: str, category: str):
    records = load_json(filepath)
    print(f"\n  📂 Indexing {len(records)} {category} records from {os.path.basename(filepath)}...")

    actions = [
        {
            "_index": INDEX_NAME,
            "_id": str(r.get("osm_id", i)),   # stable ID = no duplicates on re-index
            "_source": to_doc(r),
        }
        for i, r in enumerate(records)
    ]

    success, errors = helpers.bulk(es, actions, raise_on_error=False, chunk_size=200)
    print(f"  ✓ {success} indexed", end="")
    if errors:
        print(f", {len(errors)} errors")
        for e in errors[:3]:
            print(f"    {e}")
    else:
        print()
    return success


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Lancashire Places Elasticsearch Indexer")
    parser.add_argument("--host",     default=DEFAULT_HOST, help="Elasticsearch URL")
    parser.add_argument("--category", default="all",        help="Category to index (default: all)")
    parser.add_argument("--data",     default=DATA_DIR,     help="Path to scraped JSON files")
    parser.add_argument("--fresh",    action="store_true",  help="Delete & recreate index")
    args = parser.parse_args()

    print("=" * 60)
    print("  Lancashire Places — Elasticsearch Indexer")
    print(f"  Host: {args.host}")
    print(f"  Mode: {'fresh' if args.fresh else 'upsert (safe re-run)'}")
    print("=" * 60)

    es = connect(args.host)
    setup_index(es, args.fresh)

    # Find data files
    if args.category == "all":
        pattern = os.path.join(args.data, "lancashire_*.json")
        files = [f for f in glob.glob(pattern) if "_all" not in f]
    else:
        files = glob.glob(os.path.join(args.data, f"lancashire_{args.category}.json"))

    if not files:
        print(f"\n⚠️  No data files found in {args.data}")
        print("   Run lancashire_scraper.py first to generate data.")
        return

    total = 0
    for filepath in sorted(files):
        cat = os.path.basename(filepath).replace("lancashire_", "").replace(".json", "")
        total += index_file(es, filepath, cat)

    # Refresh so data is immediately searchable
    es.indices.refresh(index=INDEX_NAME)
    count = es.count(index=INDEX_NAME)["count"]

    print(f"\n✅ Done. {total} documents indexed. Total in index: {count}")
    print(f"\n🔍 Try it:")
    print(f"   curl '{args.host}/{INDEX_NAME}/_search?q=pizza&pretty'")
    print(f"   curl '{args.host}/{INDEX_NAME}/_search?q=category:hotels&pretty'")
    print(f"\n📊 Kibana dashboard: http://localhost:5601")


if __name__ == "__main__":
    main()
