# Architecture & Implementation Guide

## System Components

### 1. Data Layer

#### TransportGraph Class
The core data structure holding the transport network.

```python
class TransportGraph:
    nodes: Dict[str, Dict[str, Any]]  # node_id -> node data
    edges: Dict[str, List[Dict]]      # node_id -> list of edges
    
    # Indexing for fast lookups
    node_index = {
        'by_crs': {},      # CRS -> node_id
        'by_tiploc': {},   # TIPLOC -> node_id
        'by_stanox': {},   # STANOX -> node_id
        'by_atco': {},     # ATCO -> node_id
        'by_location': {}  # (lat, lon) -> [node_ids]
    }
```

**Key Methods:**
- `add_node(node_id, node_data)` - Add station/stop
- `add_edge(from_id, to_id, weight, mode, metadata)` - Add route
- `find_node_by_crs(crs)` - Fast CRS lookup
- `get_neighbors(node_id)` - Get outgoing edges
- `find_nearby_nodes(lat, lon, radius_km)` - Spatial search

### 2. Node Schema

Every transport node has this structure:

```python
node = {
    'id': 'LAN',              # Unique ID in graph
    'crs': 'LAN',             # Railway station code (if rail)
    'tiploc': 'LNCSTR',       # Timing point location code
    'stanox': '12345',        # Operational node identifier
    'atco': 'ATCO_CODE',      # Bus stop code (if bus)
    'name': 'Lancaster',      # Display name
    'type': 'rail',           # rail | bus_stop | interchange
    'lat': 54.0651,           # Latitude (WGS84)
    'lon': -2.8197,           # Longitude (WGS84)
    'operator': 'Network Rail',
    'facilities': {...},      # Accessibility data
    'timezone': 'Europe/London'  # Optional
}
```

### 3. Edge Schema

Every route connection has this structure:

```python
edge = {
    'to': 'PRE',              # Destination node ID
    'weight': 20,             # Time in minutes
    'mode': 'rail',           # rail | bus | walk
    'metadata': {
        'service_id': 'GR45',  # Train/bus service ID
        'operator': 'Network Rail',
        'platform': '3',
        'departure_time': '17:30',  # HH:MM format
        'arrival_time': '17:50',
        'route_name': 'Lancaster-Preston',
        'stops': ['LAN', 'CNF', 'PRE']
    }
}
```

### 4. Data Fetching Pipeline

```python
# Static data (load once, cache 24h)
load_local_data()
parse_corpus()        # Location mappings
parse_naptan_xml()    # Bus stop locations
parse_bplan_txt()     # Timing between stations

# Semi-static (refresh daily)
fetch_data(BASE_URL + '/rail/schedule')  # Daily timetable

# Real-time (STOMP subscriptions)
stomp_client.subscribe('/topic/TRAIN_MVT_ALL_TOC')
stomp_client.subscribe('/topic/VSTP_ALL')
```

### 5. Graph Construction Algorithm

The graph is built from multiple data sources:

```python
def build_initial_graph():
    # 1. Add rail stations from CORPUS
    for tiploc, location in corpus_data.items():
        graph.add_node(location['crs'], {
            'crs': location['crs'],
            'tiploc': tiploc,
            'name': location['name'],
            'lat': get_coords(tiploc),
            'lon': get_coords(tiploc),
            'type': 'rail'
        })
    
    # 2. Add bus stops from NaPTAN
    for stop in naptan_stops:
        graph.add_node(stop['atco'], {
            'atco': stop['atco'],
            'name': stop['name'],
            'lat': stop['lat'],
            'lon': stop['lon'],
            'type': 'bus_stop'
        })
    
    # 3. Add rail edges from BPLAN (timing)
    for from_tiploc, timings in bplan_data.items():
        for timing in timings:
            to_tiploc = timing['to']
            minutes = timing['time']
            graph.add_edge(from_tiploc, to_tiploc, minutes, 'rail')
    
    # 4. Add interchange edges (walking)
    for station in rail_nodes:
        nearby_bus_stops = find_nearby(station, 0.2)
        for bus_stop in nearby_bus_stops:
            walking_time = estimate_walk_time(station, bus_stop)
            graph.add_edge(station, bus_stop, walking_time, 'walk')
            graph.add_edge(bus_stop, station, walking_time, 'walk')
    
    # 5. Add real-time updates from STOMP
    # Updated dynamically as messages arrive
```

### 6. Dijkstra Implementation

**Algorithm Overview:**
```python
def dijkstra_shortest_path(graph_obj, start_id, end_id, departure_time=None):
    # Initialize distances
    dist = {node_id: float('inf') for node_id in graph_obj.nodes}
    dist[start_id] = 0
    prev = {}
    
    # Priority queue: (distance, node_id)
    pq = [(0, start_id)]
    visited = set()
    
    # Main loop
    while pq:
        current_dist, current_id = heapq.heappop(pq)
        
        if current_id in visited:
            continue
        visited.add(current_id)
        
        if current_id == end_id:
            break
        
        # Relax edges
        for neighbor_id, weight, mode in graph_obj.get_neighbors(current_id):
            if neighbor_id not in visited:
                alt_dist = current_dist + weight
                
                if alt_dist < dist[neighbor_id]:
                    dist[neighbor_id] = alt_dist
                    prev[neighbor_id] = current_id
                    heapq.heappush(pq, (alt_dist, neighbor_id))
    
    # Reconstruct path
    path = []
    current = end_id
    while current in prev:
        path.insert(0, current)
        current = prev[current]
    path.insert(0, start_id)
    
    return dist[end_id], path
```

**Optimizations:**
1. Early termination when destination reached
2. Skip already-visited nodes
3. Efficient priority queue with heapq
4. Pre-computed node indices for O(1) lookups

### 7. STOMP Real-Time Integration

The STOMPClient maintains a persistent connection to National Rail feeds:

```
┌─────────────────────────────────────┐
│ STOMP Server (port 61613)           │
│ transport.scc.lancs.ac.uk           │
└──────────────┬──────────────────────┘
               │ SUBSCRIBE
               ▼
    ┌──────────────────────────┐
    │ /topic/TRAIN_MVT_ALL_TOC │
    │ (Train movements)        │
    └──────────────────────────┘
               │
               ▼
    ┌─────────────────────────┐
    │ Message Parser          │
    │ - Extract train_id      │
    │ - Extract location      │
    │ - Extract delay         │
    └──────────┬──────────────┘
               │
               ▼
    ┌─────────────────────────┐
    │ Graph Update            │
    │ - Update edge weights   │
    │ - Mark delays           │
    │ - Cache updates         │
    └─────────────────────────┘
```

### 8. Request Processing Flow

```
Request: GET /api/route?from=LAN&to=GLC

    │
    ▼
┌─────────────────────────┐
│ Validate Input          │
│ - Check CRS codes       │
│ - Parse parameters      │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ Node Resolution         │
│ - Find start node by CRS│
│ - Find end node by CRS  │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ Dijkstra Calculation    │
│ - Priority queue search │
│ - Relax edges           │
│ - Reconstruct path      │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ Response Building       │
│ - Add metadata          │
│ - Format legs           │
│ - Add timestamps        │
└──────────┬──────────────┘
           │
           ▼
    JSON Response
```

## Data Structures in Memory

### Graph Example

```
Nodes:
  LAN { crs: 'LAN', name: 'Lancaster', lat: 54.0651, lon: -2.8197 }
  PRE { crs: 'PRE', name: 'Preston', lat: 53.7606, lon: -2.7304 }
  MCV { crs: 'MCV', name: 'Manchester Central', lat: 53.4817, lon: -2.2426 }

Edges:
  LAN -> [(PRE, 20, 'rail')]
  PRE -> [(LAN, 20, 'rail'), (MCV, 30, 'rail')]
  MCV -> [(PRE, 30, 'rail')]

Indices:
  by_crs: { 'LAN': 'LAN', 'PRE': 'PRE', 'MCV': 'MCV' }
  by_location: {
    (54.0651, -2.8197): ['LAN'],
    (53.7606, -2.7304): ['PRE']
  }
```

### Dijkstra Example

Finding route LAN → MCV:

```
Initial:
  dist = { LAN: 0, PRE: ∞, MCV: ∞ }
  pq = [(0, LAN)]

Step 1: Process LAN
  Relax edge LAN->PRE: dist[PRE] = 20, pq = [(20, PRE)]
  
Step 2: Process PRE
  Relax edge PRE->MCV: dist[MCV] = 50, pq = [(50, MCV)]
  
Step 3: Process MCV (destination reached)
  Break

Result:
  dist[MCV] = 50 minutes
  path = [LAN, PRE, MCV]
```

## Extending the System

### Adding a New Data Source

1. Create a parser function:
```python
def parse_new_datasource(data: bytes) -> List[Dict]:
    """Parse new data format and return normalized records"""
    results = []
    # Parse logic here
    return results
```

2. Add to build pipeline:
```python
def build_initial_graph():
    # ... existing code ...
    
    # Add new data
    new_data = fetch_data('http://example.com/data')
    parsed = parse_new_datasource(new_data)
    
    for record in parsed:
        # Add nodes and edges
        graph.add_node(record['id'], record)
```

3. Create API endpoint:
```python
@app.route('/api/new_endpoint', methods=['GET'])
def get_new_data():
    # Use graph data
    result = graph.get_node('LAN')
    return jsonify({"status": "success", "data": result}), 200
```

### Adding Real-Time Updates

1. Subscribe to new STOMP topic:
```python
stomp_client._subscribe('/topic/NEW_FEED', 3)
```

2. Process messages:
```python
def _process_message(self, message):
    # ... existing code ...
    if message_type == 'NEW_FEED':
        self._update_graph_from_new_message(data)
```

3. Expose via endpoint:
```python
@app.route('/api/new_realtime', methods=['GET'])
def get_new_realtime():
    updates = stomp_client.get_recent_updates()
    return jsonify({"status": "success", "data": updates}), 200
```

### Adding New Routing Constraints

Modify edge weights during calculation:

```python
def dijkstra_with_constraints(graph_obj, start_id, end_id, constraints):
    # Standard dijkstra with modified weights
    for neighbor_id, base_weight, mode in neighbors:
        # Apply constraints
        weight = base_weight
        if constraints.get('avoid_bus') and mode == 'bus':
            weight = float('inf')
        if constraints.get('prefer_rail') and mode == 'rail':
            weight *= 0.8
        # ... rest of algorithm
```

## Performance Optimization Techniques

### 1. Caching Strategy
```python
# Cache frequent queries
route_cache = {}

def get_route_cached(from_crs, to_crs):
    key = f"{from_crs}_{to_crs}"
    if key in route_cache:
        return route_cache[key]
    
    result = dijkstra_shortest_path(...)
    route_cache[key] = result
    return result
```

### 2. Batch Processing
```python
# Process multiple queries efficiently
def batch_routes(queries: List[Tuple[str, str]]) -> List:
    results = []
    for from_crs, to_crs in queries:
        result = dijkstra_shortest_path(...)
        results.append(result)
    return results
```

### 3. Spatial Indexing
```python
# Use spatial index for nearby node searches
from scipy.spatial import KDTree

coordinates = [(n['lat'], n['lon']) for n in graph.nodes.values()]
tree = KDTree(coordinates)

# Find nodes within 0.2 km
nearby = tree.query_ball_point((54.0651, -2.8197), 0.0018)
```

### 4. Connection Pooling
```python
# Reuse HTTP connections
import requests
session = requests.Session()

def fetch_remote_data(url):
    return session.get(url, timeout=30)
```

## Error Handling Strategy

### Input Validation
```python
def validate_crs(crs: str) -> bool:
    if not crs or len(crs) != 3:
        raise ValueError("Invalid CRS code")
    if not graph.find_node_by_crs(crs):
        raise ValueError(f"Station {crs} not found")
    return True
```

### Graceful Degradation
```python
def get_route_with_fallback(from_crs, to_crs):
    try:
        return dijkstra_shortest_path(...)
    except Exception as e:
        logger.warning(f"Route calculation failed: {e}")
        # Return cached result or empty response
        return None, None
```

### Logging and Monitoring
```python
logger.info(f"Route calculated: {from_crs} -> {to_crs}, time={total_time}min")
logger.warning(f"No route found between {from_crs} and {to_crs}")
logger.error(f"Graph update failed: {str(e)}")
```

## Testing Strategies

### Unit Tests
```python
def test_dijkstra_simple_path():
    # Create simple graph
    g = TransportGraph()
    g.add_node('A', {'id': 'A'})
    g.add_node('B', {'id': 'B'})
    g.add_edge('A', 'B', 10, 'rail')
    
    # Test
    time, path = dijkstra_shortest_path(g, 'A', 'B')
    assert time == 10
    assert path == ['A', 'B']
```

### Integration Tests
```python
def test_full_route_calculation():
    # Test with real graph
    response = requests.get('http://localhost:5000/api/route?from=LAN&to=PRE')
    assert response.status_code == 200
    data = response.json()
    assert data['total_time_minutes'] == 20
```

### Load Tests
```python
# Simulate concurrent requests
import concurrent.futures

with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
    futures = [
        executor.submit(requests.get, 'http://localhost:5000/api/route?from=LAN&to=PRE')
        for _ in range(100)
    ]
    for future in concurrent.futures.as_completed(futures):
        assert future.result().status_code == 200
```

## Deployment Considerations

### Production Configuration
```python
# api_server.py
if __name__ == '__main__':
    # Production
    app.run(
        host='0.0.0.0',
        port=5000,
        debug=False,           # Disable debug
        threaded=True          # Enable threading
    )
    
    # Or use gunicorn:
    # gunicorn -w 4 -b 0.0.0.0:5000 api_server:app
```

### Environment Variables
```bash
# .env
FLASK_ENV=production
LOG_LEVEL=INFO
CACHE_TTL=86400
MAX_ROUTE_TIME=1440
```

### Docker Deployment
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY api_server.py .
EXPOSE 5000
CMD ["gunicorn", "-w", "4", "-b", "0.0.0.0:5000", "api_server:app"]
```

## Maintenance Tasks

### Regular Updates
- Daily: Refresh `/rail/schedule` timetable
- Weekly: Update CORPUS and BPLAN datasets
- Monthly: Clear old cache files

### Monitoring
- Check `/api/graph/stats` regularly
- Monitor STOMP connection status
- Track API response times
- Alert on missing data files

### Backup Strategy
- Daily backup of `.cache/` directory
- Version control for API code
- Preserve graph snapshots for rollback

This architecture provides a scalable, maintainable foundation for transport routing while remaining flexible for future enhancements.
