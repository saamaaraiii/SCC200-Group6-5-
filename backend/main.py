from __future__ import annotations

import hashlib
import os
import html
import json
import math
import re
import secrets
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from flask import Flask, jsonify, request


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = PROJECT_ROOT / "API+SERVER_Draft"
SCRAPER_DIR = PROJECT_ROOT / "web scraping tools" / "Scraper"
TRAIN_SEED_PATH = Path(__file__).resolve().parent / "seed.sql"
DEFAULT_STORE_PATH = Path(__file__).resolve().parent / "data" / "runtime_store.json"

LANCASTER_STATION_COORDS = {"lat": 54.0466, "lon": -2.8007}
API_VERSION = "2.0.0"

EXTRA_NETWORK_STATIONS = [
    {"name": "Lancaster", "code": "LAN", "latitude": 54.0486, "longitude": -2.7996},
    {"name": "Preston", "code": "PRE", "latitude": 53.7552, "longitude": -2.7084},
]

EXTRA_NETWORK_SEGMENTS = [
    {"from_code": "LAN", "to_code": "PRE", "distance_km": 34.0, "duration_mins": 23},
    {"from_code": "PRE", "to_code": "LAN", "distance_km": 34.0, "duration_mins": 23},
    {"from_code": "PRE", "to_code": "MAN", "distance_km": 50.0, "duration_mins": 36},
    {"from_code": "MAN", "to_code": "PRE", "distance_km": 50.0, "duration_mins": 36},
]

STATION_CODE_ALIASES = {
    "lancaster": "LAN",
    "lancaster station": "LAN",
    "preston": "PRE",
    "preston station": "PRE",
    "manchester": "MAN",
    "manchester piccadilly": "MAN",
    "london euston": "EUS",
}


class HttpError(Exception):
    def __init__(self, status: int, message: str, details: Any = None) -> None:
        super().__init__(message)
        self.status = status
        self.message = message
        self.details = details


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize_text(value: Any) -> str:
    text = str(value or "").lower()
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def tokenize(text: Any) -> List[str]:
    return [token for token in normalize_text(text).split(" ") if len(token) > 1]


def to_number(value: Any, field_name: str) -> float:
    try:
        return float(value)
    except (TypeError, ValueError) as exc:
        raise HttpError(400, f"{field_name} must be a number") from exc


def to_int(value: Any, field_name: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise HttpError(400, f"{field_name} must be an integer") from exc


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2) ** 2
    )
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def random_id(prefix: str) -> str:
    return f"{prefix}_{secrets.token_hex(6)}"


def read_text(path: Path, fallback: str = "") -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception:
        return fallback


def read_json(path: Path, fallback: Any = None) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return fallback


def extract_tag(block: str, tag: str) -> Optional[str]:
    pattern = rf"<{re.escape(tag)}>([\s\S]*?)</{re.escape(tag)}>"
    match = re.search(pattern, block, flags=re.IGNORECASE)
    if not match:
        return None
    return html.unescape(match.group(1).strip())


def extract_blocks(xml_text: str, tag: str) -> List[str]:
    pattern = rf"<{re.escape(tag)}>([\s\S]*?)</{re.escape(tag)}>"
    return re.findall(pattern, xml_text, flags=re.IGNORECASE)


def parse_departure_board(xml_text: str) -> Dict[str, Any]:
    station = extract_tag(xml_text, "lt4:locationName")
    crs = extract_tag(xml_text, "lt4:crs")
    generated_at = extract_tag(xml_text, "lt4:generatedAt")

    services: List[Dict[str, Any]] = []
    for service_xml in extract_blocks(xml_text, "lt8:service"):
        calling_points = []
        for cp in extract_blocks(service_xml, "lt8:callingPoint"):
            calling_points.append(
                {
                    "location": extract_tag(cp, "lt8:locationName"),
                    "scheduled_time": extract_tag(cp, "lt8:st"),
                    "estimated_time": extract_tag(cp, "lt8:et"),
                    "delay_reason": extract_tag(cp, "lt8:delayReason"),
                }
            )

        origin_block = extract_tag(service_xml, "lt5:origin") or ""
        destination_block = extract_tag(service_xml, "lt5:destination") or ""

        services.append(
            {
                "scheduled_departure": extract_tag(service_xml, "lt4:std"),
                "estimated_departure": extract_tag(service_xml, "lt4:etd"),
                "platform": extract_tag(service_xml, "lt4:platform"),
                "operator": extract_tag(service_xml, "lt4:operator"),
                "operator_code": extract_tag(service_xml, "lt4:operatorCode"),
                "service_type": extract_tag(service_xml, "lt4:serviceType"),
                "service_id": extract_tag(service_xml, "lt4:serviceID"),
                "origin": extract_tag(origin_block, "lt4:locationName"),
                "destination": extract_tag(destination_block, "lt4:locationName"),
                "delay_reason": extract_tag(service_xml, "lt4:delayReason"),
                "calling_points": calling_points,
            }
        )

    return {
        "station": station,
        "crs": crs,
        "generated_at": generated_at,
        "services": services,
    }


def parse_bus_live(xml_text: str) -> List[Dict[str, Any]]:
    vehicles: List[Dict[str, Any]] = []
    for activity_xml in extract_blocks(xml_text, "VehicleActivity"):
        journey = extract_tag(activity_xml, "MonitoredVehicleJourney") or ""
        location = extract_tag(journey, "VehicleLocation") or ""
        lat_raw = extract_tag(location, "Latitude")
        lon_raw = extract_tag(location, "Longitude")
        vehicles.append(
            {
                "line_ref": extract_tag(journey, "LineRef"),
                "vehicle_ref": extract_tag(journey, "VehicleRef"),
                "direction": extract_tag(journey, "DirectionRef"),
                "origin_name": extract_tag(journey, "OriginName"),
                "destination_name": extract_tag(journey, "DestinationName"),
                "origin_time": extract_tag(journey, "OriginAimedDepartureTime"),
                "destination_time": extract_tag(journey, "DestinationAimedArrivalTime"),
                "published_line_name": extract_tag(journey, "PublishedLineName"),
                "operator_ref": extract_tag(journey, "OperatorRef"),
                "bearing": extract_tag(journey, "Bearing"),
                "recorded_at": extract_tag(activity_xml, "RecordedAtTime"),
                "location": {
                    "latitude": float(lat_raw),
                    "longitude": float(lon_raw),
                }
                if lat_raw and lon_raw
                else None,
            }
        )
    return vehicles


def parse_vms(xml_text: str) -> List[Dict[str, Any]]:
    statuses: List[Dict[str, Any]] = []
    for block in extract_blocks(xml_text, "vmsControllerStatus"):
        ref_block = extract_tag(block, "vmsControllerReference") or ""
        ext_block = extract_tag(block, "vmsStatusExtensionG") or ""
        lines = [html.unescape(line.strip()) for line in re.findall(r"<textLine>([^<]+)</textLine>", block)]
        unique_lines = [line for idx, line in enumerate(lines) if line and line not in lines[:idx]]
        statuses.append(
            {
                "sign_id": extract_tag(ref_block, "idG"),
                "working_status": (extract_tag(block, "workingStatus") or "").strip(),
                "message_set_by": extract_tag(block, "messageSetBy"),
                "reason": extract_tag(block, "reasonForSetting"),
                "time_last_set": extract_tag(block, "timeLastSet"),
                "road_name": extract_tag(ext_block, "roadName"),
                "location_description": extract_tag(ext_block, "locationDescription"),
                "latitude": float(extract_tag(ext_block, "latitude") or 0),
                "longitude": float(extract_tag(ext_block, "longitude") or 0),
                "message_lines": unique_lines,
            }
        )
    return statuses


def parse_naptan_stops(xml_text: str) -> List[Dict[str, Any]]:
    stops: List[Dict[str, Any]] = []
    for block in extract_blocks(xml_text, "StopPoint"):
        descriptor = extract_tag(block, "Descriptor") or ""
        place = extract_tag(block, "Place") or ""
        location = extract_tag(place, "Location") or ""
        translation = extract_tag(location, "Translation") or ""
        stop_class = extract_tag(block, "StopClassification") or ""
        common_name = extract_tag(descriptor, "CommonName")
        atco = extract_tag(block, "AtcoCode")
        if not atco or not common_name:
            continue

        lat = extract_tag(translation, "Latitude")
        lon = extract_tag(translation, "Longitude")
        stops.append(
            {
                "atco_code": atco,
                "naptan_code": extract_tag(block, "NaptanCode"),
                "common_name": common_name,
                "street": extract_tag(descriptor, "Street"),
                "indicator": extract_tag(descriptor, "Indicator"),
                "town": extract_tag(place, "Town"),
                "latitude": float(lat) if lat else None,
                "longitude": float(lon) if lon else None,
                "stop_type": extract_tag(stop_class, "StopType"),
            }
        )
    return stops


def build_train_network(stations: List[Dict[str, Any]], segments: List[Dict[str, Any]]) -> Dict[str, Any]:
    stations_by_id = {station["id"]: station for station in stations}
    stations_by_code = {str(station.get("code") or "").upper(): station for station in stations if station.get("code")}
    edges_by_pair = {
        f"{segment['from_station_id']}-{segment['to_station_id']}": segment
        for segment in segments
    }
    return {
        "stations": stations,
        "segments": segments,
        "stations_by_id": stations_by_id,
        "stations_by_code": stations_by_code,
        "edges_by_pair": edges_by_pair,
    }


def augment_train_network(network: Dict[str, Any]) -> Dict[str, Any]:
    stations = [dict(station) for station in network.get("stations", [])]
    segments = [dict(segment) for segment in network.get("segments", [])]

    stations_by_code = {str(item.get("code") or "").upper(): item for item in stations if item.get("code")}
    used_station_ids = {int(item.get("id")) for item in stations if str(item.get("id", "")).isdigit()}
    next_station_id = max(used_station_ids or {0}) + 1

    for extra in EXTRA_NETWORK_STATIONS:
        code = str(extra.get("code") or "").upper()
        if not code or code in stations_by_code:
            continue
        while next_station_id in used_station_ids:
            next_station_id += 1
        station = {
            "id": next_station_id,
            "name": extra["name"],
            "code": code,
            "latitude": float(extra["latitude"]),
            "longitude": float(extra["longitude"]),
        }
        stations.append(station)
        stations_by_code[code] = station
        used_station_ids.add(next_station_id)
        next_station_id += 1

    segment_pairs = {
        (int(seg["from_station_id"]), int(seg["to_station_id"]))
        for seg in segments
        if seg.get("from_station_id") is not None and seg.get("to_station_id") is not None
    }
    for extra in EXTRA_NETWORK_SEGMENTS:
        from_station = stations_by_code.get(str(extra.get("from_code") or "").upper())
        to_station = stations_by_code.get(str(extra.get("to_code") or "").upper())
        if not from_station or not to_station:
            continue

        pair = (int(from_station["id"]), int(to_station["id"]))
        if pair in segment_pairs:
            continue

        segments.append(
            {
                "from_station_id": pair[0],
                "to_station_id": pair[1],
                "distance_km": float(extra["distance_km"]),
                "duration_mins": int(extra["duration_mins"]),
            }
        )
        segment_pairs.add(pair)

    return build_train_network(stations, segments)


def parse_train_seed(seed_sql: str) -> Dict[str, Any]:
    station_match = re.search(r"INSERT OR IGNORE INTO stations[\s\S]*?VALUES([\s\S]*?);", seed_sql, flags=re.IGNORECASE)
    segment_match = re.search(r"INSERT OR IGNORE INTO route_segments[\s\S]*?VALUES([\s\S]*?);", seed_sql, flags=re.IGNORECASE)
    station_block = station_match.group(1) if station_match else ""
    segment_block = segment_match.group(1) if segment_match else ""

    stations = []
    station_pattern = re.compile(r"\((\d+),\s*'((?:[^']|''|\\')*)',\s*'((?:[^']|''|\\')*)',\s*([-\d.]+),\s*([-\d.]+)\)")
    for item in station_pattern.findall(station_block):
        stations.append(
            {
                "id": int(item[0]),
                "name": item[1].replace("''", "'"),
                "code": item[2].replace("''", "'"),
                "latitude": float(item[3]),
                "longitude": float(item[4]),
            }
        )

    segments = []
    segment_pattern = re.compile(r"\((\d+),\s*(\d+),\s*([-\d.]+),\s*(\d+)\)")
    for item in segment_pattern.findall(segment_block):
        segments.append(
            {
                "from_station_id": int(item[0]),
                "to_station_id": int(item[1]),
                "distance_km": float(item[2]),
                "duration_mins": int(item[3]),
            }
        )

    return augment_train_network(build_train_network(stations, segments))

def load_places(places_dir: Path) -> Dict[str, Any]:
    files = [item for item in places_dir.iterdir() if re.match(r"^lancashire_.*\.json$", item.name, flags=re.IGNORECASE)]

    by_id: Dict[int, Dict[str, Any]] = {}
    for file_path in files:
        records = read_json(file_path, [])
        if not isinstance(records, list):
            continue

        category_from_name = re.sub(r"_\d{8}_\d{6}\.json$", "", file_path.name, flags=re.IGNORECASE)
        category_from_name = re.sub(r"^lancashire_", "", category_from_name, flags=re.IGNORECASE)
        category_from_name = re.sub(r"\.json$", "", category_from_name, flags=re.IGNORECASE).lower()

        for rec in records:
            try:
                osm_id = int(rec.get("osm_id"))
            except Exception:
                continue

            parsed = {
                "osm_id": osm_id,
                "osm_type": rec.get("osm_type"),
                "category": str(rec.get("category") or category_from_name or "").lower(),
                "type": rec.get("type"),
                "name": rec.get("name") or "",
                "cuisine": rec.get("cuisine") or "",
                "address": rec.get("address") or "",
                "postcode": rec.get("postcode") or "",
                "town": rec.get("town") or "",
                "latitude": float(rec.get("latitude")) if rec.get("latitude") not in (None, "") else None,
                "longitude": float(rec.get("longitude")) if rec.get("longitude") not in (None, "") else None,
                "phone": rec.get("phone") or "",
                "website": rec.get("website") or "",
                "opening_hours": rec.get("opening_hours") or "",
                "stars": float(rec.get("stars")) if rec.get("stars") not in (None, "") else None,
                "wheelchair": rec.get("wheelchair") or "",
                "scraped_at": rec.get("scraped_at"),
            }

            existing = by_id.get(osm_id)
            if not existing:
                by_id[osm_id] = parsed
            else:
                existing_ts = existing.get("scraped_at") or "1970-01-01T00:00:00"
                parsed_ts = parsed.get("scraped_at") or "1970-01-01T00:00:00"
                if parsed_ts >= existing_ts:
                    by_id[osm_id] = parsed

    records = list(by_id.values())

    by_category: Dict[str, int] = {}
    top_towns: Dict[str, int] = {}
    for record in records:
        category = record.get("category") or "unknown"
        by_category[category] = by_category.get(category, 0) + 1
        town = record.get("town") or ""
        if town:
            top_towns[town] = top_towns.get(town, 0) + 1

    return {
        "records": records,
        "by_id": by_id,
        "by_category": by_category,
        "top_towns": top_towns,
    }


def default_store() -> Dict[str, Any]:
    return {
        "counters": {
            "user_id": 1,
            "journey_id": 1,
            "leg_id": 1,
            "ticket_id": 1,
            "payment_id": 1,
            "taxi_id": 1,
            "claim_id": 1,
            "report_id": 1,
            "quote_id": 1,
        },
        "users": [],
        "railcards": [
            {"railcard_id": 1, "type": "16-25", "discount_percent": 34},
            {"railcard_id": 2, "type": "26-30", "discount_percent": 34},
            {"railcard_id": 3, "type": "Senior", "discount_percent": 34},
            {"railcard_id": 4, "type": "Two Together", "discount_percent": 34},
        ],
        "journeys": [],
        "journey_legs": [],
        "tickets": [],
        "payments": [],
        "taxi_quotes": [],
        "taxi_bookings": [],
        "delay_claims": [],
        "disruption_reports": [],
    }


def ensure_store_shape(store: Dict[str, Any]) -> Dict[str, Any]:
    fallback = default_store()
    for key, value in fallback.items():
        if key not in store:
            store[key] = value

    if not isinstance(store.get("counters"), dict):
        store["counters"] = dict(fallback["counters"])

    for key, value in fallback["counters"].items():
        if key not in store["counters"] or not isinstance(store["counters"][key], int):
            store["counters"][key] = value

    max_by_counter = {
        "user_id": max([0] + [int(item.get("user_id", 0)) for item in store.get("users", [])]),
        "journey_id": max([0] + [int(item.get("journey_id", 0)) for item in store.get("journeys", [])]),
        "leg_id": max([0] + [int(item.get("leg_id", 0)) for item in store.get("journey_legs", [])]),
        "ticket_id": max([0] + [int(item.get("ticket_id", 0)) for item in store.get("tickets", [])]),
        "payment_id": max([0] + [int(item.get("payment_id", 0)) for item in store.get("payments", [])]),
        "taxi_id": max([0] + [int(item.get("taxi_id", 0)) for item in store.get("taxi_bookings", [])]),
        "claim_id": max([0] + [int(item.get("claim_id", 0)) for item in store.get("delay_claims", [])]),
        "report_id": max([0] + [int(item.get("report_id", 0)) for item in store.get("disruption_reports", [])]),
        "quote_id": max([0] + [int(item.get("quote_id", 0)) for item in store.get("taxi_quotes", [])]),
    }

    for key, max_value in max_by_counter.items():
        if store["counters"][key] <= max_value:
            store["counters"][key] = max_value + 1

    return store


def load_store(store_path: Path) -> Dict[str, Any]:
    if not store_path.exists():
        return default_store()
    loaded = read_json(store_path, default_store())
    return ensure_store_shape(loaded)


def save_store(store_path: Path, store: Dict[str, Any]) -> None:
    store_path.parent.mkdir(parents=True, exist_ok=True)
    store_path.write_text(json.dumps(store, indent=2), encoding="utf-8")


def next_counter(store: Dict[str, Any], key: str) -> int:
    value = store["counters"][key]
    store["counters"][key] += 1
    return value


def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


def sanitize_user(user: Dict[str, Any]) -> Dict[str, Any]:
    copy = dict(user)
    copy.pop("password_hash", None)
    return copy


def resolve_station(network: Dict[str, Any], value: Any) -> Optional[Dict[str, Any]]:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None

    if text.isdigit():
        station = network["stations_by_id"].get(int(text))
        if station:
            return station

    station = network["stations_by_code"].get(text.upper())
    if station:
        return station

    alias_code = STATION_CODE_ALIASES.get(normalize_text(text))
    if alias_code:
        station = network["stations_by_code"].get(alias_code)
        if station:
            return station

    lowered = text.lower()
    exact = next((item for item in network["stations"] if item["name"].lower() == lowered), None)
    if exact:
        return exact

    return next((item for item in network["stations"] if lowered in item["name"].lower()), None)


def build_adjacency(segments: List[Dict[str, Any]], weight_key: str) -> Dict[int, List[Dict[str, Any]]]:
    adjacency: Dict[int, List[Dict[str, Any]]] = {}
    for seg in segments:
        adjacency.setdefault(seg["from_station_id"], []).append(
            {"to": seg["to_station_id"], "weight": seg[weight_key]}
        )
    return adjacency


def bfs_path(adjacency: Dict[int, List[Dict[str, Any]]], start_id: int, end_id: int) -> Optional[List[int]]:
    if start_id == end_id:
        return [start_id]
    queue: List[List[int]] = [[start_id]]
    visited = {start_id}
    while queue:
        path = queue.pop(0)
        node = path[-1]
        for edge in adjacency.get(node, []):
            if edge["to"] in visited:
                continue
            candidate = path + [edge["to"]]
            if edge["to"] == end_id:
                return candidate
            visited.add(edge["to"])
            queue.append(candidate)
    return None


def dijkstra_path(adjacency: Dict[int, List[Dict[str, Any]]], start_id: int, end_id: int) -> Optional[List[int]]:
    dist: Dict[int, float] = {start_id: 0.0}
    prev: Dict[int, int] = {}
    queue: List[Dict[str, float]] = [{"node": start_id, "weight": 0.0}]

    while queue:
        queue.sort(key=lambda row: row["weight"])
        current = queue.pop(0)
        node = int(current["node"])
        weight = float(current["weight"])

        if node == end_id:
            break
        if weight > dist.get(node, math.inf):
            continue

        for edge in adjacency.get(node, []):
            alt = weight + float(edge["weight"])
            if alt < dist.get(edge["to"], math.inf):
                dist[edge["to"]] = alt
                prev[edge["to"]] = node
                queue.append({"node": edge["to"], "weight": alt})

    if end_id not in dist:
        return None

    path = [end_id]
    current = end_id
    while current in prev:
        current = prev[current]
        path.append(current)
    path.reverse()
    return path


def summarize_path(network: Dict[str, Any], path_ids: List[int], algorithm: str, weight: str) -> Dict[str, Any]:
    legs = []
    total_distance = 0.0
    total_duration = 0

    for idx in range(len(path_ids) - 1):
        from_id = path_ids[idx]
        to_id = path_ids[idx + 1]
        edge = network["edges_by_pair"].get(f"{from_id}-{to_id}")
        if not edge:
            continue
        total_distance += edge["distance_km"]
        total_duration += edge["duration_mins"]
        legs.append(
            {
                "from_stop": network["stations_by_id"].get(from_id, {}).get("name", f"Station {from_id}"),
                "to_stop": network["stations_by_id"].get(to_id, {}).get("name", f"Station {to_id}"),
                "from_stop_id": from_id,
                "to_stop_id": to_id,
                "distance_km": edge["distance_km"],
                "duration_mins": edge["duration_mins"],
            }
        )

    return {
        "algorithm": algorithm,
        "weight": weight,
        "path": [network["stations_by_id"].get(item) for item in path_ids if network["stations_by_id"].get(item)],
        "legs": legs,
        "changes": max(len(path_ids) - 2, 0),
        "total_distance_km": round(total_distance, 2),
        "total_duration_mins": total_duration,
    }

def infer_category_hints(query: str) -> List[str]:
    q = normalize_text(query)
    hints: List[str] = []
    mapping = [
        {"category": "restaurants", "words": ["dinner", "lunch", "eat", "food", "restaurant", "pizza"]},
        {"category": "cafes", "words": ["coffee", "cafe", "cake", "breakfast"]},
        {"category": "pubs", "words": ["pub", "ale", "bar", "drinks"]},
        {"category": "hotels", "words": ["hotel", "stay", "sleep", "accommodation", "overnight"]},
        {"category": "attractions", "words": ["museum", "park", "attraction", "family", "kids", "activity"]},
    ]
    for item in mapping:
        if any(word in q for word in item["words"]):
            hints.append(item["category"])
    return hints


def semantic_expand_tokens(tokens: List[str]) -> List[str]:
    synonym_map = {
        "romantic": ["date", "cozy", "dinner", "fine"],
        "cheap": ["budget", "affordable", "lowcost", "deal"],
        "family": ["kids", "children", "group"],
        "outdoors": ["outside", "nature", "park", "walk"],
        "lunch": ["sandwich", "brunch", "cafe"],
        "pub": ["bar", "ale", "tavern"],
        "hotel": ["stay", "sleep", "accommodation", "inn"],
        "coffee": ["cafe", "espresso", "cake"],
    }
    expanded = set(tokens)
    for token in tokens:
        for mapped in synonym_map.get(token, []):
            expanded.add(mapped)
    return list(expanded)


def pick_fields(record: Dict[str, Any], fields_csv: Optional[str]) -> Dict[str, Any]:
    if not fields_csv:
        return record
    requested = {field.strip() for field in fields_csv.split(",") if field.strip()}
    if not requested:
        return record
    return {key: value for key, value in record.items() if key in requested}


def parse_limit(raw: Optional[str], fallback: int, minimum: int = 1, maximum: int = 500) -> int:
    if raw in (None, ""):
        return fallback
    try:
        value = int(raw)
    except ValueError as exc:
        raise HttpError(400, "limit must be an integer") from exc
    if value < minimum or value > maximum:
        raise HttpError(400, f"limit must be between {minimum} and {maximum}")
    return value


def build_state(options: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    options = options or {}
    data_dir = Path(options.get("data_dir") or DATA_DIR)
    scraper_dir = Path(options.get("scraper_dir") or SCRAPER_DIR)
    train_seed_path = Path(options.get("train_seed_path") or TRAIN_SEED_PATH)
    store_path = Path(options.get("store_path") or DEFAULT_STORE_PATH)

    datasets = {
        "lancs_fac": read_json(data_dir / "lancs_fac.json", {}),
        "weather": read_json(data_dir / "weather.json", {}),
        "delay_codes": read_json(data_dir / "delay-codes.json", []),
        "bus_reference": read_json(data_dir / "bus.json", {"results": []}),
        "departures_xml": read_text(data_dir / "lancs_station_depart", ""),
        "bus_live_xml": read_text(data_dir / "busLive.xml", ""),
        "naptan_xml": read_text(data_dir / "naptan.xml", ""),
        "vms_xml": read_text(data_dir / "vms", ""),
        "corpus": read_json(data_dir / "CORPUSExtract.json", {"TIPLOCDATA": []}),
        "smart": read_json(data_dir / "SMARTExtract.json", {"BERTHDATA": []}),
    }

    parsed = {
        "departures": parse_departure_board(datasets["departures_xml"]),
        "bus_live": parse_bus_live(datasets["bus_live_xml"]),
        "vms": parse_vms(datasets["vms_xml"]),
        "naptan_stops": None,
        "places": load_places(scraper_dir),
        "train_network": parse_train_seed(read_text(train_seed_path, "")),
    }

    store = ensure_store_shape(load_store(store_path))

    taxi_providers = [
        {
            "provider_id": "LAN_CABS",
            "name": "Lancaster Station Cabs",
            "phone": "+44 1524 555111",
            "base_fare_gbp": 3.8,
            "per_km_gbp": 2.2,
        },
        {
            "provider_id": "CITY_TAXI",
            "name": "CityLine Taxi",
            "phone": "+44 1524 555222",
            "base_fare_gbp": 4.2,
            "per_km_gbp": 2.05,
        },
        {
            "provider_id": "NIGHT_RIDE",
            "name": "NightRide Lancaster",
            "phone": "+44 1524 555333",
            "base_fare_gbp": 4.6,
            "per_km_gbp": 2.45,
        },
    ]

    return {
        "data_dir": data_dir,
        "scraper_dir": scraper_dir,
        "train_seed_path": train_seed_path,
        "store_path": store_path,
        "datasets": datasets,
        "parsed": parsed,
        "store": store,
        "sessions": {},
        "taxi_providers": taxi_providers,
    }


def get_or_parse_stops(state: Dict[str, Any]) -> List[Dict[str, Any]]:
    if state["parsed"]["naptan_stops"] is None:
        state["parsed"]["naptan_stops"] = parse_naptan_stops(state["datasets"]["naptan_xml"])
    return state["parsed"]["naptan_stops"]


def get_weather_payload(state: Dict[str, Any]) -> Dict[str, Any]:
    weather_root = state["datasets"].get("weather", {}).get("weather", {})
    main = weather_root.get("main", {})
    weather_list = weather_root.get("weather", [])
    weather_0 = weather_list[0] if weather_list else {}
    return {
        "status": "success",
        "data": {
            "location": {
                "name": weather_root.get("name"),
                "latitude": weather_root.get("coord", {}).get("lat"),
                "longitude": weather_root.get("coord", {}).get("lon"),
            },
            "conditions": {
                "main": weather_0.get("main"),
                "description": weather_0.get("description"),
                "icon": weather_0.get("icon"),
            },
            "temperature": {
                "current": main.get("temp"),
                "feels_like": main.get("feels_like"),
                "min": main.get("temp_min"),
                "max": main.get("temp_max"),
            },
            "wind": {
                "speed": weather_root.get("wind", {}).get("speed"),
                "direction": weather_root.get("wind", {}).get("deg"),
                "gust": weather_root.get("wind", {}).get("gust"),
            },
            "humidity": main.get("humidity"),
            "visibility": weather_root.get("visibility"),
            "clouds": weather_root.get("clouds", {}).get("all"),
            "timestamp": weather_root.get("dt"),
        },
    }


def compute_delay_risk(state: Dict[str, Any], mode: str) -> Dict[str, Any]:
    services = state["parsed"]["departures"].get("services", [])
    delayed = [
        service
        for service in services
        if normalize_text(service.get("estimated_departure") or "") not in ("", "on time")
    ]
    delayed_ratio = len(delayed) / len(services) if services else 0

    weather_data = get_weather_payload(state)["data"]
    wind = float(weather_data.get("wind", {}).get("speed") or 0)
    weather_main = normalize_text(weather_data.get("conditions", {}).get("main") or "")

    weather_risk = 0.0
    if wind >= 15:
        weather_risk += 0.3
    if wind >= 25:
        weather_risk += 0.3
    if "rain" in weather_main or "snow" in weather_main:
        weather_risk += 0.2
    if "storm" in weather_main:
        weather_risk += 0.4
    weather_risk = min(weather_risk, 1)

    if mode == "train":
        score = delayed_ratio * 0.75 + weather_risk * 0.25
    elif mode == "bus":
        score = delayed_ratio * 0.35 + weather_risk * 0.65
    else:
        score = delayed_ratio * 0.25 + weather_risk * 0.75

    rounded = max(0, min(1, round(score, 3)))
    level = "low" if rounded < 0.25 else "medium" if rounded < 0.55 else "high"
    return {
        "mode": mode,
        "risk_score": rounded,
        "risk_level": level,
        "factors": {
            "delayed_services_ratio": round(delayed_ratio, 3),
            "weather_risk": round(weather_risk, 3),
        },
    }


def auth_user_from_request(state: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return None

    token = auth_header.replace("Bearer ", "", 1).strip()
    session = state["sessions"].get(token)
    if not session:
        return None

    if datetime.now(timezone.utc) > session["expires_at"]:
        del state["sessions"][token]
        return None

    return next((user for user in state["store"]["users"] if user["user_id"] == session["user_id"]), None)


def bad_request_if_missing_body_json() -> Dict[str, Any]:
    body = request.get_json(silent=True)
    if body is None:
        return {}
    if not isinstance(body, dict):
        raise HttpError(400, "JSON object body required")
    return body

def create_app(options: Optional[Dict[str, Any]] = None) -> Flask:
    app = Flask(__name__)
    state = build_state(options)
    app.config["STATE"] = state

    @app.after_request
    def add_cors_headers(response):
        response.headers["Access-Control-Allow-Origin"] = "*"
        response.headers["Access-Control-Allow-Methods"] = "GET,POST,PUT,PATCH,DELETE,OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type,Authorization"
        return response

    @app.errorhandler(HttpError)
    def handle_http_error(err: HttpError):
        payload = {"status": "error", "message": err.message}
        if err.details is not None:
            payload["details"] = err.details
        return jsonify(payload), err.status

    @app.errorhandler(404)
    def handle_not_found(_err):
        return jsonify({"status": "error", "message": "Endpoint not found"}), 404

    @app.errorhandler(Exception)
    def handle_unexpected_error(err: Exception):
        return jsonify({"status": "error", "message": "Internal server error", "details": str(err)}), 500

    @app.route("/health", methods=["GET"])
    def health_check():
        return jsonify({"status": "healthy", "timestamp": now_iso(), "version": API_VERSION})

    def endpoint_records() -> List[Dict[str, Any]]:
        records = []
        for rule in app.url_map.iter_rules():
            if rule.rule.startswith("/static"):
                continue
            methods = [method for method in rule.methods if method in {"GET", "POST", "PUT", "PATCH", "DELETE"}]
            for method in methods:
                records.append({"method": method, "path": rule.rule})
        records.sort(key=lambda row: (row["path"], row["method"]))
        return records

    @app.route("/api/endpoints", methods=["GET"])
    def list_endpoints():
        return jsonify({"status": "success", "endpoints": endpoint_records()})

    @app.route("/api/lancs_fac", methods=["GET"])
    def lancs_fac():
        data = state["datasets"].get("lancs_fac", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "station_name": data.get("name"),
                    "crs_code": data.get("crsCode"),
                    "nrcc_code": data.get("nationalLocationCode"),
                    "location": data.get("location"),
                    "address": data.get("address"),
                    "operator": data.get("stationOperator", {}).get("name"),
                    "staffing_level": data.get("staffingLevel"),
                    "alerts": data.get("stationAlerts", []),
                },
            }
        )

    @app.route("/api/lancs_fac/accessibility", methods=["GET"])
    def lancs_accessibility():
        data = state["datasets"].get("lancs_fac", {}).get("stationAccessibility", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "step_free_category": data.get("stepFreeCategory", {}).get("category"),
                    "tactile_paving": data.get("tactilePaving"),
                    "induction_loops": data.get("inductionLoop", {}).get("provision"),
                    "wheelchairs_available": data.get("wheelchairsAvailable"),
                    "passenger_assistance": data.get("passengerAssistance", []),
                    "train_ramp": data.get("trainRamp", {}).get("available"),
                    "ticket_barriers": data.get("ticketBarriers", {}).get("available"),
                },
            }
        )

    @app.route("/api/lancs_fac/lifts", methods=["GET"])
    def lancs_lifts():
        data = state["datasets"].get("lancs_fac", {}).get("lifts", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "available": data.get("available"),
                    "statement": data.get("statement"),
                    "lifts_info": data.get("liftsInfo", []),
                },
            }
        )

    @app.route("/api/lancs_fac/ticket_buying", methods=["GET"])
    def lancs_ticket_buying():
        data = state["datasets"].get("lancs_fac", {}).get("ticketBuying", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "ticket_office": data.get("ticketOffice", {}),
                    "machines_available": data.get("ticketMachinesAvailable"),
                    "collect_online": data.get("collectOnlineBookedTickets", {}),
                    "pay_as_you_go": data.get("payAsYouGo", {}),
                },
            }
        )

    @app.route("/api/lancs_fac/transport_links", methods=["GET"])
    def lancs_transport_links():
        data = state["datasets"].get("lancs_fac", {}).get("transportLinks", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "bus": data.get("bus", {}).get("available"),
                    "replacement_bus": data.get("replacementBus", {}).get("available"),
                    "taxi": data.get("taxi", {}).get("available"),
                    "taxi_ranks": data.get("taxi", {}).get("taxiRanks", []),
                    "airport": data.get("airport", {}).get("available"),
                    "underground": data.get("underground", {}).get("available"),
                    "car_hire": data.get("carHire", {}).get("available"),
                    "port": data.get("port", {}).get("available"),
                },
            }
        )

    @app.route("/api/lancs_fac/cycling", methods=["GET"])
    def lancs_cycling():
        data = state["datasets"].get("lancs_fac", {}).get("cycling", {})
        return jsonify(
            {
                "status": "success",
                "data": {
                    "storage_available": data.get("cycleStorageAvailable"),
                    "spaces": data.get("spaces", {}).get("numberOfSpaces"),
                    "storage_types": data.get("typesOfStorage", []),
                    "sheltered": data.get("sheltered"),
                    "cctv": data.get("cctv"),
                    "location": data.get("location"),
                },
            }
        )

    @app.route("/api/lancs_fac/parking", methods=["GET"])
    def lancs_parking():
        data = state["datasets"].get("lancs_fac", {}).get("carParks", {})
        return jsonify({"status": "success", "data": {"car_parks": data}})

    @app.route("/api/departures", methods=["GET"])
    def departures():
        board = state["parsed"]["departures"]
        limit = parse_limit(request.args.get("limit"), len(board.get("services", [])), 1, 2000)
        data = board.get("services", [])[:limit]
        return jsonify(
            {
                "status": "success",
                "station": board.get("station"),
                "crs": board.get("crs"),
                "timestamp": now_iso(),
                "source_generated_at": board.get("generated_at"),
                "count": len(data),
                "data": data,
            }
        )

    @app.route("/api/departures/search", methods=["GET"])
    def departures_search():
        destination = normalize_text(request.args.get("destination"))
        if not destination:
            raise HttpError(400, "destination parameter required")
        limit = parse_limit(request.args.get("limit"), 50, 1, 1000)
        board = state["parsed"]["departures"]
        data = [
            row
            for row in board.get("services", [])
            if destination in normalize_text(row.get("destination"))
        ][:limit]
        return jsonify({"status": "success", "search_query": destination, "count": len(data), "data": data})

    @app.route("/api/bus/live", methods=["GET"])
    def bus_live():
        line_filter = normalize_text(request.args.get("line"))
        limit = parse_limit(request.args.get("limit"), 200, 1, 5000)
        records = state["parsed"]["bus_live"]
        if line_filter:
            records = [row for row in records if normalize_text(row.get("line_ref")) == line_filter]
        records = records[:limit]
        return jsonify({"status": "success", "timestamp": now_iso(), "count": len(records), "data": records})

    @app.route("/api/bus/live/location", methods=["GET"])
    def bus_live_location():
        vehicle = normalize_text(request.args.get("vehicle"))
        if not vehicle:
            raise HttpError(400, "vehicle parameter required")
        found = next(
            (row for row in state["parsed"]["bus_live"] if normalize_text(row.get("vehicle_ref")) == vehicle),
            None,
        )
        if not found:
            raise HttpError(404, "Vehicle not found")
        return jsonify({"status": "success", "data": found})

    @app.route("/api/bus/routes", methods=["GET"])
    def bus_routes():
        line = normalize_text(request.args.get("line"))
        operator = normalize_text(request.args.get("operator"))
        limit = parse_limit(request.args.get("limit"), 100, 1, 5000)

        all_rows = state["datasets"].get("bus_reference", {}).get("results", [])
        records = [
            {
                "id": row.get("id"),
                "name": row.get("name"),
                "noc": row.get("noc"),
                "operator_name": row.get("operatorName"),
                "status": row.get("status"),
                "description": row.get("description"),
                "lines": row.get("lines", []),
                "localities": row.get("localities", []),
                "created": row.get("created"),
                "modified": row.get("modified"),
            }
            for row in all_rows
        ]

        if operator:
            records = [row for row in records if operator in normalize_text(row.get("operator_name"))]
        if line:
            records = [
                row
                for row in records
                if any(line in normalize_text(item) for item in row.get("lines", []))
            ]

        records = records[:limit]
        return jsonify({"status": "success", "count": len(records), "data": records})

    @app.route("/api/bus/stops", methods=["GET"])
    def bus_stops():
        stops = get_or_parse_stops(state)
        q = normalize_text(request.args.get("q"))
        town = normalize_text(request.args.get("town"))
        lat_raw = request.args.get("lat")
        lon_raw = request.args.get("lon")
        radius = to_number(request.args.get("radius"), "radius") if request.args.get("radius") else 2.0
        limit = parse_limit(request.args.get("limit"), 100, 1, 2000)

        records = stops
        if q:
            records = [
                row
                for row in records
                if q in normalize_text(f"{row.get('common_name')} {row.get('street') or ''} {row.get('indicator') or ''}")
            ]
        if town:
            records = [row for row in records if town in normalize_text(row.get("town"))]

        if lat_raw is not None and lon_raw is not None:
            lat = to_number(lat_raw, "lat")
            lon = to_number(lon_raw, "lon")
            enriched = []
            for row in records:
                if row.get("latitude") is None or row.get("longitude") is None:
                    continue
                distance = round(haversine_km(lat, lon, row["latitude"], row["longitude"]), 3)
                if distance <= radius:
                    copy = dict(row)
                    copy["distance_km"] = distance
                    enriched.append(copy)
            records = sorted(enriched, key=lambda item: item["distance_km"])

        records = records[:limit]
        return jsonify({"status": "success", "count": len(records), "data": records})
    @app.route("/api/delay_codes", methods=["GET"])
    def delay_codes():
        code = normalize_text(request.args.get("code"))
        records = state["datasets"].get("delay_codes", [])
        if code:
            records = [row for row in records if normalize_text(row.get("Code")) == code]
            if not records:
                raise HttpError(404, f"Code {code.upper()} not found")
        return jsonify({"status": "success", "count": len(records), "data": records})

    @app.route("/api/delay_codes/search", methods=["GET"])
    def delay_codes_search():
        q = normalize_text(request.args.get("q"))
        if not q:
            raise HttpError(400, "q parameter required")
        records = [
            row
            for row in state["datasets"].get("delay_codes", [])
            if q in normalize_text(f"{row.get('Code')} {row.get('Cause')} {row.get('Abbreviation')}")
        ]
        return jsonify({"status": "success", "query": q, "count": len(records), "data": records})

    @app.route("/api/weather", methods=["GET"])
    def weather():
        return jsonify(get_weather_payload(state))

    @app.route("/api/predictions/delay", methods=["GET"])
    def delay_prediction():
        mode_raw = normalize_text(request.args.get("mode") or "train")
        mode = mode_raw if mode_raw in {"train", "bus", "taxi"} else "train"
        return jsonify({"status": "success", "timestamp": now_iso(), "data": compute_delay_risk(state, mode)})

    @app.route("/api/disruptions/vms", methods=["GET"])
    def disruptions_vms():
        data = state["parsed"]["vms"]
        return jsonify({"status": "success", "timestamp": now_iso(), "count": len(data), "data": data})

    @app.route("/api/places/nearby", methods=["GET"])
    def places_nearby():
        lat = to_number(request.args.get("lat"), "lat") if request.args.get("lat") is not None else LANCASTER_STATION_COORDS["lat"]
        lon = to_number(request.args.get("lon"), "lon") if request.args.get("lon") is not None else LANCASTER_STATION_COORDS["lon"]
        radius = to_number(request.args.get("radius"), "radius") if request.args.get("radius") is not None else 1.0
        category = normalize_text(request.args.get("category"))
        limit = parse_limit(request.args.get("limit"), 20, 1, 500)
        fields = request.args.get("fields")

        records = [
            row
            for row in state["parsed"]["places"]["records"]
            if row.get("latitude") is not None and row.get("longitude") is not None
        ]
        if category:
            records = [row for row in records if normalize_text(row.get("category")) == category]

        enriched = []
        for row in records:
            distance = round(haversine_km(lat, lon, row["latitude"], row["longitude"]), 3)
            if distance <= radius:
                copy = dict(row)
                copy["distance_km"] = distance
                enriched.append(copy)

        enriched = sorted(enriched, key=lambda item: item["distance_km"])[:limit]
        enriched = [pick_fields(row, fields) for row in enriched]

        return jsonify(
            {
                "status": "success",
                "lat": lat,
                "lon": lon,
                "radius_km": radius,
                "count": len(enriched),
                "data": enriched,
            }
        )

    @app.route("/api/places/search", methods=["GET"])
    def places_search():
        q = normalize_text(request.args.get("q"))
        if not q:
            raise HttpError(400, "q parameter required")
        category = normalize_text(request.args.get("category"))
        town = normalize_text(request.args.get("town"))
        limit = parse_limit(request.args.get("limit"), 20, 1, 500)
        fields = request.args.get("fields")

        records = state["parsed"]["places"]["records"]
        if category:
            records = [row for row in records if normalize_text(row.get("category")) == category]
        if town:
            records = [row for row in records if town in normalize_text(row.get("town"))]

        scored = []
        for row in records:
            hay = normalize_text(
                f"{row.get('name')} {row.get('cuisine')} {row.get('address')} {row.get('town')} {row.get('category')} {row.get('type')}"
            )
            score = len(q) if q in hay else 0
            if score > 0:
                scored.append((score, row))

        scored.sort(key=lambda item: (item[0], item[1].get("stars") or 0), reverse=True)
        result = [pick_fields(dict(item[1]), fields) for item in scored[:limit]]

        return jsonify({"status": "success", "query": q, "count": len(result), "data": result})

    @app.route("/api/places/semantic", methods=["GET"])
    def places_semantic():
        q_raw = request.args.get("q") or ""
        q = normalize_text(q_raw)
        if not q:
            raise HttpError(400, "q parameter required")

        category = normalize_text(request.args.get("category"))
        limit = parse_limit(request.args.get("limit"), 20, 1, 200)
        threshold = to_number(request.args.get("threshold"), "threshold") if request.args.get("threshold") is not None else 0.2
        fields = request.args.get("fields")

        query_tokens = tokenize(q_raw)
        expanded = semantic_expand_tokens(query_tokens)
        category_hints = infer_category_hints(q_raw)

        records = state["parsed"]["places"]["records"]
        if category:
            records = [row for row in records if normalize_text(row.get("category")) == category]

        ranked = []
        for row in records:
            hay = normalize_text(
                f"{row.get('name')} {row.get('cuisine')} {row.get('address')} {row.get('town')} {row.get('category')} {row.get('type')}"
            )
            match_count = sum(1 for token in expanded if token in hay)
            score = (match_count / len(expanded)) if expanded else 0
            if row.get("category") in category_hints:
                score += 0.2
            score = round(min(score, 1), 3)
            if score >= threshold:
                copy = dict(row)
                copy["relevance_score"] = score
                ranked.append(copy)

        ranked.sort(key=lambda item: (item["relevance_score"], item.get("stars") or 0), reverse=True)
        ranked = [pick_fields(row, fields) for row in ranked[:limit]]

        return jsonify(
            {
                "status": "success",
                "query": q_raw,
                "mode": "semantic",
                "count": len(ranked),
                "data": ranked,
            }
        )
    @app.route("/api/places/categories", methods=["GET"])
    def places_categories():
        data = [
            {"category": category, "count": count}
            for category, count in state["parsed"]["places"]["by_category"].items()
        ]
        data.sort(key=lambda row: row["count"], reverse=True)
        return jsonify({"status": "success", "data": data})

    @app.route("/api/places/stats", methods=["GET"])
    def places_stats():
        by_category = [
            {"category": category, "count": count}
            for category, count in state["parsed"]["places"]["by_category"].items()
        ]
        by_category.sort(key=lambda row: row["count"], reverse=True)

        top_towns = [
            {"town": town, "count": count}
            for town, count in state["parsed"]["places"]["top_towns"].items()
        ]
        top_towns.sort(key=lambda row: row["count"], reverse=True)

        return jsonify(
            {
                "status": "success",
                "data": {
                    "total_places": len(state["parsed"]["places"]["records"]),
                    "by_category": by_category,
                    "top_towns": top_towns[:15],
                },
            }
        )

    @app.route("/api/places/<int:osm_id>", methods=["GET"])
    def place_by_id(osm_id: int):
        found = state["parsed"]["places"]["by_id"].get(osm_id)
        if not found:
            raise HttpError(404, "Place not found")
        return jsonify({"status": "success", "data": found})

    @app.route("/api/taxi/ranks", methods=["GET"])
    def taxi_ranks():
        ranks = state["datasets"].get("lancs_fac", {}).get("transportLinks", {}).get("taxi", {}).get("taxiRanks", [])
        return jsonify({"status": "success", "data": ranks})

    @app.route("/api/taxi/providers", methods=["GET"])
    def taxi_providers():
        providers = state["taxi_providers"]
        return jsonify({"status": "success", "count": len(providers), "data": providers})

    @app.route("/api/taxi/quote", methods=["POST"])
    def taxi_quote():
        body = bad_request_if_missing_body_json()
        pickup_lat = to_number(body.get("pickup_latitude"), "pickup_latitude")
        pickup_lon = to_number(body.get("pickup_longitude"), "pickup_longitude")
        dropoff_lat = to_number(body.get("dropoff_latitude"), "dropoff_latitude")
        dropoff_lon = to_number(body.get("dropoff_longitude"), "dropoff_longitude")
        passengers = to_int(body.get("passengers"), "passengers") if body.get("passengers") is not None else 1
        priority = bool(body.get("priority_pickup"))

        distance = haversine_km(pickup_lat, pickup_lon, dropoff_lat, dropoff_lon)
        eta_minutes = max(3, round((distance / 28) * 60 + (-2 if priority else 2)))

        providers = state["taxi_providers"]
        provider_filter = body.get("provider_id")
        if provider_filter:
            providers = [row for row in providers if row["provider_id"] == str(provider_filter)]
            if not providers:
                raise HttpError(404, "provider_id not found")

        quotes = []
        for provider in providers:
            fare = provider["base_fare_gbp"] + provider["per_km_gbp"] * distance + max(passengers - 1, 0) * 0.8
            if priority:
                fare += 2.5
            quote = {
                "quote_id": next_counter(state["store"], "quote_id"),
                "provider_id": provider["provider_id"],
                "provider_name": provider["name"],
                "pickup_location": {"latitude": pickup_lat, "longitude": pickup_lon},
                "dropoff_location": {"latitude": dropoff_lat, "longitude": dropoff_lon},
                "passengers": passengers,
                "priority_pickup": priority,
                "distance_km": round(distance, 3),
                "estimated_duration_mins": eta_minutes,
                "estimated_fare_gbp": round(fare, 2),
                "created_at": now_iso(),
                "expires_at": (datetime.now(timezone.utc) + timedelta(minutes=5)).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
            }
            quotes.append(quote)

        state["store"]["taxi_quotes"].extend(quotes)
        save_store(state["store_path"], state["store"])

        return jsonify({"status": "success", "count": len(quotes), "data": quotes})

    @app.route("/api/taxi/bookings", methods=["POST"])
    def taxi_bookings_create():
        body = bad_request_if_missing_body_json()
        quote_id = to_int(body.get("quote_id"), "quote_id") if body.get("quote_id") is not None else None

        quote = None
        if quote_id is not None:
            quote = next((row for row in state["store"]["taxi_quotes"] if row["quote_id"] == quote_id), None)
            if not quote:
                raise HttpError(404, "quote_id not found")

        journey_id = to_int(body.get("journey_id"), "journey_id") if body.get("journey_id") is not None else None
        if journey_id is not None:
            exists = any(row["journey_id"] == journey_id for row in state["store"]["journeys"])
            if not exists:
                raise HttpError(404, "journey_id not found")

        booking = {
            "taxi_id": next_counter(state["store"], "taxi_id"),
            "journey_id": journey_id,
            "quote_id": quote.get("quote_id") if quote else None,
            "provider_id": quote.get("provider_id") if quote else body.get("provider_id"),
            "pickup_location": body.get("pickup_location") or (quote.get("pickup_location") if quote else None),
            "dropoff_location": body.get("dropoff_location") or (quote.get("dropoff_location") if quote else None),
            "booking_time": now_iso(),
            "status": "confirmed",
            "estimated_fare_gbp": quote.get("estimated_fare_gbp") if quote else None,
            "estimated_duration_mins": quote.get("estimated_duration_mins") if quote else None,
        }

        state["store"]["taxi_bookings"].append(booking)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": booking})

    @app.route("/api/taxi/bookings/<int:taxi_id>", methods=["GET"])
    def taxi_booking_get(taxi_id: int):
        booking = next((row for row in state["store"]["taxi_bookings"] if row["taxi_id"] == taxi_id), None)
        if not booking:
            raise HttpError(404, "Taxi booking not found")
        return jsonify({"status": "success", "data": booking})

    @app.route("/api/stations", methods=["GET"])
    def stations():
        q = normalize_text(request.args.get("q"))
        limit = parse_limit(request.args.get("limit"), 100, 1, 1000)

        records = state["parsed"]["train_network"]["stations"]
        if q:
            records = [row for row in records if q in normalize_text(f"{row.get('name')} {row.get('code')}")]

        records = records[:limit]
        return jsonify({"status": "success", "count": len(records), "data": records})

    @app.route("/api/routes/find", methods=["GET"])
    def routes_find():
        from_value = request.args.get("from")
        to_value = request.args.get("to")
        if not from_value or not to_value:
            raise HttpError(400, "from and to parameters are required")

        algorithm_raw = normalize_text(request.args.get("algorithm") or "dijkstra")
        weight_raw = normalize_text(request.args.get("weight") or "duration")
        algorithm = "bfs" if algorithm_raw == "bfs" else "dijkstra"
        weight = "distance" if weight_raw == "distance" else "duration"

        network = state["parsed"]["train_network"]
        from_station = resolve_station(network, from_value)
        to_station = resolve_station(network, to_value)
        if not from_station:
            raise HttpError(404, "Origin station not found")
        if not to_station:
            raise HttpError(404, "Destination station not found")

        if algorithm == "bfs":
            path_ids = bfs_path(build_adjacency(network["segments"], "duration_mins"), from_station["id"], to_station["id"])
        else:
            weight_key = "distance_km" if weight == "distance" else "duration_mins"
            path_ids = dijkstra_path(build_adjacency(network["segments"], weight_key), from_station["id"], to_station["id"])

        if not path_ids:
            raise HttpError(404, "No route found")

        return jsonify({"status": "success", "data": summarize_path(network, path_ids, algorithm, weight)})

    @app.route("/api/users/register", methods=["POST"])
    def users_register():
        body = bad_request_if_missing_body_json()
        name = str(body.get("name") or "").strip()
        email = str(body.get("email") or "").strip().lower()
        password = str(body.get("password") or "")
        accessibility_needs = bool(body.get("accessibility_needs"))
        if not name or not email or not password:
            raise HttpError(400, "name, email, and password are required")

        exists = any(user["email"].lower() == email for user in state["store"]["users"])
        if exists:
            raise HttpError(409, "email already registered")

        user = {
            "user_id": next_counter(state["store"], "user_id"),
            "name": name,
            "email": email,
            "password_hash": hash_password(password),
            "accessibility_needs": accessibility_needs,
            "created_at": now_iso(),
        }
        state["store"]["users"].append(user)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": sanitize_user(user)})

    @app.route("/api/users/login", methods=["POST"])
    def users_login():
        body = bad_request_if_missing_body_json()
        email = str(body.get("email") or "").strip().lower()
        password = str(body.get("password") or "")
        if not email or not password:
            raise HttpError(400, "email and password are required")

        user = next((item for item in state["store"]["users"] if item["email"].lower() == email), None)
        if not user:
            raise HttpError(401, "invalid credentials")
        if user["password_hash"] != hash_password(password):
            raise HttpError(401, "invalid credentials")

        token = random_id("tok")
        state["sessions"][token] = {
            "user_id": user["user_id"],
            "expires_at": datetime.now(timezone.utc) + timedelta(hours=24),
        }

        return jsonify(
            {
                "status": "success",
                "data": {
                    "token": token,
                    "expires_in_seconds": 24 * 60 * 60,
                    "user": sanitize_user(user),
                },
            }
        )
    @app.route("/api/users/<int:user_id>", methods=["GET"])
    def user_get(user_id: int):
        user = next((item for item in state["store"]["users"] if item["user_id"] == user_id), None)
        if not user:
            raise HttpError(404, "User not found")
        return jsonify({"status": "success", "data": sanitize_user(user)})

    @app.route("/api/users/<int:user_id>/journeys", methods=["GET"])
    def user_journeys(user_id: int):
        limit = parse_limit(request.args.get("limit"), 100, 1, 1000)
        journeys = [row for row in state["store"]["journeys"] if row["user_id"] == user_id]
        journeys.sort(key=lambda item: item.get("start_time") or "", reverse=True)
        journeys = journeys[:limit]
        return jsonify({"status": "success", "count": len(journeys), "data": journeys})

    @app.route("/api/journeys", methods=["POST"])
    def journeys_create():
        body = bad_request_if_missing_body_json()
        auth_user = auth_user_from_request(state)

        mode_raw = normalize_text(body.get("mode") or "train")
        mode = mode_raw if mode_raw in {"train", "bus", "taxi", "mixed"} else "train"
        origin = str(body.get("origin") or "").strip()
        destination = str(body.get("destination") or "").strip()
        if not origin or not destination:
            raise HttpError(400, "origin and destination are required")

        body_user_id = to_int(body.get("user_id"), "user_id") if body.get("user_id") is not None else None
        user_id = body_user_id or (auth_user["user_id"] if auth_user else None)
        if not user_id:
            raise HttpError(400, "user_id required (or Bearer token)")

        user_exists = any(item["user_id"] == user_id for item in state["store"]["users"])
        if not user_exists:
            raise HttpError(404, "user_id not found")

        journey_id = next_counter(state["store"], "journey_id")
        start_time = body.get("start_time") or now_iso()
        status = str(body.get("status") or "planned")
        end_time = body.get("end_time")

        journey = {
            "journey_id": journey_id,
            "user_id": user_id,
            "start_time": start_time,
            "end_time": end_time,
            "status": status,
            "mode": mode,
            "origin": origin,
            "destination": destination,
            "created_at": now_iso(),
        }

        if mode in {"train", "mixed"}:
            network = state["parsed"]["train_network"]
            from_station = resolve_station(network, origin)
            to_station = resolve_station(network, destination)
            if from_station and to_station:
                path_ids = dijkstra_path(
                    build_adjacency(network["segments"], "duration_mins"),
                    from_station["id"],
                    to_station["id"],
                )
                if path_ids and len(path_ids) > 1:
                    summary = summarize_path(network, path_ids, "dijkstra", "duration")
                    for idx, leg in enumerate(summary["legs"]):
                        state["store"]["journey_legs"].append(
                            {
                                "leg_id": next_counter(state["store"], "leg_id"),
                                "journey_id": journey_id,
                                "route_id": None,
                                "from_stop": leg["from_stop"],
                                "to_stop": leg["to_stop"],
                                "from_stop_id": leg["from_stop_id"],
                                "to_stop_id": leg["to_stop_id"],
                                "departure_time": start_time if idx == 0 else None,
                                "arrival_time": None,
                                "duration_mins": leg["duration_mins"],
                                "distance_km": leg["distance_km"],
                                "transport_mode": "train",
                            }
                        )
                    journey["estimated_duration_mins"] = summary["total_duration_mins"]
                    journey["estimated_distance_km"] = summary["total_distance_km"]
                    if not journey["end_time"]:
                        journey["end_time"] = (
                            datetime.fromisoformat(start_time.replace("Z", ""))
                            + timedelta(minutes=summary["total_duration_mins"])
                        ).replace(microsecond=0).isoformat() + "Z"

        state["store"]["journeys"].append(journey)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": journey})

    @app.route("/api/journeys/<int:journey_id>", methods=["GET"])
    def journeys_get(journey_id: int):
        journey = next((row for row in state["store"]["journeys"] if row["journey_id"] == journey_id), None)
        if not journey:
            raise HttpError(404, "Journey not found")
        legs = [row for row in state["store"]["journey_legs"] if row["journey_id"] == journey_id]
        copy = dict(journey)
        copy["legs"] = legs
        return jsonify({"status": "success", "data": copy})

    @app.route("/api/journeys/<int:journey_id>/legs", methods=["GET"])
    def journey_legs_get(journey_id: int):
        legs = [row for row in state["store"]["journey_legs"] if row["journey_id"] == journey_id]
        return jsonify({"status": "success", "count": len(legs), "data": legs})

    @app.route("/api/tickets", methods=["POST"])
    def tickets_create():
        body = bad_request_if_missing_body_json()
        journey_id = to_int(body.get("journey_id"), "journey_id")
        if not any(row["journey_id"] == journey_id for row in state["store"]["journeys"]):
            raise HttpError(404, "journey_id not found")

        price = to_number(body.get("price"), "price")
        ticket = {
            "ticket_id": next_counter(state["store"], "ticket_id"),
            "journey_id": journey_id,
            "price": round(price, 2),
            "currency": body.get("currency") or "GBP",
            "ticket_type": body.get("ticket_type") or "standard",
            "seat_info": body.get("seat_info"),
            "purchase_time": now_iso(),
            "status": "issued",
        }
        state["store"]["tickets"].append(ticket)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": ticket})

    @app.route("/api/tickets/<int:ticket_id>", methods=["GET"])
    def ticket_get(ticket_id: int):
        ticket = next((row for row in state["store"]["tickets"] if row["ticket_id"] == ticket_id), None)
        if not ticket:
            raise HttpError(404, "Ticket not found")
        return jsonify({"status": "success", "data": ticket})

    @app.route("/api/payments", methods=["POST"])
    def payments_create():
        body = bad_request_if_missing_body_json()
        ticket_id = to_int(body.get("ticket_id"), "ticket_id")
        ticket = next((row for row in state["store"]["tickets"] if row["ticket_id"] == ticket_id), None)
        if not ticket:
            raise HttpError(404, "ticket_id not found")

        amount = to_number(body.get("amount"), "amount")
        method = str(body.get("method") or "").strip()
        if not method:
            raise HttpError(400, "method is required")

        payment = {
            "payment_id": next_counter(state["store"], "payment_id"),
            "ticket_id": ticket_id,
            "amount": round(amount, 2),
            "method": method,
            "payment_time": now_iso(),
            "status": "completed",
        }
        state["store"]["payments"].append(payment)
        ticket["status"] = "paid"
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": payment})

    @app.route("/api/payments/<int:payment_id>", methods=["GET"])
    def payment_get(payment_id: int):
        payment = next((row for row in state["store"]["payments"] if row["payment_id"] == payment_id), None)
        if not payment:
            raise HttpError(404, "Payment not found")
        return jsonify({"status": "success", "data": payment})

    @app.route("/api/delay_claims", methods=["POST"])
    def delay_claim_create():
        body = bad_request_if_missing_body_json()
        ticket_id = to_int(body.get("ticket_id"), "ticket_id")
        ticket = next((row for row in state["store"]["tickets"] if row["ticket_id"] == ticket_id), None)
        if not ticket:
            raise HttpError(404, "ticket_id not found")

        delay_minutes = to_int(body.get("delay_minutes"), "delay_minutes")
        if delay_minutes >= 60:
            pct = 1
        elif delay_minutes >= 30:
            pct = 0.5
        elif delay_minutes >= 15:
            pct = 0.25
        else:
            pct = 0

        compensation = round(ticket["price"] * pct, 2)
        claim = {
            "claim_id": next_counter(state["store"], "claim_id"),
            "ticket_id": ticket_id,
            "delay_minutes": delay_minutes,
            "compensation_amount": compensation,
            "status": "pending" if compensation > 0 else "rejected",
            "created_at": now_iso(),
        }
        state["store"]["delay_claims"].append(claim)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": claim})

    @app.route("/api/delay_claims/<int:claim_id>", methods=["GET"])
    def delay_claim_get(claim_id: int):
        claim = next((row for row in state["store"]["delay_claims"] if row["claim_id"] == claim_id), None)
        if not claim:
            raise HttpError(404, "Delay claim not found")
        return jsonify({"status": "success", "data": claim})

    @app.route("/api/disruption_reports", methods=["POST"])
    def disruption_report_create():
        body = bad_request_if_missing_body_json()
        user_id = to_int(body.get("user_id"), "user_id")
        if not any(row["user_id"] == user_id for row in state["store"]["users"]):
            raise HttpError(404, "user_id not found")

        description = str(body.get("description") or "").strip()
        if not description:
            raise HttpError(400, "description is required")

        report = {
            "report_id": next_counter(state["store"], "report_id"),
            "user_id": user_id,
            "stop_id": body.get("stop_id"),
            "description": description,
            "report_time": now_iso(),
            "confirmed": False,
        }
        state["store"]["disruption_reports"].append(report)
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": report})

    @app.route("/api/disruption_reports", methods=["GET"])
    def disruption_report_list():
        confirmed_raw = request.args.get("confirmed")
        limit = parse_limit(request.args.get("limit"), 200, 1, 2000)

        records = list(state["store"]["disruption_reports"])
        if confirmed_raw is not None:
            confirmed = normalize_text(confirmed_raw) == "true"
            records = [row for row in records if bool(row.get("confirmed")) == confirmed]

        records.sort(key=lambda item: item.get("report_time") or "", reverse=True)
        records = records[:limit]
        return jsonify({"status": "success", "count": len(records), "data": records})

    @app.route("/api/disruption_reports/<int:report_id>/confirm", methods=["POST"])
    def disruption_report_confirm(report_id: int):
        report = next((row for row in state["store"]["disruption_reports"] if row["report_id"] == report_id), None)
        if not report:
            raise HttpError(404, "Disruption report not found")
        report["confirmed"] = True
        report["confirmed_at"] = now_iso()
        save_store(state["store_path"], state["store"])
        return jsonify({"status": "success", "data": report})

    @app.route("/api/operators", methods=["GET"])
    def operators_list():
        operators: Dict[str, Dict[str, Any]] = {}

        for service in state["parsed"]["departures"].get("services", []):
            code = service.get("operator_code") or service.get("operator") or "UNKNOWN"
            if code not in operators:
                operators[code] = {
                    "operator_id": len(operators) + 1,
                    "name": service.get("operator") or code,
                    "operator_code": code,
                    "type": "rail",
                }

        for row in state["datasets"].get("bus_reference", {}).get("results", []):
            code = row.get("noc") or row.get("operatorName") or str(row.get("id"))
            if code not in operators:
                operators[code] = {
                    "operator_id": len(operators) + 1,
                    "name": row.get("operatorName") or row.get("name") or code,
                    "operator_code": code,
                    "type": "bus",
                }

        records = list(operators.values())
        return jsonify({"status": "success", "count": len(records), "data": records})

    @app.route("/api/vehicles/live", methods=["GET"])
    def vehicles_live():
        line = normalize_text(request.args.get("line"))
        vehicles = [
            {
                "vehicle_id": item.get("vehicle_ref"),
                "operator_id": item.get("operator_ref"),
                "vehicle_type": "bus",
                "line_ref": item.get("line_ref"),
                "location": item.get("location"),
                "recorded_at": item.get("recorded_at"),
            }
            for item in state["parsed"]["bus_live"]
        ]
        if line:
            vehicles = [row for row in vehicles if normalize_text(row.get("line_ref")) == line]
        return jsonify({"status": "success", "count": len(vehicles), "data": vehicles})

    @app.route("/api/vehicles/<vehicle_id>/location", methods=["GET"])
    def vehicle_location(vehicle_id: str):
        target = normalize_text(vehicle_id)
        found = next(
            (item for item in state["parsed"]["bus_live"] if normalize_text(item.get("vehicle_ref")) == target),
            None,
        )
        if not found:
            raise HttpError(404, "Vehicle not found")
        return jsonify(
            {
                "status": "success",
                "data": {
                    "location_id": random_id("loc"),
                    "vehicle_id": found.get("vehicle_ref"),
                    "latitude": found.get("location", {}).get("latitude") if found.get("location") else None,
                    "longitude": found.get("location", {}).get("longitude") if found.get("location") else None,
                    "timestamp": found.get("recorded_at"),
                    "occupancy_level": None,
                },
            }
        )

    return app


def main() -> None:
    app = create_app()
    port = int(os.getenv("PORT", "5000"))
    app.run(host="0.0.0.0", port=port, debug=False)


if __name__ == "__main__":
    main()





