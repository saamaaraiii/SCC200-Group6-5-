"""
Lancashire Places — Elasticsearch Search Examples

Demonstrates common queries you can run against the index.

Usage:
    python search_examples.py
    python search_examples.py --query "Italian restaurant Preston"
"""

import json
import argparse
from elasticsearch import Elasticsearch

ES_HOST = "http://localhost:9200"
INDEX   = "lancashire_places"


def pp(results):
    """Pretty-print search hits."""
    hits = results["hits"]["hits"]
    total = results["hits"]["total"]["value"]
    print(f"  Found {total} results (showing {len(hits)})\n")
    for h in hits:
        s = h["_source"]
        loc = s.get("location", {})
        print(f"  🏠 {s.get('name','?')} [{s.get('category','?')}]")
        print(f"     {s.get('address','No address')} | {s.get('town','')}")
        if s.get("cuisine"):     print(f"     Cuisine: {s['cuisine']}")
        if s.get("stars"):       print(f"     Stars: {s['stars']}")
        if s.get("phone"):       print(f"     Phone: {s['phone']}")
        if s.get("website"):     print(f"     Web: {s['website']}")
        if s.get("opening_hours"): print(f"     Hours: {s['opening_hours']}")
        print()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", default=None)
    parser.add_argument("--host", default=ES_HOST)
    args = parser.parse_args()

    es = Elasticsearch(args.host)

    if args.query:
        # ── Free-text search ──────────────────────────────────────────────────
        print(f"\n🔍 Free-text search: '{args.query}'")
        print("-" * 50)
        results = es.search(index=INDEX, body={
            "query": {
                "multi_match": {
                    "query": args.query,
                    "fields": ["name^3", "cuisine^2", "address", "town"]
                }
            },
            "size": 10
        })
        pp(results)
        return

    # ── Example 1: All restaurants in Preston ────────────────────────────────
    print("\n🍽  Restaurants in Preston")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "query": {
            "bool": {
                "must":   [{"term": {"category": "restaurants"}}],
                "filter": [{"term": {"town.keyword": "Preston"}}]
            }
        },
        "size": 5
    })
    pp(results)

    # ── Example 2: Italian cuisine anywhere in Lancashire ────────────────────
    print("\n🇮🇹  Italian restaurants in Lancashire")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "query": {
            "bool": {
                "must": [{"match": {"cuisine": "italian"}}]
            }
        },
        "size": 5
    })
    pp(results)

    # ── Example 3: Hotels with 4+ stars ─────────────────────────────────────
    print("\n🏨  Hotels rated 4+ stars")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "query": {
            "bool": {
                "must":   [{"term": {"category": "hotels"}}],
                "filter": [{"range": {"stars": {"gte": 4}}}]
            }
        },
        "size": 5
    })
    pp(results)

    # ── Example 4: Places near Lancaster city centre (5km radius) ────────────
    print("\n📍  Places within 5km of Lancaster centre")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "query": {
            "geo_distance": {
                "distance": "5km",
                "location": {"lat": 54.0466, "lon": -2.8007}
            }
        },
        "size": 5
    })
    pp(results)

    # ── Example 5: Aggregation — top towns by place count ────────────────────
    print("\n📊  Top 10 towns by number of places")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "size": 0,
        "aggs": {
            "by_town": {
                "terms": {"field": "town.keyword", "size": 10}
            }
        }
    })
    for bucket in results["aggregations"]["by_town"]["buckets"]:
        print(f"  {bucket['key']:25} {bucket['doc_count']:4} places")

    # ── Example 6: Aggregation — breakdown by category ───────────────────────
    print("\n📊  Breakdown by category")
    print("-" * 50)
    results = es.search(index=INDEX, body={
        "size": 0,
        "aggs": {
            "by_category": {
                "terms": {"field": "category", "size": 20}
            }
        }
    })
    for bucket in results["aggregations"]["by_category"]["buckets"]:
        print(f"  {bucket['key']:20} {bucket['doc_count']:4}")


if __name__ == "__main__":
    main()
