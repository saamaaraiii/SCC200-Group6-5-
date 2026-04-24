"""
DB_Scc REST API Server - Complete Transport Routing System
Handles GET requests from iOS (Swift) and Android (APK) clients
Serves data from JSON, XML, and text data streams
Implements Dijkstra's shortest-path algorithm for multi-modal routing
Each function returns standardized JSON responses for client consumption

Architecture:
- Data Layer: Fetches and parses from external datasets
- Normalization Layer: Converts to unified Node/Edge schema
- Graph Layer: Builds and maintains transport network
- Routing Layer: Implements Dijkstra's algorithm
- API Layer: RESTful endpoints for clients
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple, Set
from datetime import datetime, timedelta
import logging
import heapq
import gzip
import requests
import threading
from collections import defaultdict
import pickle
import hashlib
import math
import sqlite3
import time
import os

app = Flask(__name__)
CORS(app)

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Data directory path
DATA_DIR = Path(__file__).parent
CACHE_DIR = DATA_DIR / '.cache'
CACHE_DIR.mkdir(exist_ok=True)

# Configuration
BASE_URL = "http://transport.scc.lancs.ac.uk"
WALKING_DISTANCE_KM = 0.2  # Consider nearby stops as interchangeable
WALKING_TIME_MIN = 5

# ============================================================================
# LANCASHIRE PLACES DATABASE CONFIGURATION
# ============================================================================

# Path to the Lancashire SQLite database
_HERE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get(
    "LANCASHIRE_DB",
    os.path.join(_HERE, "..", "..", "lancashire-elastic", "lancashire.db")
)

# Lancaster station coordinates (default centre point for nearby searches)
LANCCASTER_LAT = 54.0466
LANCCASTER_LON = -2.8007

# Simple in-memory query cache (avoids re-running identical searches)
_places_cache: dict = {}
CACHE_TTL = 300  # seconds

# Global state
class TransportGraph:
    """Thread-safe transport network graph with caching"""
    
    def __init__(self):
        self.nodes: Dict[str, Dict[str, Any]] = {}  # id -> node data
        self.edges: Dict[str, List[Dict[str, Any]]] = defaultdict(list)  # from_id -> edges
        self.node_index = {
            'by_crs': {},  # CRS code -> node_id
            'by_tiploc': {},  # TIPLOC -> node_id
            'by_stanox': {},  # STANOX -> node_id
            'by_atco': {},  # ATCO -> node_id
            'by_location': {}  # (lat, lon) -> [node_ids]
        }
        self.lock = threading.RLock()
        self.last_update = None
        self.cache_hash = None
    
    def add_node(self, node_id: str, node_data: Dict[str, Any]):
        """Add a node to the graph"""
        with self.lock:
            self.nodes[node_id] = node_data
            
            # Index by various identifiers
            if 'crs' in node_data:
                self.node_index['by_crs'][node_data['crs']] = node_id
            if 'tiploc' in node_data:
                self.node_index['by_tiploc'][node_data['tiploc']] = node_id
            if 'stanox' in node_data:
                self.node_index['by_stanox'][node_data['stanox']] = node_id
            if 'atco' in node_data:
                self.node_index['by_atco'][node_data['atco']] = node_id
            if 'lat' in node_data and 'lon' in node_data:
                loc_key = (round(node_data['lat'], 5), round(node_data['lon'], 5))
                if loc_key not in self.node_index['by_location']:
                    self.node_index['by_location'][loc_key] = []
                self.node_index['by_location'][loc_key].append(node_id)
    
    def add_edge(self, from_id: str, to_id: str, weight: float, mode: str, metadata: Dict[str, Any] = None):
        """Add a weighted edge between two nodes"""
        with self.lock:
            if from_id not in self.nodes or to_id not in self.nodes:
                logger.warning(f"Edge references non-existent nodes: {from_id} -> {to_id}")
                return
            
            edge = {
                'to': to_id,
                'weight': weight,
                'mode': mode,
                'metadata': metadata or {}
            }
            self.edges[from_id].append(edge)
    
    def get_node(self, node_id: str) -> Optional[Dict[str, Any]]:
        """Get node by ID"""
        with self.lock:
            return self.nodes.get(node_id)
    
    def find_node_by_crs(self, crs: str) -> Optional[str]:
        """Find node ID by CRS code"""
        with self.lock:
            return self.node_index['by_crs'].get(crs.upper())
    
    def find_node_by_tiploc(self, tiploc: str) -> Optional[str]:
        """Find node ID by TIPLOC"""
        with self.lock:
            return self.node_index['by_tiploc'].get(tiploc.upper())
    
    def find_nearby_nodes(self, lat: float, lon: float, radius_km: float = 0.2) -> List[str]:
        """Find nodes within radius (km) of a coordinate"""
        with self.lock:
            nearby = []
            for other_id, node in self.nodes.items():
                if 'lat' in node and 'lon' in node:
                    dist = haversine_distance(lat, lon, node['lat'], node['lon'])
                    if dist <= radius_km:
                        nearby.append(other_id)
            return nearby
    
    def get_neighbors(self, node_id: str) -> List[Tuple[str, float, str]]:
        """Get outgoing edges from a node as (to_id, weight, mode) tuples"""
        with self.lock:
            return [(e['to'], e['weight'], e['mode']) for e in self.edges.get(node_id, [])]

# Global graph instance
graph = TransportGraph()


# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance between two coordinates in km"""
    R = 6371  # Earth radius in km
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    
    a = math.sin(delta_phi/2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda/2)**2
    c = 2 * math.asin(math.sqrt(a))
    return R * c

def parse_time_string(time_str: str) -> Optional[datetime]:
    """Parse various time formats"""
    if not time_str:
        return None
    
    formats = [
        '%H:%M:%S',
        '%H:%M',
        '%Y-%m-%dT%H:%M:%S',
        '%Y-%m-%dT%H:%M:%SZ',
        '%d/%m/%Y %H:%M:%S'
    ]
    
    for fmt in formats:
        try:
            return datetime.strptime(time_str.strip(), fmt)
        except ValueError:
            continue
    return None

def time_to_minutes(time_str: str) -> Optional[int]:
    """Convert HH:MM time to minutes since midnight"""
    if not time_str:
        return None
    try:
        parts = time_str.split(':')
        return int(parts[0]) * 60 + int(parts[1])
    except:
        return None

def get_cache_path(name: str) -> Path:
    """Get cache file path for a dataset"""
    return CACHE_DIR / f"{name}.cache"

def cache_exists(name: str, max_age_hours: int = 24) -> bool:
    """Check if cache exists and is fresh"""
    cache_file = get_cache_path(name)
    if not cache_file.exists():
        return False
    
    age_seconds = (datetime.now() - datetime.fromtimestamp(cache_file.stat().st_mtime)).total_seconds()
    return age_seconds < (max_age_hours * 3600)


# ============================================================================
# PLACES DATABASE HELPERS
# ============================================================================

def _get_places_db() -> sqlite3.Connection:
    """Get connection to Lancashire database."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def _places_cache_key(*args) -> str:
    """Generate cache key from arguments."""
    return hashlib.md5(json.dumps(args, sort_keys=True).encode()).hexdigest()


def _places_cached(key: str):
    """Retrieve cached result if still valid."""
    entry = _places_cache.get(key)
    if entry and time.time() - entry["ts"] < CACHE_TTL:
        return entry["data"]
    return None


def _places_set_cache(key: str, data):
    """Store data in cache with timestamp."""
    _places_cache[key] = {"ts": time.time(), "data": data}


def haversine_simple(lat1, lon1, lat2, lon2) -> float:
    """Calculate distance in km between two points using haversine formula."""
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _rows_to_list(rows, limit=None) -> list:
    """Convert sqlite3.Row objects to list of dicts."""
    result = [dict(r) for r in rows]
    return result[:limit] if limit else result


def _fields_filter(rows: list, fields: Optional[str]) -> list:
    """If ?fields=name,town,... is provided, strip other keys."""
    if not fields:
        return rows
    keep = {f.strip() for f in fields.split(",")}
    return [{k: v for k, v in r.items() if k in keep} for r in rows]


# ============================================================================
# DIJKSTRA'S ALGORITHM
# ============================================================================

def dijkstra_shortest_path(
    graph_obj: TransportGraph,
    start_id: str,
    end_id: str,
    departure_time: Optional[int] = None
) -> Tuple[Optional[float], Optional[List[str]]]:
    """
    Find shortest path using Dijkstra's algorithm
    
    Args:
        graph_obj: TransportGraph instance
        start_id: Starting node ID
        end_id: Ending node ID
        departure_time: Departure time in minutes since midnight (optional)
    
    Returns:
        (total_time_minutes, [node_ids_in_path]) or (None, None) if no path found
    """
    
    if start_id not in graph_obj.nodes or end_id not in graph_obj.nodes:
        logger.warning(f"Invalid nodes for dijkstra: {start_id} or {end_id}")
        return None, None
    
    # Distance map: node_id -> min_distance
    dist = {node_id: float('inf') for node_id in graph_obj.nodes}
    dist[start_id] = 0
    
    # Previous node map for path reconstruction
    prev = {}
    
    # Priority queue: (distance, node_id)
    pq = [(0, start_id)]
    visited: Set[str] = set()
    
    while pq:
        current_dist, current_id = heapq.heappop(pq)
        
        if current_id in visited:
            continue
        
        visited.add(current_id)
        
        # Early termination if we reached the destination
        if current_id == end_id:
            break
        
        # Skip if this is not the best path to this node
        if current_dist > dist[current_id]:
            continue
        
        # Relax edges
        for neighbor_id, weight, mode in graph_obj.get_neighbors(current_id):
            if neighbor_id in visited:
                continue
            
            alt_dist = current_dist + weight
            
            if alt_dist < dist[neighbor_id]:
                dist[neighbor_id] = alt_dist
                prev[neighbor_id] = current_id
                heapq.heappush(pq, (alt_dist, neighbor_id))
    
    # Reconstruct path
    if dist[end_id] == float('inf'):
        return None, None
    
    path = []
    current = end_id
    while current in prev:
        path.insert(0, current)
        current = prev[current]
    path.insert(0, start_id)
    
    return dist[end_id], path

# ============================================================================
# DATA FETCHERS AND PARSERS
# ============================================================================

def fetch_data(url: str, timeout: int = 30, decompress: bool = False) -> Optional[bytes]:
    """Fetch data from remote URL with error handling"""
    try:
        response = requests.get(url, timeout=timeout)
        response.raise_for_status()
        data = response.content
        
        if decompress:
            try:
                data = gzip.decompress(data)
            except:
                pass  # Not gzipped
        return data
    except Exception as e:
        logger.error(f"Error fetching {url}: {str(e)}")
        return None

def parse_corpus(corpus_data: str) -> Dict[str, Dict[str, str]]:
    """Parse CORPUS dataset - mapping of locations (TIPLOC, STANOX, CRS)"""
    try:
        locations = {}
        for line in corpus_data.strip().split('\n'):
            if not line.strip() or line.startswith('ZZZZZZZZZZZZZZZZZZZZZZZZZZZZ'):
                continue
            
            # CORPUS format is fixed-width
            tiploc = line[0:8].strip()
            crs = line[8:11].strip()
            stanox = line[11:16].strip()
            name = line[36:60].strip()
            
            if tiploc:
                locations[tiploc] = {
                    'crs': crs,
                    'stanox': stanox,
                    'name': name,
                    'tiploc': tiploc
                }
        
        logger.info(f"Parsed {len(locations)} locations from CORPUS")
        return locations
    except Exception as e:
        logger.error(f"Error parsing CORPUS: {str(e)}")
        return {}

def parse_naptan_xml(xml_data: bytes) -> List[Dict[str, Any]]:
    """Parse NaPTAN XML for bus stop locations"""
    try:
        root = ET.fromstring(xml_data)
        stops = []
        
        # NaPTAN namespace
        ns = {'naptan': 'http://www.naptan.org.uk/'}
        
        for stop_elem in root.findall('.//naptan:StopPoint', ns):
            atco = stop_elem.findtext('naptan:AtcoCode', namespaces=ns)
            common_name = stop_elem.findtext('.//naptan:CommonName', namespaces=ns)
            descriptor = stop_elem.find('.//naptan:Descriptor', ns)
            
            location = stop_elem.find('.//naptan:Location', ns)
            lat = float(location.findtext('naptan:Latitude', namespaces=ns) or 0) if location else 0
            lon = float(location.findtext('naptan:Longitude', namespaces=ns) or 0) if location else 0
            
            stops.append({
                'atco': atco,
                'name': common_name,
                'lat': lat,
                'lon': lon,
                'type': 'bus_stop'
            })
        
        logger.info(f"Parsed {len(stops)} bus stops from NaPTAN")
        return stops
    except Exception as e:
        logger.error(f"Error parsing NaPTAN: {str(e)}")
        return []

def parse_bplan_txt(bplan_data: str) -> Dict[str, List[Dict[str, Any]]]:
    """Parse BPLAN - timing information between locations"""
    try:
        timing_data = defaultdict(list)
        
        for line in bplan_data.strip().split('\n'):
            if not line.strip() or line.startswith('HD'):
                continue
            
            # BPLAN format: timing points with distance and time
            parts = line.split()
            if len(parts) >= 3:
                from_tiploc = parts[0].upper()
                to_tiploc = parts[1].upper()
                
                try:
                    # Try to parse timing (usually HHmm format or minutes)
                    timing_str = parts[2]
                    if len(timing_str) == 4 and timing_str.isdigit():
                        minutes = int(timing_str[:2]) * 60 + int(timing_str[2:])
                    else:
                        minutes = int(timing_str)
                    
                    timing_data[from_tiploc].append({
                        'to': to_tiploc,
                        'time': minutes
                    })
                except:
                    pass
        
        logger.info(f"Parsed {len(timing_data)} timing records from BPLAN")
        return dict(timing_data)
    except Exception as e:
        logger.error(f"Error parsing BPLAN: {str(e)}")
        return {}

def load_local_data():
    """Load all available local data files into the graph"""
    try:
        # Load station facilities
        facilities_file = DATA_DIR / 'lancs_fac.json'
        if facilities_file.exists():
            with open(facilities_file, 'r') as f:
                lancs_data = json.load(f)
                
                # Add Lancaster station node
                graph.add_node('LAN', {
                    'id': 'LAN',
                    'crs': 'LAN',
                    'name': lancs_data.get('name', 'Lancaster'),
                    'type': 'rail_station',
                    'lat': lancs_data.get('latitude', 54.0651),
                    'lon': lancs_data.get('longitude', -2.8197),
                    'operator': lancs_data.get('stationOperator', {}).get('name'),
                    'facilities': lancs_data.get('stationAccessibility', {})
                })
                logger.info("Loaded Lancaster station data")
        
        # Load delay codes
        delay_codes_file = DATA_DIR / 'delay-codes.json'
        if delay_codes_file.exists():
            with open(delay_codes_file, 'r') as f:
                delay_codes = json.load(f)
                logger.info(f"Loaded {len(delay_codes)} delay codes")
        
        # Load weather data
        weather_file = DATA_DIR / 'weather.json'
        if weather_file.exists():
            with open(weather_file, 'r') as f:
                weather = json.load(f)
                logger.info("Loaded weather data")
        
        logger.info("Local data loading complete")
        return True
    except Exception as e:
        logger.error(f"Error loading local data: {str(e)}")
        return False

def build_initial_graph():
    """Build the initial transport graph from available data"""
    try:
        logger.info("Starting graph construction...")
        
        # Add some sample rail stations for testing
        sample_stations = [
            {'id': 'LAN', 'crs': 'LAN', 'name': 'Lancaster', 'lat': 54.0651, 'lon': -2.8197, 'type': 'rail'},
            {'id': 'PRE', 'crs': 'PRE', 'name': 'Preston', 'lat': 53.7606, 'lon': -2.7304, 'type': 'rail'},
            {'id': 'MCV', 'crs': 'MCV', 'name': 'Manchester Central', 'lat': 53.4817, 'lon': -2.2426, 'type': 'rail'},
            {'id': 'GLC', 'crs': 'GLC', 'name': 'Glasgow Central', 'lat': 55.8642, 'lon': -4.2588, 'type': 'rail'},
        ]
        
        for station in sample_stations:
            graph.add_node(station['id'], station)
        
        # Add sample rail edges (time in minutes)
        edges = [
            ('LAN', 'PRE', 20, 'rail'),
            ('PRE', 'LAN', 20, 'rail'),
            ('PRE', 'MCV', 30, 'rail'),
            ('MCV', 'PRE', 30, 'rail'),
            ('MCV', 'GLC', 60, 'rail'),
            ('GLC', 'MCV', 60, 'rail'),
        ]
        
        for from_id, to_id, weight, mode in edges:
            graph.add_edge(from_id, to_id, weight, mode, {'route': f'{from_id}-{to_id}'})
        
        logger.info(f"Built initial graph with {len(graph.nodes)} nodes and {sum(len(e) for e in graph.edges.values())} edges")
        
        # Load local data
        load_local_data()
        
        graph.last_update = datetime.now()
        return True
    except Exception as e:
        logger.error(f"Error building graph: {str(e)}")
        return False

# Build graph on startup
build_initial_graph()


# ============================================================================
# STOMP REAL-TIME UPDATES
# ============================================================================

class STOMPClient:
    """STOMP client for real-time train movement and timetable updates"""
    
    def __init__(self, host: str = "transport.scc.lancs.ac.uk", port: int = 61613):
        self.host = host
        self.port = port
        self.socket = None
        self.connected = False
        self.thread = None
        self.running = False
        self.subscriptions = {}
        self.delays = {}  # station -> delay info
        self.updates = []  # Recent updates log
    
    def connect_async(self):
        """Start STOMP connection in background thread"""
        self.running = True
        self.thread = threading.Thread(target=self._connect_and_subscribe, daemon=True)
        self.thread.start()
        logger.info("STOMP client connection started in background")
    
    def _connect_and_subscribe(self):
        """Connect to STOMP server and subscribe to feeds"""
        try:
            import socket as sock_module
            
            self.socket = sock_module.socket(sock_module.AF_INET, sock_module.SOCK_STREAM)
            self.socket.settimeout(30)
            self.socket.connect((self.host, self.port))
            self.connected = True
            logger.info(f"Connected to STOMP server at {self.host}:{self.port}")
            
            # Send CONNECT frame
            connect_frame = 'CONNECT\nlogin:guest\npasscode:guest\nwait:True\n\n\x00'
            self.socket.send(connect_frame.encode())
            
            # Read CONNECTED response
            response = self.socket.recv(1024).decode()
            if 'CONNECTED' in response:
                logger.info("STOMP authentication successful")
                
                # Subscribe to feeds
                self._subscribe('/topic/TRAIN_MVT_ALL_TOC', 1)
                self._subscribe('/topic/VSTP_ALL', 2)
                
                # Listen for messages
                self._listen()
        
        except Exception as e:
            logger.error(f"STOMP connection error: {str(e)}")
            self.connected = False
    
    def _subscribe(self, destination: str, sub_id: int):
        """Subscribe to a STOMP topic"""
        try:
            subscribe_frame = f'SUBSCRIBE\ndestination:{destination}\nid:{sub_id}\nack:auto\n\n\x00'
            self.socket.send(subscribe_frame.encode())
            self.subscriptions[sub_id] = destination
            logger.info(f"Subscribed to {destination}")
        except Exception as e:
            logger.error(f"Subscription error: {str(e)}")
    
    def _listen(self):
        """Listen for incoming STOMP messages"""
        import socket as sock_module
        buffer = ""
        while self.running and self.connected:
            try:
                data = self.socket.recv(4096).decode('utf-8', errors='ignore')
                if not data:
                    break
                
                buffer += data
                
                # Process complete messages (end with null character)
                while '\x00' in buffer:
                    message, buffer = buffer.split('\x00', 1)
                    if message.strip():
                        self._process_message(message)
            
            except sock_module.timeout:
                continue
            except Exception as e:
                logger.error(f"Listen error: {str(e)}")
                break
        
        self.connected = False
    
    def _process_message(self, message: str):
        """Process incoming STOMP message"""
        try:
            lines = message.split('\n')
            if lines[0] == 'MESSAGE':
                # Parse message body (JSON payload)
                for i, line in enumerate(lines):
                    if line.strip() == '':
                        # Everything after empty line is body
                        body = '\n'.join(lines[i+1:])
                        if body:
                            data = json.loads(body)
                            self._update_graph_from_message(data)
                        break
        except Exception as e:
            logger.debug(f"Message processing error: {str(e)}")
    
    def _update_graph_from_message(self, data: Dict[str, Any]):
        """Update graph based on real-time message"""
        try:
            # Example: Handle TRUST train movement messages
            if isinstance(data, list):
                for msg in data:
                    if msg.get('type') == '0003':  # Train Movement
                        train_id = msg.get('train_id')
                        loc_stanox = msg.get('loc_stanox')
                        
                        # Update delays if present
                        if msg.get('delay_minutes'):
                            self.delays[loc_stanox] = msg['delay_minutes']
                        
                        self.updates.append({
                            'timestamp': datetime.now(),
                            'train_id': train_id,
                            'location': loc_stanox,
                            'delay': msg.get('delay_minutes', 0)
                        })
        
        except Exception as e:
            logger.debug(f"Graph update error: {str(e)}")
    
    def get_recent_updates(self, limit: int = 50) -> List[Dict[str, Any]]:
        """Get recent updates for monitoring"""
        return self.updates[-limit:]
    
    def disconnect(self):
        """Cleanly disconnect from STOMP server"""
        self.running = False
        if self.socket:
            try:
                disconnect_frame = 'DISCONNECT\n\n\x00'
                self.socket.send(disconnect_frame.encode())
                self.socket.close()
            except:
                pass
        self.connected = False

# Initialize STOMP client (disabled by default for local development)
stomp_client = None
try:
    stomp_client = STOMPClient()
    logger.info("STOMP client initialized (disabled by default - enable with VPN access)")
except Exception as e:
    logger.warning(f"STOMP initialization warning: {str(e)}")


# ============================================================================
# LANCASTER FACILITY DATA PARSERS
# ============================================================================

@app.route('/api/lancs_fac', methods=['GET'])
def get_lancs_fac():
    """
    GET /api/lancs_fac
    Returns complete Lancaster station facility information.
    Used by: iOS/Android for displaying station info, accessibility, facilities
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        return jsonify({
            "status": "success",
            "data": {
                "station_name": data.get("name"),
                "crs_code": data.get("crsCode"),
                "nrcc_code": data.get("nationalLocationCode"),
                "location": data.get("location"),
                "address": data.get("address"),
                "operator": data.get("stationOperator", {}).get("name"),
                "staffing_level": data.get("staffingLevel"),
                "alerts": data.get("stationAlerts", [])
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching lancs_fac: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/accessibility', methods=['GET'])
def get_lancs_accessibility():
    """
    GET /api/lancs_fac/accessibility
    Returns accessibility features (step-free, lifts, tactile paving, etc.)
    Used by: iOS/Android accessibility filtering
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        accessibility = data.get("stationAccessibility", {})
        return jsonify({
            "status": "success",
            "data": {
                "step_free_category": accessibility.get("stepFreeCategory", {}).get("category"),
                "tactile_paving": accessibility.get("tactilePaving"),
                "induction_loops": accessibility.get("inductionLoop", {}).get("provision"),
                "wheelchairs_available": accessibility.get("wheelchairsAvailable"),
                "passenger_assistance": accessibility.get("passengerAssistance", []),
                "train_ramp": accessibility.get("trainRamp", {}).get("available"),
                "ticket_barriers": accessibility.get("ticketBarriers", {}).get("available"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching accessibility: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/lifts', methods=['GET'])
def get_lancs_lifts():
    """
    GET /api/lancs_fac/lifts
    Returns lift information and locations
    Used by: iOS/Android for navigation planning
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        lifts_data = data.get("lifts", {})
        return jsonify({
            "status": "success",
            "data": {
                "available": lifts_data.get("available"),
                "statement": lifts_data.get("statement"),
                "lifts_info": lifts_data.get("liftsInfo", [])
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching lifts: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/ticket_buying', methods=['GET'])
def get_lancs_ticket_buying():
    """
    GET /api/lancs_fac/ticket_buying
    Returns ticket office, machines, and online booking info
    Used by: iOS/Android for journey planning
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        tickets = data.get("ticketBuying", {})
        return jsonify({
            "status": "success",
            "data": {
                "ticket_office": tickets.get("ticketOffice", {}),
                "machines_available": tickets.get("ticketMachinesAvailable"),
                "collect_online": tickets.get("collectOnlineBookedTickets", {}),
                "pay_as_you_go": tickets.get("payAsYouGo", {})
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching ticket info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/transport_links', methods=['GET'])
def get_lancs_transport_links():
    """
    GET /api/lancs_fac/transport_links
    Returns onward transport connectivity (bus, taxi, airport, etc.)
    Used by: iOS/Android for multi-modal journey planning
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        transport = data.get("transportLinks", {})
        return jsonify({
            "status": "success",
            "data": {
                "bus": transport.get("bus", {}).get("available"),
                "replacement_bus": transport.get("replacementBus", {}).get("available"),
                "taxi": transport.get("taxi", {}).get("available"),
                "taxi_ranks": transport.get("taxi", {}).get("taxiRanks", []),
                "airport": transport.get("airport", {}).get("available"),
                "underground": transport.get("underground", {}).get("available"),
                "car_hire": transport.get("carHire", {}).get("available"),
                "port": transport.get("port", {}).get("available"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching transport links: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/cycling', methods=['GET'])
def get_lancs_cycling():
    """
    GET /api/lancs_fac/cycling
    Returns cycle storage and hire information
    Used by: iOS/Android for cyclist journey planning
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        cycling = data.get("cycling", {})
        return jsonify({
            "status": "success",
            "data": {
                "storage_available": cycling.get("cycleStorageAvailable"),
                "spaces": cycling.get("spaces", {}).get("numberOfSpaces"),
                "storage_types": cycling.get("typesOfStorage", []),
                "sheltered": cycling.get("sheltered"),
                "cctv": cycling.get("cctv"),
                "location": cycling.get("location"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching cycling info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/parking', methods=['GET'])
def get_lancs_parking():
    """
    GET /api/lancs_fac/parking
    Returns car park information (spaces, accessibility, charges)
    Used by: iOS/Android for parking lookup
    """
    try:
        url = BASE_URL + "/rail/facilities/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch facilities data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        car_parks = data.get("carParks", {})
        return jsonify({
            "status": "success",
            "data": {
                "car_parks": car_parks
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching parking info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# TRAIN DEPARTURE BOARD PARSERS (XML)
# ============================================================================

def parse_xml_departure_board(xml_data: bytes) -> Dict[str, Any]:
    """
    Parse XML departure board and extract train services
    """
    try:
        root = ET.fromstring(xml_data)
        
        # Define namespaces
        namespaces = {
            'lt4': 'http://thalesgroup.com/RTTI/2015-11-27/ldb/types',
            'lt8': 'http://thalesgroup.com/RTTI/2021-11-01/ldb/types',
            'lt5': 'http://thalesgroup.com/RTTI/2016-02-16/ldb/types'
        }
        
        services = []
        
        # Extract station info
        station_name = root.findtext('.//lt4:locationName', namespaces=namespaces)
        crs = root.findtext('.//lt4:crs', namespaces=namespaces)
        
        # Extract all train services
        for service in root.findall('.//lt8:service', namespaces=namespaces):
            std = service.findtext('lt4:std', namespaces=namespaces)
            etd = service.findtext('lt4:etd', namespaces=namespaces)
            platform = service.findtext('lt4:platform', namespaces=namespaces)
            operator = service.findtext('lt4:operator', namespaces=namespaces)
            delay_reason = service.findtext('lt4:delayReason', namespaces=namespaces)
            
            # Extract origin and destination
            origin_elem = service.find('.//lt5:origin', namespaces=namespaces)
            dest_elem = service.find('.//lt5:destination', namespaces=namespaces)
            
            origin = origin_elem.findtext('lt4:location/lt4:locationName', namespaces=namespaces) if origin_elem else None
            destination = dest_elem.findtext('lt4:location/lt4:locationName', namespaces=namespaces) if dest_elem else None
            
            # Extract calling points
            calling_points = []
            for cp in service.findall('.//lt8:callingPoint', namespaces=namespaces):
                calling_points.append({
                    "location": cp.findtext('lt8:locationName', namespaces=namespaces),
                    "scheduled_time": cp.findtext('lt8:st', namespaces=namespaces),
                    "estimated_time": cp.findtext('lt8:et', namespaces=namespaces)
                })
            
            services.append({
                "scheduled_departure": std,
                "estimated_departure": etd,
                "platform": platform,
                "operator": operator,
                "origin": origin,
                "destination": destination,
                "delay_reason": delay_reason,
                "calling_points": calling_points
            })
        
        return {
            "station": station_name,
            "crs": crs,
            "services": services
        }
    except Exception as e:
        logger.error(f"Error parsing XML: {str(e)}")
        raise


@app.route('/api/departures', methods=['GET'])
def get_departures():
    """
    GET /api/departures
    Returns train departures from Lancaster station (from XML feed)
    Query params: ?limit=5 (default: all)
    Used by: iOS/Android for real-time departure info
    """
    try:
        limit = request.args.get('limit', type=int, default=None)
        
        url = BASE_URL + "/rail/departures/LAN"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch departures data"}), 500
        board_data = parse_xml_departure_board(data_bytes)
        
        services = board_data['services']
        if limit:
            services = services[:limit]
        
        return jsonify({
            "status": "success",
            "station": board_data['station'],
            "crs": board_data['crs'],
            "timestamp": datetime.now().isoformat(),
            "count": len(services),
            "data": services
        }), 200
    except Exception as e:
        logger.error(f"Error fetching departures: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/departures/search', methods=['GET'])
def search_departures():
    """
    GET /api/departures/search?destination=Glasgow&limit=3
    Search departures by destination
    Used by: iOS/Android for filtering by destination
    """
    try:
        destination = request.args.get('destination', '', type=str).lower()
        limit = request.args.get('limit', type=int, default=None)
        
        if not destination:
            return jsonify({"status": "error", "message": "destination parameter required"}), 400
        
        board_data = parse_xml_departure_board(DATA_DIR / 'lancs_station_depart')
        
        filtered = [s for s in board_data['services'] if destination in (s.get('destination') or '').lower()]
        
        if limit:
            filtered = filtered[:limit]
        
        return jsonify({
            "status": "success",
            "search_query": destination,
            "count": len(filtered),
            "data": filtered
        }), 200
    except Exception as e:
        logger.error(f"Error searching departures: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# BUS LIVE TRACKING PARSERS (XML)
# ============================================================================

def parse_bus_xml(xml_path: Path) -> List[Dict[str, Any]]:
    """
    Parse SIRI bus live tracking XML
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        namespaces = {
            'siri': 'http://www.siri.org.uk/siri'
        }
        
        vehicles = []
        
        for vehicle in root.findall('.//siri:VehicleActivity', namespaces=namespaces):
            journey = vehicle.find('.//siri:MonitoredVehicleJourney', namespaces=namespaces)
            
            if journey is None:
                continue
            
            location = journey.find('.//siri:VehicleLocation', namespaces=namespaces)
            
            vehicles.append({
                "line_ref": journey.findtext('siri:LineRef', namespaces=namespaces),
                "vehicle_ref": journey.findtext('siri:VehicleRef', namespaces=namespaces),
                "direction": journey.findtext('siri:DirectionRef', namespaces=namespaces),
                "origin_name": journey.findtext('siri:OriginName', namespaces=namespaces),
                "destination_name": journey.findtext('siri:DestinationName', namespaces=namespaces),
                "origin_time": journey.findtext('siri:OriginAimedDepartureTime', namespaces=namespaces),
                "destination_time": journey.findtext('siri:DestinationAimedArrivalTime', namespaces=namespaces),
                "location": {
                    "latitude": float(location.findtext('siri:Latitude', namespaces=namespaces) or 0),
                    "longitude": float(location.findtext('siri:Longitude', namespaces=namespaces) or 0)
                } if location is not None else None,
                "bearing": journey.findtext('siri:Bearing', namespaces=namespaces),
                "recorded_at": vehicle.findtext('siri:RecordedAtTime', namespaces=namespaces)
            })
        
        return vehicles
    except Exception as e:
        logger.error(f"Error parsing bus XML: {str(e)}")
        raise


def parse_naptan(xml_path: Path) -> List[Dict[str, Any]]:
    """
    Parse NaPTAN XML for stop points
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        namespaces = {'naptan': 'http://www.naptan.org.uk/'}
        
        stops = []
        
        for stop in root.findall('.//naptan:StopPoint', namespaces):
            atco = stop.findtext('naptan:AtcoCode', namespaces)
            name = stop.findtext('.//naptan:CommonName', namespaces)
            stops.append({"atco_code": atco, "name": name})
        
        return stops
    except Exception as e:
        logger.error(f"Error parsing naptan: {str(e)}")
        raise


def parse_nptg(xml_path: Path) -> List[Dict[str, Any]]:
    """
    Parse NPTG XML for localities
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        localities = []
        
        for loc in root.findall('.//NptgLocality'):
            code = loc.findtext('NptgLocalityCode')
            name = loc.findtext('Descriptor/CommonName')
            localities.append({"code": code, "name": name})
        
        return localities
    except Exception as e:
        logger.error(f"Error parsing nptg: {str(e)}")
        raise


def parse_vms(xml_data: bytes) -> Dict[str, Any]:
    """
    Parse VMS XML from bytes data
    """
    try:
        root = ET.fromstring(xml_data)
        
        return {
            "version": root.findtext('versionG'),
            "feed": root.findtext('feedDescription'),
            "publication_time": root.findtext('publicationTime')
        }
    except Exception as e:
        logger.error(f"Error parsing vms: {str(e)}")
        raise


@app.route('/api/bus/live', methods=['GET'])
def get_bus_live():
    """
    GET /api/bus/live
    Returns live bus tracking data
    Query params: ?line=74 (optional line filter), ?limit=10
    Used by: iOS/Android for real-time bus tracking
    """
    try:
        line_filter = request.args.get('line', type=str, default=None)
        limit = request.args.get('limit', type=int, default=None)
        
        vehicles = parse_bus_xml(DATA_DIR / 'busLive.xml')
        
        if line_filter:
            vehicles = [v for v in vehicles if v['line_ref'] == line_filter]
        
        if limit:
            vehicles = vehicles[:limit]
        
        return jsonify({
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            "count": len(vehicles),
            "data": vehicles
        }), 200
    except Exception as e:
        logger.error(f"Error fetching bus live data: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/bus/live/location', methods=['GET'])
def get_bus_location():
    """
    GET /api/bus/live/location?vehicle=BU25_YSD
    Returns specific vehicle location
    Used by: iOS/Android for vehicle tracking
    """
    try:
        vehicle_ref = request.args.get('vehicle', type=str, default=None)
        
        if not vehicle_ref:
            return jsonify({"status": "error", "message": "vehicle parameter required"}), 400
        
        vehicles = parse_bus_xml(DATA_DIR / 'busLive.xml')
        vehicle = next((v for v in vehicles if v['vehicle_ref'] == vehicle_ref), None)
        
        if not vehicle:
            return jsonify({"status": "error", "message": "Vehicle not found"}), 404
        
        return jsonify({
            "status": "success",
            "data": vehicle
        }), 200
    except Exception as e:
        logger.error(f"Error fetching bus location: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# DELAY CODES PARSER (JSON)
# ============================================================================

@app.route('/api/delay_codes', methods=['GET'])
def get_delay_codes():
    """
    GET /api/delay_codes
    Returns all delay codes (reason + abbreviation)
    Query params: ?code=IA (search by specific code)
    Used by: iOS/Android for understanding delay reasons
    """
    try:
        code_search = request.args.get('code', type=str, default=None)
        
        url = BASE_URL + "/rail/delay-codes.json"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch delay codes"}), 500
        codes = json.loads(data_bytes.decode('utf-8'))
        
        if code_search:
            codes = [c for c in codes if c['Code'].upper() == code_search.upper()]
            if not codes:
                return jsonify({"status": "error", "message": f"Code {code_search} not found"}), 404
        
        return jsonify({
            "status": "success",
            "count": len(codes),
            "data": codes
        }), 200
    except Exception as e:
        logger.error(f"Error fetching delay codes: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/delay_codes/search', methods=['GET'])
def search_delay_codes():
    """
    GET /api/delay_codes/search?q=signalling
    Search delay codes by keyword
    Used by: iOS/Android for delay reason lookup
    """
    try:
        query = request.args.get('q', type=str, default='').lower()
        
        if not query:
            return jsonify({"status": "error", "message": "q parameter required"}), 400
        
        with open(DATA_DIR / 'delay-codes.json', 'r') as f:
            codes = json.load(f)
        
        results = [c for c in codes if query in c['Cause'].lower() or query in c['Abbreviation'].lower()]
        
        return jsonify({
            "status": "success",
            "query": query,
            "count": len(results),
            "data": results
        }), 200
    except Exception as e:
        logger.error(f"Error searching delay codes: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# WEATHER DATA PARSER (JSON)
# ============================================================================

@app.route('/api/weather', methods=['GET'])
def get_weather():
    """
    GET /api/weather
    Returns current weather information for Lancaster area
    Used by: iOS/Android for weather context
    """
    try:
        url = BASE_URL + "/weather?lat=54.05&lon=-2.80"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch weather data"}), 500
        weather_data = json.loads(data_bytes.decode('utf-8'))
        
        weather = weather_data.get('weather', {})
        main = weather.get('main', {})
        
        return jsonify({
            "status": "success",
            "data": {
                "location": {
                    "name": weather.get('name'),
                    "latitude": weather.get('coord', {}).get('lat'),
                    "longitude": weather.get('coord', {}).get('lon')
                },
                "conditions": {
                    "main": weather.get('weather', [{}])[0].get('main'),
                    "description": weather.get('weather', [{}])[0].get('description'),
                    "icon": weather.get('weather', [{}])[0].get('icon')
                },
                "temperature": {
                    "current": main.get('temp'),
                    "feels_like": main.get('feels_like'),
                    "min": main.get('temp_min'),
                    "max": main.get('temp_max')
                },
                "wind": {
                    "speed": weather.get('wind', {}).get('speed'),
                    "direction": weather.get('wind', {}).get('deg'),
                    "gust": weather.get('wind', {}).get('gust')
                },
                "humidity": main.get('humidity'),
                "visibility": weather.get('visibility'),
                "clouds": weather.get('clouds', {}).get('all'),
                "timestamp": weather.get('dt')
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching weather: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# ADDITIONAL DATA FEEDS
# ============================================================================

@app.route('/api/corpus', methods=['GET'])
def get_corpus():
    """
    GET /api/corpus
    Returns CORPUS station data
    Used by: iOS/Android for station reference
    """
    try:
        url = BASE_URL + "/rail/corpus"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch corpus data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        return jsonify({
            "status": "success",
            "data": data
        }), 200
    except Exception as e:
        logger.error(f"Error fetching corpus: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/smart', methods=['GET'])
def get_smart():
    """
    GET /api/smart
    Returns SMART station data
    Used by: iOS/Android for station reference
    """
    try:
        url = BASE_URL + "/rail/smart"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch smart data"}), 500
        data = json.loads(data_bytes.decode('utf-8'))
        
        return jsonify({
            "status": "success",
            "data": data
        }), 200
    except Exception as e:
        logger.error(f"Error fetching smart: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/naptan', methods=['GET'])
def get_naptan():
    """
    GET /api/naptan
    Returns NaPTAN stop points data
    Used by: iOS/Android for stop reference
    """
    try:
        stops = parse_naptan(DATA_DIR / 'naptan.xml')
        
        return jsonify({
            "status": "success",
            "data": stops
        }), 200
    except Exception as e:
        logger.error(f"Error fetching naptan: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/nptg', methods=['GET'])
def get_nptg():
    """
    GET /api/nptg
    Returns NPTG localities data
    Used by: iOS/Android for locality reference
    """
    try:
        localities = parse_nptg(DATA_DIR / 'nptg.xml')
        
        return jsonify({
            "status": "success",
            "data": localities
        }), 200
    except Exception as e:
        logger.error(f"Error fetching nptg: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/toc', methods=['GET'])
def get_toc():
    """
    GET /api/toc
    Returns TOC (Train Operating Company) data
    Used by: iOS/Android for TOC reference
    """
    try:
        data = []
        with open(DATA_DIR / 'toc-full', 'r') as f:
            for line in f:
                line = line.strip()
                if line:
                    data.append(json.loads(line))
        
        return jsonify({
            "status": "success",
            "data": data
        }), 200
    except Exception as e:
        logger.error(f"Error fetching toc: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/bplan', methods=['GET'])
def get_bplan():
    """
    GET /api/bplan
    Returns business plan data
    Used by: iOS/Android for schedule reference
    """
    try:
        url = BASE_URL + "/rail/bplan.txt"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch bplan data"}), 500
        data = data_bytes.decode('utf-8')
        
        return jsonify({
            "status": "success",
            "data": data
        }), 200
    except Exception as e:
        logger.error(f"Error fetching bplan: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/schedule', methods=['GET'])
def get_schedule():
    """
    GET /api/schedule
    Returns schedule data
    Used by: iOS/Android for schedule reference
    """
    try:
        url = BASE_URL + "/rail/schedule"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            return jsonify({"status": "error", "message": "Failed to fetch schedule data"}), 500
        try:
            data = json.loads(data_bytes.decode('utf-8'))
            return jsonify({
                "status": "success",
                "data": data
            }), 200
        except:
            data = data_bytes.decode('utf-8')
            return jsonify({
                "status": "success",
                "data": data
            }), 200
    except Exception as e:
        logger.error(f"Error fetching schedule: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/vms', methods=['GET'])
def get_vms():
    """
    GET /api/vms
    Returns VMS (Variable Message Signs) data from transport.lancs.ac.uk
    Used by: iOS/Android for traffic messages
    """
    try:
        url = BASE_URL + "/road/vms"
        data_bytes = fetch_data(url)
        if data_bytes is None:
            # Fallback to local file if available
            try:
                with open(DATA_DIR / 'vms', 'rb') as f:
                    data_bytes = f.read()
            except:
                return jsonify({"status": "error", "message": "VMS data unavailable"}), 503
        
        vms_data = parse_vms(data_bytes)
        
        return jsonify({
            "status": "success",
            "data": vms_data,
            "source": "transport.lancs.ac.uk",
            "endpoint": "/road/vms"
        }), 200
    except Exception as e:
        logger.error(f"Error fetching vms: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# LANCASHIRE PLACES ENDPOINTS
# ============================================================================

@app.route('/api/places/nearby', methods=['GET'])
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
        radius   = request.args.get("radius",   1.0,              type=float)
        category = request.args.get("category", None,             type=str)
        limit    = request.args.get("limit",    20,               type=int)
        fields   = request.args.get("fields",   None,             type=str)

        cache_key = _places_cache_key("nearby", lat, lon, radius, category, limit)
        cached = _places_cached(cache_key)
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

        try:
            db = _get_places_db()
            rows = db.execute(sql, params).fetchall()
            db.close()
        except:
            rows = []

        results = []
        for r in rows:
            d = haversine_simple(lat, lon, r["latitude"], r["longitude"])
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
        _places_set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        logger.error(f"Error fetching nearby places: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/places/search', methods=['GET'])
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

        cache_key = _places_cache_key("search", q, category, town, limit)
        cached = _places_cached(cache_key)
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

        try:
            db = _get_places_db()
            rows = db.execute(sql, params).fetchall()
            db.close()
        except:
            rows = []

        results = _fields_filter(_rows_to_list(rows), fields)
        payload = {
            "status": "success",
            "query": q,
            "count": len(results),
            "data": results
        }
        _places_set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        logger.error(f"Error searching places: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/places/semantic', methods=['GET'])
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

        cache_key = _places_cache_key("semantic", q, category, limit, threshold)
        cached = _places_cached(cache_key)
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
        _places_set_cache(cache_key, payload)
        return jsonify(payload), 200

    except Exception as e:
        logger.error(f"Error searching places semantically: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/places/categories', methods=['GET'])
def places_categories():
    """
    GET /api/places/categories
    List all places categories with counts.
    """
    try:
        cache_key = _places_cache_key("places_categories")
        cached = _places_cached(cache_key)
        if cached:
            return jsonify(cached), 200

        try:
            db = _get_places_db()
            rows = db.execute(
                "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
            ).fetchall()
            db.close()
        except:
            rows = []

        payload = {
            "status": "success",
            "data": _rows_to_list(rows)
        }
        _places_set_cache(cache_key, payload)
        return jsonify(payload), 200
    except Exception as e:
        logger.error(f"Error fetching places categories: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/places/stats', methods=['GET'])
def places_stats():
    """
    GET /api/places/stats
    Summary of the Lancashire places database.
    """
    try:
        cache_key = _places_cache_key("places_stats")
        cached = _places_cached(cache_key)
        if cached:
            return jsonify(cached), 200

        try:
            db = _get_places_db()
            total = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
            by_cat = _rows_to_list(db.execute(
                "SELECT category, COUNT(*) as count FROM places GROUP BY category ORDER BY count DESC"
            ).fetchall())
            top_towns = _rows_to_list(db.execute(
                "SELECT town, COUNT(*) as count FROM places WHERE town != '' GROUP BY town ORDER BY count DESC LIMIT 10"
            ).fetchall())
            db.close()
        except:
            total = 0
            by_cat = []
            top_towns = []

        payload = {
            "status": "success",
            "data": {
                "total_places": total,
                "by_category": by_cat,
                "top_towns": top_towns,
            }
        }
        _places_set_cache(cache_key, payload)
        return jsonify(payload), 200
    except Exception as e:
        logger.error(f"Error fetching places stats: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# ROUTING ENDPOINTS
# ============================================================================

@app.route('/api/route', methods=['GET'])
def get_shortest_route():
    """
    GET /api/route?from=LAN&to=PRE
    Compute shortest route between two stations using Dijkstra's algorithm
    Query params:
        from (required): Starting station CRS code
        to (required): Destination station CRS code
        departure_time (optional): Time in HH:MM format for time-aware routing
    Returns: Route with intermediate nodes and total travel time
    """
    try:
        from_crs = request.args.get('from', type=str, default='').upper()
        to_crs = request.args.get('to', type=str, default='').upper()
        departure_time_str = request.args.get('departure_time', type=str, default=None)
        
        if not from_crs or not to_crs:
            return jsonify({
                "status": "error",
                "message": "Both 'from' and 'to' CRS codes are required"
            }), 400
        
        # Find node IDs from CRS codes
        start_id = graph.find_node_by_crs(from_crs)
        end_id = graph.find_node_by_crs(to_crs)
        
        if not start_id:
            return jsonify({
                "status": "error",
                "message": f"Starting station {from_crs} not found"
            }), 404
        
        if not end_id:
            return jsonify({
                "status": "error",
                "message": f"Destination station {to_crs} not found"
            }), 404
        
        # Convert departure time if provided
        departure_minutes = None
        if departure_time_str:
            departure_minutes = time_to_minutes(departure_time_str)
        
        # Run Dijkstra
        total_time, path_nodes = dijkstra_shortest_path(
            graph, start_id, end_id, departure_minutes
        )
        
        if path_nodes is None:
            return jsonify({
                "status": "error",
                "message": f"No route found between {from_crs} and {to_crs}"
            }), 404
        
        # Build detailed response
        legs = []
        for i in range(len(path_nodes) - 1):
            from_node_id = path_nodes[i]
            to_node_id = path_nodes[i + 1]
            
            from_node = graph.get_node(from_node_id)
            to_node = graph.get_node(to_node_id)
            
            # Find edge between nodes
            edge_weight = None
            edge_mode = None
            for edge in graph.edges.get(from_node_id, []):
                if edge['to'] == to_node_id:
                    edge_weight = edge['weight']
                    edge_mode = edge['mode']
                    break
            
            legs.append({
                "from": from_node.get('crs', from_node_id) if from_node else from_node_id,
                "from_name": from_node.get('name', '') if from_node else '',
                "to": to_node.get('crs', to_node_id) if to_node else to_node_id,
                "to_name": to_node.get('name', '') if to_node else '',
                "mode": edge_mode or "unknown",
                "duration_minutes": int(edge_weight) if edge_weight else 0
            })
        
        return jsonify({
            "status": "success",
            "from": from_crs,
            "to": to_crs,
            "total_time_minutes": int(total_time) if total_time else 0,
            "num_legs": len(legs),
            "legs": legs,
            "path": [graph.get_node(n).get('crs', n) if graph.get_node(n) else n for n in path_nodes],
            "timestamp": datetime.now().isoformat()
        }), 200
    
    except Exception as e:
        logger.error(f"Error computing route: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/station/<crs>', methods=['GET'])
def get_station_info(crs: str):
    """
    GET /api/station/{CRS}
    Returns station information and metadata
    Used by: iOS/Android for station details, coordinates, and services
    """
    try:
        crs = crs.upper()
        node_id = graph.find_node_by_crs(crs)
        
        if not node_id:
            return jsonify({
                "status": "error",
                "message": f"Station {crs} not found"
            }), 404
        
        node = graph.get_node(node_id)
        
        return jsonify({
            "status": "success",
            "crs": crs,
            "data": {
                "name": node.get('name', ''),
                "type": node.get('type', ''),
                "coordinates": {
                    "lat": node.get('lat', 0),
                    "lon": node.get('lon', 0)
                },
                "operator": node.get('operator', ''),
                "facilities": node.get('facilities', {}),
                "connections": len(graph.edges.get(node_id, []))
            }
        }), 200
    
    except Exception as e:
        logger.error(f"Error fetching station info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/stations', methods=['GET'])
def list_stations():
    """
    GET /api/stations?type=rail&limit=50
    List all available stations/stops
    Query params:
        type (optional): 'rail', 'bus', or 'interchange'
        limit (optional): Maximum number to return
    """
    try:
        station_type = request.args.get('type', type=str, default=None)
        limit = request.args.get('limit', type=int, default=50)
        
        stations = []
        count = 0
        
        for node_id, node in graph.nodes.items():
            if station_type and node.get('type') != station_type:
                continue
            
            stations.append({
                "crs": node.get('crs', ''),
                "name": node.get('name', ''),
                "type": node.get('type', ''),
                "lat": node.get('lat', 0),
                "lon": node.get('lon', 0),
                "connections": len(graph.edges.get(node_id, []))
            })
            
            count += 1
            if count >= limit:
                break
        
        return jsonify({
            "status": "success",
            "count": len(stations),
            "limit": limit,
            "data": stations
        }), 200
    
    except Exception as e:
        logger.error(f"Error listing stations: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/graph/stats', methods=['GET'])
def get_graph_stats():
    """
    GET /api/graph/stats
    Returns graph structure statistics
    Used by: Debugging and monitoring
    """
    try:
        total_edges = sum(len(edges) for edges in graph.edges.values())
        
        rail_nodes = sum(1 for n in graph.nodes.values() if n.get('type') == 'rail')
        bus_nodes = sum(1 for n in graph.nodes.values() if n.get('type') == 'bus_stop')
        rail_edges = sum(1 for edges in graph.edges.values() 
                        for e in edges if e.get('mode') == 'rail')
        bus_edges = sum(1 for edges in graph.edges.values() 
                       for e in edges if e.get('mode') == 'bus')
        
        return jsonify({
            "status": "success",
            "nodes": {
                "total": len(graph.nodes),
                "rail_stations": rail_nodes,
                "bus_stops": bus_nodes,
                "other": len(graph.nodes) - rail_nodes - bus_nodes
            },
            "edges": {
                "total": total_edges,
                "rail": rail_edges,
                "bus": bus_edges,
                "walk": total_edges - rail_edges - bus_edges
            },
            "last_updated": graph.last_update.isoformat() if graph.last_update else None
        }), 200
    
    except Exception as e:
        logger.error(f"Error fetching graph stats: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/realtime/status', methods=['GET'])
def get_realtime_status():
    """
    GET /api/realtime/status
    Returns real-time updates status from STOMP
    """
    try:
        if not stomp_client:
            return jsonify({
                "status": "success",
                "realtime": {
                    "connected": False,
                    "message": "STOMP client not initialized"
                }
            }), 200
        
        return jsonify({
            "status": "success",
            "realtime": {
                "connected": stomp_client.connected,
                "subscriptions": list(stomp_client.subscriptions.values()),
                "recent_updates": len(stomp_client.updates),
                "known_delays": len(stomp_client.delays)
            }
        }), 200
    
    except Exception as e:
        logger.error(f"Error fetching realtime status: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/realtime/updates', methods=['GET'])
def get_realtime_updates():
    """
    GET /api/realtime/updates?limit=20
    Returns recent real-time updates
    Query params:
        limit (optional): Maximum number of updates to return (default: 20)
    """
    try:
        limit = request.args.get('limit', type=int, default=20)
        
        if not stomp_client:
            return jsonify({
                "status": "success",
                "updates": [],
                "message": "STOMP client not available"
            }), 200
        
        updates = stomp_client.get_recent_updates(limit)
        
        return jsonify({
            "status": "success",
            "count": len(updates),
            "limit": limit,
            "updates": [
                {
                    "timestamp": u['timestamp'].isoformat(),
                    "train_id": u.get('train_id'),
                    "location": u.get('location'),
                    "delay_minutes": u.get('delay', 0)
                }
                for u in updates
            ]
        }), 200
    
    except Exception as e:
        logger.error(f"Error fetching realtime updates: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/realtime/enable', methods=['POST'])
def enable_realtime():
    """
    POST /api/realtime/enable
    Enable real-time updates (requires VPN access to transport.scc.lancs.ac.uk)
    """
    try:
        if not stomp_client:
            return jsonify({
                "status": "error",
                "message": "STOMP client not initialized"
            }), 500
        
        if stomp_client.connected:
            return jsonify({
                "status": "success",
                "message": "Real-time updates already enabled"
            }), 200
        
        stomp_client.connect_async()
        
        return jsonify({
            "status": "success",
            "message": "Real-time updates enabled (connecting in background)"
        }), 200
    
    except Exception as e:
        logger.error(f"Error enabling realtime: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/routes', methods=['GET'])
def get_routes():
    """
    Deprecated: Use /api/route instead
    Kept for backward compatibility
    """
    return get_shortest_route()


# ============================================================================
# HEALTH CHECK & STATUS ENDPOINTS
# ============================================================================

@app.route('/health', methods=['GET'])
def health_check():
    """
    GET /health
    Health check endpoint for monitoring
    """
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "version": "1.0"
    }), 200


@app.route('/api/endpoints', methods=['GET'])
def list_endpoints():
    """
    GET /api/endpoints
    Lists all available API endpoints
    Used by: iOS/Android for endpoint discovery
    """
    endpoints = [
        {"method": "GET", "path": "/health", "description": "Health check"},
        {"method": "GET", "path": "/api/route", "description": "Shortest route via Dijkstra", "params": "from (CRS), to (CRS), departure_time (optional)"},
        {"method": "GET", "path": "/api/station/{CRS}", "description": "Station info and coordinates", "params": "CRS in path"},
        {"method": "GET", "path": "/api/stations", "description": "List all stations", "params": "type (optional), limit (optional)"},
        {"method": "GET", "path": "/api/graph/stats", "description": "Graph statistics"},
        {"method": "GET", "path": "/api/lancs_fac", "description": "Lancaster facility data"},
        {"method": "GET", "path": "/api/lancs_fac/accessibility", "description": "Accessibility info"},
        {"method": "GET", "path": "/api/lancs_fac/lifts", "description": "Lift information"},
        {"method": "GET", "path": "/api/lancs_fac/ticket_buying", "description": "Ticket office info"},
        {"method": "GET", "path": "/api/lancs_fac/transport_links", "description": "Transport connectivity"},
        {"method": "GET", "path": "/api/lancs_fac/cycling", "description": "Cycle storage/hire"},
        {"method": "GET", "path": "/api/lancs_fac/parking", "description": "Car park info"},
        {"method": "GET", "path": "/api/departures", "description": "Train departures", "params": "limit (optional)"},
        {"method": "GET", "path": "/api/departures/search", "description": "Search departures by destination", "params": "destination (required), limit (optional)"},
        {"method": "GET", "path": "/api/bus/live", "description": "Live bus tracking", "params": "line (optional), limit (optional)"},
        {"method": "GET", "path": "/api/bus/live/location", "description": "Specific bus location", "params": "vehicle (required)"},
        {"method": "GET", "path": "/api/delay_codes", "description": "All delay codes", "params": "code (optional)"},
        {"method": "GET", "path": "/api/delay_codes/search", "description": "Search delay codes", "params": "q (required)"},
        {"method": "GET", "path": "/api/weather", "description": "Current weather"},
        {"method": "GET", "path": "/api/corpus", "description": "CORPUS station data"},
        {"method": "GET", "path": "/api/smart", "description": "SMART station data"},
        {"method": "GET", "path": "/api/naptan", "description": "NaPTAN stop points"},
        {"method": "GET", "path": "/api/nptg", "description": "NPTG localities"},
        {"method": "GET", "path": "/api/toc", "description": "TOC data"},
        {"method": "GET", "path": "/api/bplan", "description": "Business plan data"},
        {"method": "GET", "path": "/api/schedule", "description": "Schedule data"},
        {"method": "GET", "path": "/api/vms", "description": "VMS data"},
        {"method": "GET", "path": "/api/places/nearby", "description": "Places near a location", "params": "lat, lon, radius, category (optional), limit, fields"},
        {"method": "GET", "path": "/api/places/search", "description": "Keyword search places", "params": "q (required), category, town, limit, fields"},
        {"method": "GET", "path": "/api/places/semantic", "description": "AI semantic search", "params": "q (required), category, limit, threshold, fields"},
        {"method": "GET", "path": "/api/places/categories", "description": "Places categories list"},
        {"method": "GET", "path": "/api/places/stats", "description": "Places database summary"},
        {"method": "GET", "path": "/api/endpoints", "description": "This endpoint list"},
    ]
    return jsonify({"status": "success", "endpoints": endpoints}), 200


# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.errorhandler(404)
def not_found(error):
    return jsonify({"status": "error", "message": "Endpoint not found"}), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({"status": "error", "message": "Internal server error"}), 500


if __name__ == '__main__':
    logger.info("Starting DB_Scc API Server...")
    app.run(host='0.0.0.0', port=5001, debug=False)
