"""
Lancashire Places — Semantic Search Engine

Converts every place into a meaning vector using a local embedding model.
Searches find places by *concept*, not just keywords.

Examples that now work:
    "somewhere romantic for dinner"  → upscale restaurants
    "things to do outdoors"          → parks, nature reserves, attractions
    "cheap lunch"                    → cafes, fast food, budget spots
    "family day out"                 → museums, theme parks, attractions
    "traditional British pub"        → pubs with British character
    "coffee and cake"                → cafes, bakeries

Model: all-MiniLM-L6-v2 (~90MB, downloaded once, runs fully offline after that)
"""

import sqlite3
import struct
import os
import math
import numpy as np
from sentence_transformers import SentenceTransformer

MODEL_NAME = "all-MiniLM-L6-v2"
_model = None


# ── Model ─────────────────────────────────────────────────────────────────────

def get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        print(f"  🤖 Loading embedding model ({MODEL_NAME})...")
        _model = SentenceTransformer(MODEL_NAME)
        print(f"  ✓ Model ready")
    return _model


# ── Text representation ───────────────────────────────────────────────────────

def place_to_text(row: dict) -> str:
    """
    Build a rich text description of a place to embed.
    The richer this is, the better the semantic matching.
    """
    parts = []

    name     = row.get("name", "")
    category = row.get("category", "")
    ptype    = row.get("type", "")
    cuisine  = row.get("cuisine", "")
    town     = row.get("town", "")
    address  = row.get("address", "")
    hours    = row.get("opening_hours", "")
    stars    = row.get("stars")
    wheelchair = row.get("wheelchair", "")

    if name:     parts.append(name)
    if category: parts.append(category.replace("_", " "))
    if ptype:    parts.append(ptype.replace("_", " "))

    # Cuisine — expand shorthand to full words
    if cuisine:
        cuisine_clean = cuisine.replace(";", " and ").replace("_", " ")
        parts.append(f"{cuisine_clean} cuisine")

    if town:    parts.append(f"in {town}")
    if address: parts.append(address)

    # Add implied context
    if category == "restaurants":
        parts.append("food dining eat restaurant meal")
    elif category == "cafes":
        parts.append("coffee cafe drink tea snack light bite")
    elif category == "pubs":
        parts.append("pub bar beer drink social")
    elif category == "hotels":
        parts.append("hotel accommodation stay sleep overnight")
        if stars:
            parts.append(f"{int(stars)} star hotel")
    elif category == "attractions":
        parts.append("visit attraction sightseeing activity things to do")

    if hours:
        parts.append(f"open {hours}")
    if wheelchair == "yes":
        parts.append("wheelchair accessible")

    return " | ".join(p for p in parts if p)


# ── Vector storage ────────────────────────────────────────────────────────────

def vec_to_blob(vec: np.ndarray) -> bytes:
    return struct.pack(f"{len(vec)}f", *vec)


def blob_to_vec(blob: bytes) -> np.ndarray:
    n = len(blob) // 4
    return np.array(struct.unpack(f"{n}f", blob), dtype=np.float32)


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(np.dot(a, b) / (norm_a * norm_b))


# ── Index builder ─────────────────────────────────────────────────────────────

def build_embeddings(db_path: str, batch_size: int = 64, force: bool = False):
    """
    Generate and store embeddings for all places in the database.
    Skips places that already have embeddings unless force=True.
    """
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    # Create embeddings table
    conn.execute("""
        CREATE TABLE IF NOT EXISTS embeddings (
            osm_id  INTEGER PRIMARY KEY,
            vector  BLOB NOT NULL
        )
    """)
    conn.commit()

    # Find places that need embeddings
    if force:
        rows = conn.execute("SELECT * FROM places").fetchall()
    else:
        rows = conn.execute("""
            SELECT p.* FROM places p
            LEFT JOIN embeddings e ON e.osm_id = p.osm_id
            WHERE e.osm_id IS NULL
        """).fetchall()

    if not rows:
        print("  ✓ All places already have embeddings")
        conn.close()
        return

    print(f"  🔢 Generating embeddings for {len(rows)} places...")
    model = get_model()

    # Process in batches
    all_rows = [dict(r) for r in rows]
    texts    = [place_to_text(r) for r in all_rows]
    osm_ids  = [r["osm_id"] for r in all_rows]

    for i in range(0, len(texts), batch_size):
        batch_texts = texts[i:i + batch_size]
        batch_ids   = osm_ids[i:i + batch_size]

        vectors = model.encode(batch_texts, show_progress_bar=False, normalize_embeddings=True)

        conn.executemany(
            "INSERT OR REPLACE INTO embeddings (osm_id, vector) VALUES (?, ?)",
            [(oid, vec_to_blob(vec)) for oid, vec in zip(batch_ids, vectors)]
        )
        conn.commit()

        done = min(i + batch_size, len(texts))
        print(f"  [{done}/{len(texts)}]", end="\r")

    total = conn.execute("SELECT COUNT(*) FROM embeddings").fetchone()[0]
    conn.close()
    print(f"\n  ✓ {total} embeddings stored in database")


# ── Semantic search ───────────────────────────────────────────────────────────

def semantic_search(
    db_path:  str,
    query:    str,
    category: str  = None,
    town:     str  = None,
    limit:    int  = 20,
    threshold: float = 0.2,
) -> list[dict]:
    """
    Search by meaning. Returns places ranked by semantic similarity to the query.

    threshold: minimum similarity score (0-1). Lower = more results but less relevant.
    """
    model = get_model()

    # Embed the query
    query_vec = model.encode(query, normalize_embeddings=True)

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    # Load embeddings (with optional pre-filter)
    sql = """
        SELECT p.*, e.vector FROM places p
        JOIN embeddings e ON e.osm_id = p.osm_id
        WHERE 1=1
    """
    params = []
    if category:
        sql += " AND p.category = ?"
        params.append(category.lower())
    if town:
        sql += " AND LOWER(p.town) LIKE ?"
        params.append(f"%{town.lower()}%")

    rows = conn.execute(sql, params).fetchall()
    conn.close()

    if not rows:
        return []

    # Score every place
    results = []
    for row in rows:
        vec   = blob_to_vec(row["vector"])
        score = cosine_similarity(query_vec, vec)
        if score >= threshold:
            r = dict(row)
            r.pop("vector", None)
            r["relevance_score"] = round(score, 4)
            results.append(r)

    # Sort by relevance
    results.sort(key=lambda x: x["relevance_score"], reverse=True)
    return results[:limit]
