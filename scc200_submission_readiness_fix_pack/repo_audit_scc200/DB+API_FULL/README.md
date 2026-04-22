> [!WARNING]
> **Legacy/reference folder (not primary submission runtime).**
> This directory is a historical integrated prototype snapshot.
> For the assessed runtime path, execute `backend/main.py` from the repo root.
> See root `README.md` for current architecture and run instructions.

# A Python Flask-based REST API server that consolidates data from multiple streams (JSON, XML, plain text) serving the DB_Scc transportation data system. This server provides standardized HTTP endpoints for iOS (Swift) and Android (Kotlin) mobile applications.

## Architecture Overview

The system handles three primary data sources with different formats:

| Source | Format | Purpose |
|--------|--------|---------|
| `lancs_fac.json` | JSON | Lancaster station facilities & accessibility |
| `lancs_station_depart` | XML | Train departure board data |
| `busLive.xml` | XML | Real-time bus vehicle tracking (SIRI format) |
| `delay-codes.json` | JSON | Train delay reason codes |
| `weather.json` | JSON | Weather data for Lancaster area |



### Installation

```bash
# Navigate to project directory
# Install dependencies found in requirements.txt
# Run the server
python api_server.py
#then run the client side code module either kt for andrioid or swift for ios
```

Server will start on `http://localhost:5000`

### Verify Server is Running

```bash
curl http://localhost:5000/health
# Expected response:
# {"status":"healthy","timestamp":"2026-02-24T...","version":"1.0"}
```

## API Endpoints

### Health & Discovery

#### `GET /health`
Server health check endpoint

**Response:**
```json
{
  "status": "healthy",
  "timestamp": "2026-02-24T12:30:00...",
  "version": "1.0"
}
```

#### `GET /api/endpoints`
Discover all available API endpoints

**Response:**
```json
{
  "status": "success",
  "endpoints": [
    {
      "method": "GET",
      "path": "/api/lancs_fac",
      "description": "Lancaster facility data"
    },
    ...
  ]
}
```

---

### Station Facility Data (`lancs_fac.json`)

#### `GET /api/lancs_fac`
Complete Lancaster station information

**Query Parameters:** None

**Response:**
```json
{
  "status": "success",
  "data": {
    "station_name": "Lancaster",
    "crs_code": "LAN",
    "location": {
      "latitude": 54.04855789,
      "longitude": -2.807909914
    },
    "address": {...},
    "operator": "Avanti West Coast",
    "staffing_level": "Full Time",
    "alerts": [...]
  }
}
```

#### `GET /api/lancs_fac/accessibility`
Accessibility features and services

**Response:**
```json
{
  "status": "success",
  "data": {
    "step_free_category": "...",
    "tactile_paving": "There are tactile warnings on all platforms",
    "induction_loops": "Most Help Points do have induction loops",
    "wheelchairs_available": false,
    "passenger_assistance": [...],
    "train_ramp": {...},
    "ticket_barriers": {...}
  }
}
```

#### `GET /api/lancs_fac/lifts`
Lift locations and availability

**Response:**
```json
{
  "status": "success",
  "data": {
    "available": true,
    "statement": "There are lifts",
    "lifts_info": [...]
  }
}
```

#### `GET /api/lancs_fac/ticket_buying`
Ticket office, machines, and online booking information

**Response:**
```json
{
  "status": "success",
  "data": {
    "ticket_office": {...},
    "machines_available": true,
    "collect_online": {...},
    "pay_as_you_go": {...}
  }
}
```

#### `GET /api/lancs_fac/transport_links`
Onward transport connectivity (bus, taxi, airport, etc.)

**Response:**
```json
{
  "status": "success",
  "data": {
    "bus": true,
    "replacement_bus": true,
    "taxi": true,
    "taxi_ranks": [...],
    "airport": false,
    "underground": false,
    "car_hire": false,
    "port": false
  }
}
```

#### `GET /api/lancs_fac/cycling`
Cycle storage and hire information

**Response:**
```json
{
  "status": "success",
  "data": {
    "storage_available": true,
    "spaces": 20,
    "storage_types": [...],
    "sheltered": true,
    "cctv": true,
    "location": "..."
  }
}
```

#### `GET /api/lancs_fac/parking`
Car park availability and details

**Response:**
```json
{
  "status": "success",
  "data": {
    "car_parks": {...}
  }
}
```

---

### Train Departures (XML Parse)

#### `GET /api/departures`
Real-time train departures from Lancaster station

**Query Parameters:**
- `limit` (optional, int): Limit number of results

**Example:**
```
GET /api/departures?limit=5
```

**Response:**
```json
{
  "status": "success",
  "station": "Lancaster",
  "crs": "LAN",
  "timestamp": "2026-02-24T12:30:00...",
  "count": 5,
  "data": [
    {
      "scheduled_departure": "12:56",
      "estimated_departure": "13:14",
      "platform": "3",
      "operator": "Avanti West Coast",
      "origin": "London Euston",
      "destination": "Glasgow Central",
      "delay_reason": "This service has been delayed by a fault with the signalling system",
      "calling_points": [
        {
          "location": "Penrith",
          "scheduled_time": "13:31",
          "estimated_time": "13:46"
        },
        ...
      ]
    },
    ...
  ]
}
```

#### `GET /api/departures/search`
Search departures by destination

**Query Parameters:**
- `destination` (required, string): Destination station name
- `limit` (optional, int): Limit number of results

**Example:**
```
GET /api/departures/search?destination=Glasgow&limit=3
```

**Response:**
```json
{
  "status": "success",
  "search_query": "Glasgow",
  "count": 3,
  "data": [...]
}
```

---

### Bus Live Tracking (XML SIRI Format)

#### `GET /api/bus/live`
Real-time live bus vehicle tracking data

**Query Parameters:**
- `line` (optional, string): Filter by bus line number
- `limit` (optional, int): Limit number of results

**Example:**
```
GET /api/bus/live?line=74&limit=5
```

**Response:**
```json
{
  "status": "success",
  "timestamp": "2026-02-24T12:30:00...",
  "count": 5,
  "data": [
    {
      "line_ref": "74",
      "vehicle_ref": "BU25_YSD",
      "direction": "inbound",
      "origin_name": "Bus_Station_7",
      "destination_name": "Rossall_Point",
      "origin_time": "2026-02-23T11:10:00+00:00",
      "destination_time": "2026-02-23T13:00:00+00:00",
      "location": {
        "latitude": 53.926361,
        "longitude": -3.015988
      },
      "bearing": "238",
      "recorded_at": "2026-02-23T12:56:43+00:00"
    },
    ...
  ]
}
```

#### `GET /api/bus/live/location`
Get specific vehicle real-time location

**Query Parameters:**
- `vehicle` (required, string): Vehicle reference code

**Example:**
```
GET /api/bus/live/location?vehicle=BU25_YSD
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "line_ref": "74",
    "vehicle_ref": "BU25_YSD",
    "location": {
      "latitude": 53.926361,
      "longitude": -3.015988
    },
    ...
  }
}
```

---

### Delay Codes (JSON)

#### `GET /api/delay_codes`
All train delay reason codes

**Query Parameters:**
- `code` (optional, string): Search by specific code (e.g., "IA")

**Example:**
```
GET /api/delay_codes?code=IA
```

**Response:**
```json
{
  "status": "success",
  "count": 1,
  "data": [
    {
      "Code": "IA",
      "Cause": "Signal failure (including no fault found)",
      "Abbreviation": "SIGNAL FLR"
    }
  ]
}
```

#### `GET /api/delay_codes/search`
Search delay codes by keyword

**Query Parameters:**
- `q` (required, string): Search query (searches Code, Cause, and Abbreviation)

**Example:**
```
GET /api/delay_codes/search?q=signalling
```

**Response:**
```json
{
  "status": "success",
  "query": "signalling",
  "count": 3,
  "data": [...]
}
```

---

### Weather Data (JSON)

#### `GET /api/weather`
Current weather for Lancaster area

**Response:**
```json
{
  "status": "success",
  "data": {
    "location": {
      "name": "Bolton le Sands",
      "latitude": 54.06,
      "longitude": -2.77
    },
    "conditions": {
      "main": "Clouds",
      "description": "overcast clouds",
      "icon": "04d"
    },
    "temperature": {
      "current": 9.12,
      "feels_like": 6.19,
      "min": 9.12,
      "max": 10.24
    },
    "wind": {
      "speed": 5.81,
      "direction": 357,
      "gust": 5.81
    },
    "humidity": 84,
    "visibility": 10000,
    "clouds": 87,
    "timestamp": 1771851243
  }
}
```

---

## 📱 Mobile Client Implementation

### iOS (Swift)

See `ios_client.swift` for complete implementation.

**Basic Usage:**
```swift
import Foundation

Task {
    do {
        // Get station info
        let station = try await DBSccAPIClient.shared.getStationInfo()
        print("Station: \(station.data.station_name ?? "Unknown")")
        
        // Get next 5 departures
        let departures = try await DBSccAPIClient.shared.getDepartures(limit: 5)
        departures.data.forEach { service in
            print("\(service.destination) at \(service.estimated_departure)")
        }
        
        // Search for trains to Glasgow
        let glasgowTrains = try await DBSccAPIClient.shared.searchDepartures(destination: "Glasgow")
        print("Found \(glasgowTrains.count) trains to Glasgow")
        
        // Get live bus tracking
        let buses = try await DBSccAPIClient.shared.getBusLive(limit: 10)
        print("Active buses: \(buses.count)")
        
    } catch {
        print("Error: \(error.localizedDescription)")
    }
}
```

### Android (Kotlin)

See `android_client.kt` for complete implementation.

**Basic Usage:**
```kotlin
class MainActivity : AppCompatActivity() {
    private val apiClient = DBSccAPIClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // Get station info
            apiClient.getStationInfo().onSuccess { response ->
                println("Station: ${response.data.station_name}")
            }

            // Get departures
            apiClient.getDepartures(limit = 5).onSuccess { response ->
                response.data.forEach { service ->
                    println("${service.origin} -> ${service.destination} at ${service.estimated_departure}")
                }
            }
        }
    }
}
```

---

## Configuration

### Server Configuration

Edit `api_server.py` for production deployment:

```python
if __name__ == '__main__':
    # Development
    app.run(host='0.0.0.0', port=5000, debug=False)
    
    # Production with Gunicorn
    # gunicorn -w 4 -b 0.0.0.0:8000 api_server:app
```

### Mobile Client Configuration

**iOS:**
```swift
let baseURL = "http://your-server-ip:5000"  // Change for production
```

**Android:**
```java
private val baseUrl: String = "http://your-server-ip:5000"  // Change for production
// Use actual IP/domain, not localhost for real devices
```

---

## Data Flow

```
┌─────────────────────────────────────────────────────────┐
│  Multiple Data Sources                                  │
│  ├─ lancs_fac.json (Station facilities)                │
│  ├─ lancs_station_depart (XML Train board)             │
│  ├─ busLive.xml (SIRI Bus tracking)                    │
│  ├─ delay-codes.json (Delay reasons)                  │
│  └─ weather.json (Weather data)                       │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│  Python Flask API Server (api_server.py)               │
│                                                         │
│  ├─ Format Parsing Layer (JSON/XML conversion)        │
│  ├─ Data Validation & Filtering                       │
│  └─ REST Endpoint Layer (Standardized JSON output)    │
└──────────────────────┬──────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
    ┌────────┐    ┌────────┐    ┌────────┐
    │  iOS   │    │Android │    │  Web   │
    │ Client │    │ Client │    │ Client │
    └────────┘    └────────┘    └────────┘
```

---

## Testing

### Using cURL

```bash
# Get station info
curl http://localhost:5000/api/lancs_fac

# Search departures
curl "http://localhost:5000/api/departures/search?destination=Glasgow&limit=3"

# Get live bus data
curl "http://localhost:5000/api/bus/live?line=74&limit=5"

# Search delay codes
curl "http://localhost:5000/api/delay_codes/search?q=signalling"

# Get weather
curl http://localhost:5000/api/weather
```

### Using Python requests

```python
import requests
import json

BASE_URL = "http://localhost:5000"

# Get departures
response = requests.get(f"{BASE_URL}/api/departures", params={"limit": 5})
data = response.json()
print(f"Services: {data['count']}")

# Search buses by line
response = requests.get(f"{BASE_URL}/api/bus/live", params={"line": "74"})
buses = response.json()['data']
for bus in buses:
    print(f"Bus {bus['vehicle_ref']} at ({bus['location']['latitude']}, {bus['location']['longitude']})")
```

---

## Error Handling

All endpoints return standardized error responses:

```json
{
  "status": "error",
  "message": "Description of what went wrong"
}
```

**HTTP Status Codes:**
- `200`: Success
- `400`: Bad Request (missing/invalid parameters)
- `404`: Not Found
- `500`: Internal Server Error

---

## Production Deployment

### Using Gunicorn

```bash
# Install gunicorn
pip install gunicorn

# Run with 4 workers
gunicorn -w 4 -b 0.0.0.0:8000 api_server:app

# With environment variables
export FLASK_ENV=production
gunicorn -w 4 -b 0.0.0.0:8000 api_server:app
```

### Using Docker

```dockerfile
FROM python:3.9-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

EXPOSE 5000
CMD ["gunicorn", "-w", "4", "-b", "0.0.0.0:5000", "api_server:app"]
```

---

## Files Overview

| File | Purpose |
|------|---------|
| `api_server.py` | Main Flask REST API server |
| `ios_client.swift` | Swift client for iOS apps |
| `android_client.kt` | Kotlin client for Android apps |
| `requirements.txt` | Python dependencies |
| `README.md` | This documentation |
| `Usage_EXAMPLES.md`| Basic use exmaples|
|`VISUAL_SUMMARY.md`| Shows flow of data and how the db responds|
|`QUICK_REFERENCE.md`| Useful information to use while implementing my DB code into the project|
---



