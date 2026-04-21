
# Quick Reference Guide - DB_Scc Transport Routing System

## Getting Started

Ensure all dependencies are installed:

```bash
pip install -r requirements.txt
python api_server.py
curl http://localhost:5000/api/lancs_fac
```

## Core Features

1. **Route Planning** - Dijkstra's algorithm for shortest paths
2. **Multi-modal** - Rail, bus, and walking transfers
3. **Real-time** - STOMP feed integration
4. **REST API** - GET endpoints only
5. **Thread-safe** - Concurrent request handling

## Key Endpoints

### Routing
| Endpoint | Purpose |
|----------|---------|
| `GET /api/route?from=LAN&to=PRE` | Calculate shortest route |
| `GET /api/stations?type=rail` | List all stations |
| `GET /api/station/{CRS}` | Station details & coordinates |
| `GET /api/graph/stats` | Network statistics |

### Station Info
| Endpoint | Returns |
|----------|---------|
| `GET /api/lancs_fac` | Station name, location, operator |
| `GET /api/lancs_fac/accessibility` | Lifts, step-free, assistance |
| `GET /api/lancs_fac/lifts` | Lift availability & locations |
| `GET /api/lancs_fac/ticket_buying` | Ticket office, machines, online |
| `GET /api/lancs_fac/transport_links` | Bus, taxi, airport connectivity |
| `GET /api/lancs_fac/cycling` | Cycle storage locations |
| `GET /api/lancs_fac/parking` | Car park info |

### Trains
| Endpoint | Returns | Params |
|----------|---------|--------|
| `GET /api/departures` | All departures | `?limit=5` |
| `GET /api/departures/search` | Filtered trains | `?destination=Glasgow&limit=3` |

### Buses
| Endpoint | Returns | Params |
|----------|---------|--------|
| `GET /api/bus/live` | All live buses | `?line=74&limit=10` |
| `GET /api/bus/live/location` | Single bus | `?vehicle=BU25_YSD` |

### Reference Data
| Endpoint | Returns | Params |
|----------|---------|--------|
| `GET /api/delay_codes` | All codes | `?code=IA` |
| `GET /api/delay_codes/search` | Filtered codes | `?q=signalling` |

### Other
| Endpoint | Returns |
|----------|---------|
| `GET /health` | Server status |
| `GET /api/weather` | Temperature, wind, clouds |
| `GET /api/endpoints` | All endpoints list |

---

## EXAMPLE REQUESTS

### Get next 5 trains
```bash
curl "http://localhost:5000/api/departures?limit=5"
```

### Search trains to Glasgow
```bash
curl "http://localhost:5000/api/departures/search?destination=Glasgow&limit=3"
```

### Get live buses on line 74
```bash
curl "http://localhost:5000/api/bus/live?line=74"
```

### Find delay code for signal failure
```bash
curl "http://localhost:5000/api/delay_codes/search?q=signal"
```

### Check weather
```bash
curl "http://localhost:5000/api/weather"
```

---

## Mobile apps        

### iOS
```swift
// Get station info
let station = try await DBSccAPIClient.shared.getStationInfo()

// Get departures
let trains = try await DBSccAPIClient.shared.getDepartures(limit: 5)

// Search destination
let glasgow = try await DBSccAPIClient.shared.searchDepartures(
    destination: "Glasgow",
    limit: 3
)
```

### Android
``` java
val client = DBSccAPIClient()

client.getStationInfo().onSuccess { response ->
    println(response.data.station_name)
}

client.getDepartures(limit = 5).onSuccess { response ->
    response.data.forEach { train ->
        println("${train.destination} at ${train.estimated_departure}")
    }
}
```

---

## Response Formatting

All endpoints return:
```json
{
  "status": "success",
  "data": { /* varies by endpoint */ },
  "count": 5,
  "timestamp": "2026-02-24T12:00:00"
}
```

Error responses:
```json
{
  "status": "error",
  "message": "Description"
}
```

---

## Data Types

### Train Service
```json
{
  "scheduled_departure": "12:56",
  "estimated_departure": "13:14",
  "platform": "3",
  "operator": "Avanti West Coast",
  "origin": "London Euston",
  "destination": "Glasgow Central",
  "delay_reason": "...",
  "calling_points": [...]
}
```

### Bus Service
```json
{
  "line_ref": "74",
  "vehicle_ref": "BU25_YSD",
  "location": {
    "latitude": 53.926361,
    "longitude": -3.015988
  },
  "origin_name": "Bus Station",
  "destination_name": "Rossall Point"
}
```

### Delay Code
```json
{
  "Code": "IA",
  "Cause": "Signal failure",
  "Abbreviation": "SIGNAL FLR"
}
```

---

## Config

### Change Server Port
Alter the line that reads
```python
app.run(host='0.0.0.0', port=5000, debug=False) #
```
### Change Client URL
**iOS:**
```swift
let baseURL = "http://192.168.1.100:5000"  // Your IP:port
```

**Android:**
```java
private val baseUrl: String = "http://192.168.1.100:5000"
```

---



## Network Help

| Scenario | URL |
|----------|-----|
| Local (same computer) | `http://localhost:5000` |
| LAN (same network) | `http://192.168.x.x:5000` |
| Emulator (iOS) | `http://localhost:5000` |
| Emulator (Android) | `http://10.0.2.2:5000` |
| Public (ngrok) | `https://abc123.ngrok.io` |
| Public (deployed) | `https://your-domain.com` |

Find your IP:
```bash
# macOS/Linux
ifconfig | grep inet

# Windows
ipconfig

# Get just IPv4
hostname -I
```

---

## Debugging

### Check if server running
```bash
curl http://localhost:5000/health
```

### See server logs
Check terminal where `python api_server.py` is running

### Test single endpoint
```bash
curl "http://localhost:5000/api/endpoints"
```

### Enable Python debug mode
```python
# api_server.py line ~450
app.run(debug=True) #same line that changes port and ip shown above
```

---

## Performance Tips

### for quicker response times alter size
```bash
# Only get 5 items instead of all
curl "http://localhost:5000/api/departures?limit=5"

# Filter specific line
curl "http://localhost:5000/api/bus/live?line=74"
```

### Cache on client
```swift
// Swift: Cache responses for 5 minutes
let cached = NSCache<NSString, NSData>()
```

``` java
// Android: Use Room database for caching
@Entity
data class CachedResponse(
    @PrimaryKey val endpoint: String,
    val data: String,
    val timestamp: Long
)
```

---

## docs i wrote alongside to help u use my code

| File | Purpose |
|------|---------|
| `README.md` | Complete API reference |
| `DEPLOYMENT.md` | Server setup & deployment |
| `IMPLEMENTATION_SUMMARY.md` | System overview |
| `USAGE_EXAMPLES.md` | Real-world code examples |
| `QUICK_REFERENCE.md` | This file |

---

## light debugging

| Issue | Fix |
|-------|-----|
| `Connection refused` | Check if server running: `python api_server.py` |
| `Cannot reach from mobile` | Use IP not localhost: `http://192.168.1.x:5000` |
| `CORS error` | Already enabled - check mobile URL format |
| `XML parse error` | Check file exists: `ls -la lancs_station_depart` |
| `Module not found` | Install dependencies: `pip install -r requirements.txt` |

---


**Server won't start?**
```bash
# Check Python version
python3 --version

# Check port is free
lsof -i :5000

# Try different port
python api_server.py  # Edit api_server.py and change port
```

**Mobile can't connect?**
```bash
# Ping server from emulator 
ping 192.168.1.100

# Test with curl from same network
curl http://192.168.1.100:5000/health

# Use ngrok if needed
ngrok http 5000
```

**Data not updating?**
```bash
# Restart server to reload data files
kill $(lsof -t -i :5000)
python api_server.py
```
---
