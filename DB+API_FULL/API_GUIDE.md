# DB_Scc REST API - Complete Transport Routing System

## Overview

This is a comprehensive REST API server that provides transport routing functionality using **Dijkstra's shortest-path algorithm**. It integrates data from multiple sources (rail, bus, locations) and exposes RESTful endpoints for front-end applications to consume.

### Key Features
- **Multi-modal routing**: Compute shortest paths between rail stations, bus stops, and interchanges
- **Real-time updates**: STOMP integration for live train movements and timetable updates
- **Comprehensive station data**: Facilities, accessibility, parking, and transport links
- **Standardized JSON responses**: All endpoints return consistent JSON format
- **Thread-safe graph operations**: Concurrent access to transport network graph

## Architecture

```
┌─────────────────────────────────────────────────────┐
│         Frontend Application (iOS/Android)          │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │    REST API Layer (Flask)   │
        │  GET endpoints only         │
        └────────────────┬────────────┘
                         │
        ┌────────────────┴─────────────────┐
        │                                  │
        ▼                                  ▼
    ┌────────────────┐         ┌──────────────────┐
    │ Routing Engine │         │ Real-Time Updates│
    │ (Dijkstra)     │         │ (STOMP Client)   │
    └────────┬───────┘         └──────────────────┘
             │
             ▼
    ┌─────────────────────┐
    │ Transport Graph     │
    │ - Nodes (stations)  │
    │ - Edges (routes)    │
    │ - Caching          │
    └──────────┬──────────┘
               │
        ┌──────┴──────────────────────┐
        │                             │
        ▼                             ▼
    ┌────────────┐          ┌──────────────────┐
    │ Local Data │          │ Remote Feeds     │
    │ (JSON/XML) │          │ (HTTP/STOMP)     │
    └────────────┘          └──────────────────┘
```

## Getting Started

### Installation

```bash
# Install dependencies
pip install -r requirements.txt
```

### Running the Server

```bash
# Development (with debug mode)
python api_server.py

# Production (with gunicorn)
gunicorn -w 4 -b 0.0.0.0:5000 api_server:app
```

The server will start on `http://0.0.0.0:5000`

### Verify Installation

```bash
# Check health
curl http://localhost:5000/health

# List all endpoints
curl http://localhost:5000/api/endpoints
```

## API Reference

### 1. Routing Endpoints

#### Get Shortest Route
```http
GET /api/route?from=LAN&to=PRE&departure_time=17:30
```

**Query Parameters:**
- `from` (required): Starting station CRS code (e.g., `LAN`)
- `to` (required): Destination station CRS code (e.g., `PRE`)
- `departure_time` (optional): Time in HH:MM format for time-aware routing

**Response:**
```json
{
  "status": "success",
  "from": "LAN",
  "to": "PRE",
  "total_time_minutes": 20,
  "num_legs": 1,
  "legs": [
    {
      "from": "LAN",
      "from_name": "Lancaster",
      "to": "PRE",
      "to_name": "Preston",
      "mode": "rail",
      "duration_minutes": 20
    }
  ],
  "path": ["LAN", "PRE"],
  "timestamp": "2026-04-21T14:30:00.123456"
}
```

#### List All Stations
```http
GET /api/stations?type=rail&limit=50
```

**Query Parameters:**
- `type` (optional): Filter by station type (`rail`, `bus`, `interchange`)
- `limit` (optional): Maximum number to return (default: 50)

**Response:**
```json
{
  "status": "success",
  "count": 4,
  "limit": 50,
  "data": [
    {
      "crs": "LAN",
      "name": "Lancaster",
      "type": "rail",
      "lat": 54.0651,
      "lon": -2.8197,
      "connections": 2
    }
  ]
}
```

#### Get Station Info
```http
GET /api/station/LAN
```

**Response:**
```json
{
  "status": "success",
  "crs": "LAN",
  "data": {
    "name": "Lancaster",
    "type": "rail",
    "coordinates": {
      "lat": 54.0651,
      "lon": -2.8197
    },
    "operator": "Network Rail",
    "facilities": { "wheelchair_access": true },
    "connections": 2
  }
}
```

#### Get Graph Statistics
```http
GET /api/graph/stats
```

**Response:**
```json
{
  "status": "success",
  "nodes": {
    "total": 4,
    "rail_stations": 4,
    "bus_stops": 0,
    "other": 0
  },
  "edges": {
    "total": 6,
    "rail": 6,
    "bus": 0,
    "walk": 0
  },
  "last_updated": "2026-04-21T14:25:33.456789"
}
```

### 2. Station Information Endpoints

#### Lancaster Facility Data
```http
GET /api/lancs_fac
GET /api/lancs_fac/accessibility
GET /api/lancs_fac/lifts
GET /api/lancs_fac/ticket_buying
GET /api/lancs_fac/transport_links
GET /api/lancs_fac/cycling
GET /api/lancs_fac/parking
```

### 3. Train Departure Endpoints

#### Get Departures
```http
GET /api/departures?limit=10
```

#### Search Departures
```http
GET /api/departures/search?destination=Manchester&limit=5
```

### 4. Bus Endpoints

#### Live Bus Tracking
```http
GET /api/bus/live?line=74&limit=10
```

#### Specific Bus Location
```http
GET /api/bus/live/location?vehicle=BU25_YSD
```

### 5. Real-Time Update Endpoints

#### Real-Time Status
```http
GET /api/realtime/status
```

**Response:**
```json
{
  "status": "success",
  "realtime": {
    "connected": false,
    "subscriptions": [
      "/topic/TRAIN_MVT_ALL_TOC",
      "/topic/VSTP_ALL"
    ],
    "recent_updates": 0,
    "known_delays": 0
  }
}
```

#### Get Recent Updates
```http
GET /api/realtime/updates?limit=20
```

#### Enable Real-Time Updates
```http
POST /api/realtime/enable
```

**Note:** Requires VPN access to `transport.scc.lancs.ac.uk` port 61613

### 6. Utility Endpoints

#### Health Check
```http
GET /health
```

#### List All Endpoints
```http
GET /api/endpoints
```

## CRS Codes Reference

Common UK railway station CRS codes used in the system:

| Code | Station | Code | Station |
|------|---------|------|---------|
| LAN | Lancaster | PRE | Preston |
| MCV | Manchester Central | GLC | Glasgow Central |
| BPN | Blackpool North | SQU | Stockport |
| MCM | Manchester Airport | LTM | Liverpool Lime Street |

See `/api/endpoints` for a complete list of supported codes.

## Data Integration

### Local Data Files

The system loads the following files from the `DB_Scc` directory:

- `lancs_fac.json` - Lancaster station facilities
- `lancs_station_depart` - Train departures (XML)
- `busLive.xml` - Real-time bus tracking
- `delay-codes.json` - Train delay codes
- `weather.json` - Weather data
- `bplan.txt` - Business plan (timing data)
- Various other transport datasets

### Remote Data Sources

When available, the system can fetch from:

```
http://transport.scc.lancs.ac.uk/

- /rail/departures/{CRS}
- /rail/facilities/{CRS}
- /rail/corpus
- /rail/bplan.txt
- /rail/schedule
- /bus/times/{NOC}
- /bus/live/{NOC}
- /nptg/naptan.xml
- /nptg/nptg.xml
- /weather?lat=54.05&lon=-2.80
```

## Dijkstra's Algorithm Implementation

### Algorithm Details

The `dijkstra_shortest_path()` function implements a priority-queue based Dijkstra's algorithm optimized for the transport network:

1. **Initialization**
   - Set distance to start node = 0
   - Set distance to all other nodes = ∞
   - Initialize priority queue with start node

2. **Main Loop**
   - Pop node with minimum distance from queue
   - Mark as visited
   - For each unvisited neighbor:
     - Calculate alternative distance through current node
     - If shorter than known distance, update and re-queue

3. **Path Reconstruction**
   - Trace back from end node through previous nodes
   - Return complete path and total time

### Time Complexity
- **Best case:** O(n log n) with binary heap
- **Average case:** O((n + m) log n) where m = edges
- **Space complexity:** O(n + m)

### Usage Example

```python
# Compute route from Lancaster to Glasgow
total_time, path_nodes = dijkstra_shortest_path(
    graph_obj=graph,
    start_id='LAN',
    end_id='GLC',
    departure_time=1050  # 17:30 in minutes
)

# total_time: 120 minutes
# path_nodes: ['LAN', 'PRE', 'MCV', 'GLC']
```

## Real-Time Updates (STOMP)

### Enabling Real-Time Feeds

The STOMP client connects to National Rail's real-time feeds when enabled:

```python
# In api_server.py, uncomment to enable:
stomp_client.connect_async()
```

### Subscribed Topics

- `/topic/TRAIN_MVT_ALL_TOC` - Train movements (TRUST feed)
- `/topic/VSTP_ALL` - Very short-term planning updates

### Message Processing

Messages are processed to:
- Track train locations and delays
- Update edge weights in the graph
- Cache recent updates for API queries

### Connection Requirements

- **Host:** transport.scc.lancs.ac.uk
- **Port:** 61613
- **Authentication:** guest/guest
- **Network:** Requires University VPN or campus access

## Caching Strategy

The system implements multi-level caching:

### Graph Caching
- In-memory graph stored globally for fast access
- Nodes indexed by CRS, TIPLOC, STANOX, ATCO codes
- Thread-safe operations with locks

### Data Caching
- Large datasets (CORPUS, BPLAN) cached locally in `.cache/` directory
- Cache validity: 24 hours by default
- Automatic re-download when cache expires

### Query Caching (Optional)
For production deployments, consider caching popular queries:
- Recent routes between common stations
- Departure boards for major hubs

## Frontend Integration Example

### JavaScript/Web

```javascript
// Fetch shortest route
async function getRoute(from, to) {
  const response = await fetch(
    `/api/route?from=${from}&to=${to}`
  );
  const data = await response.json();
  
  if (data.status === 'success') {
    console.log(`Route: ${data.total_time_minutes} minutes`);
    data.legs.forEach(leg => {
      console.log(
        `${leg.from} -> ${leg.to} (${leg.mode}, ${leg.duration_minutes} min)`
      );
    });
  }
}

// Get station information
async function getStation(crs) {
  const response = await fetch(`/api/station/${crs}`);
  const data = await response.json();
  
  if (data.status === 'success') {
    console.log(`${data.data.name}: ${data.data.coordinates}`);
  }
}

// Enable real-time updates
async function enableRealTime() {
  const response = await fetch('/api/realtime/enable', {
    method: 'POST'
  });
  const data = await response.json();
  console.log(data.message);
}
```

### Swift (iOS)

```swift
import Foundation

struct RouteResponse: Codable {
    let status: String
    let from: String
    let to: String
    let total_time_minutes: Int
    let legs: [RouteLeg]
}

struct RouteLeg: Codable {
    let from: String
    let from_name: String
    let to: String
    let to_name: String
    let mode: String
    let duration_minutes: Int
}

func getRoute(from: String, to: String) {
    let url = URL(string: "http://localhost:5000/api/route?from=\(from)&to=\(to)")!
    
    URLSession.shared.dataTask(with: url) { data, _, error in
        guard let data = data else { return }
        let decoder = JSONDecoder()
        let route = try! decoder.decode(RouteResponse.self, from: data)
        
        print("Total time: \(route.total_time_minutes) minutes")
    }.resume()
}
```

## Error Handling

All endpoints return standardized error responses:

```json
{
  "status": "error",
  "message": "Starting station LAN not found"
}
```

**Common HTTP Status Codes:**
- `200` - Success
- `400` - Bad request (missing/invalid parameters)
- `404` - Resource not found
- `500` - Server error

## Performance Considerations

### Large Datasets
- CORPUS: ~7MB, parsed once and cached
- NaPTAN: ~53MB, parsed once and cached
- Schedule: ~122MB, refresh daily

### Query Optimization
- Index nodes by CRS/TIPLOC for O(1) lookup
- Limit route calculations with bounds
- Cache popular routes

### Scalability
- Thread-safe graph for concurrent requests
- Connection pooling for remote API calls
- In-memory storage for fast access

## Troubleshooting

### STOMP Connection Issues
- Verify VPN connection to campus network
- Check firewall allows port 61613
- Verify credentials (guest/guest)

### Missing Station Data
- Ensure data files exist in `DB_Scc/` directory
- Check file permissions
- Verify JSON/XML format

### Slow Route Calculations
- Check graph size with `/api/graph/stats`
- Monitor for circular dependencies in edge definitions
- Consider precomputing common routes

### Memory Issues
- Large XML files require streaming parsing
- Consider paginating results with `limit` parameter
- Monitor cache directory size in `.cache/`

## Testing

### Unit Tests (Example)

```python
import requests

def test_health():
    r = requests.get('http://localhost:5000/health')
    assert r.status_code == 200
    assert r.json()['status'] == 'healthy'

def test_route():
    r = requests.get('http://localhost:5000/api/route?from=LAN&to=PRE')
    assert r.status_code == 200
    assert r.json()['total_time_minutes'] == 20

def test_stations():
    r = requests.get('http://localhost:5000/api/stations')
    assert r.status_code == 200
    assert len(r.json()['data']) > 0
```

## Future Enhancements

1. **Multi-modal Optimization**
   - Weight preferences (faster vs cheaper vs fewer transfers)
   - Accessibility constraints
   - Real-time delay considerations

2. **Advanced Routing**
   - Time-dependent edges for schedule-aware routing
   - Circular route detection
   - Alternative route suggestions (top-k shortest paths)

3. **Analytics**
   - Popular route statistics
   - Usage patterns
   - Performance metrics

4. **Integration**
   - Ticketing system integration
   - Journey planner features
   - Delay notifications

## License and Attribution

This system integrates with data from:
- National Rail (CORPUS, BPLAN, TRUST, TD feeds)
- NaPTAN (bus stops and accessibility data)
- NPTG (transport gazetteer)
- OpenStreetMap (mapping data)

All data sources have respective licenses and usage terms that should be respected.

## Support

For issues or questions:
1. Check `/api/endpoints` for available functionality
2. Review error messages in response
3. Check server logs for debug information
4. Verify data files are present and valid
