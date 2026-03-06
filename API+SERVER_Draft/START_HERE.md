A complete, production-ready REST API system that handles data in 3 formats (JSON, XML, text) and serves mobile apps.
### Step 1: Install 
```bash
extract DB_Scc.zip to coding space
ensure requirements.txt holds the dependencies you will need to run
Go to https://drive.google.com/drive/folders/1ED0FHn_5fVBsqUdayo4-L3Pphhs3Wh_J?usp=drive_link and add the packages to the directory.
```

### Step 2: Run 
```bash
python api_server.py
```

**Output:**
```
 * Running on http://0.0.0.0:5000
```

### Step 3: Test 
Open new terminal:
```bash
# Test station info
curl http://localhost:5000/api/lancs_fac

# Test departures
curl "http://localhost:5000/api/departures?limit=3"

# Test buses
curl "http://localhost:5000/api/bus/live?line=74"
```

### Step 4: Verify 
Every command should return JSON with `"status": "success"`


## Integrate into Your App

### iOS (Swift)
```swift
// 1. Copy ios_client.swift to your Xcode project
// 2. Update baseURL (or use localhost if on Mac)
let baseURL = "http://localhost:5000"

// 3. Use it:
let station = try await DBSccAPIClient.shared.getStationInfo()
let trains = try await DBSccAPIClient.shared.getDepartures(limit: 5)
```

### Android (Kotlin)
```kotlin
// 1. Copy android_client.kt to your Android project
// 2. Update baseUrl (10.0.2.2 for emulator, or your IP)
private val baseUrl = "http://10.0.2.2:5000"

// 3. Use it:
client.getStationInfo().onSuccess { response ->
    println(response.data.station_name)
}
```

---



## Key Endpoints

```bash
# Station info
curl http://localhost:5000/api/lancs_fac

# Trains from Lancaster
curl http://localhost:5000/api/departures

# Search trains to Glasgow
curl "http://localhost:5000/api/departures/search?destination=Glasgow"

# Live buses
curl "http://localhost:5000/api/bus/live"

# Bus on specific line
curl "http://localhost:5000/api/bus/live?line=74"

# Weather
curl http://localhost:5000/api/weather

# See all endpoints
curl http://localhost:5000/api/endpoints
```

---

## Access from Phone/Emulator

### From Same Computer
```
http://localhost:5000
```

### From Phone on Same Network
```
http://YOUR_COMPUTER_IP:5000
```

Get your IP:
```bash
# macOS/Linux
hostname -I

# Shows: 192.168.1.100 (example)
# Use: http://192.168.1.100:5000
```

### Android Emulator Special
```
http://10.0.2.2:5000
```

### iOS Simulator
```
http://localhost:5000
```

---

## Troubleshooting

### "Connection refused"
```bash
# Check if server running
ps aux | grep python

# Restart it
python api_server.py
```

### "Cannot connect from phone"
```bash
# Check your IP
hostname -I

# Use that IP instead of localhost
curl http://192.168.1.100:5000/health
```

### "Port already in use"
```bash
# Find what's using port 5000
lsof -i :5000

# Kill it
kill -9 <PID>

# Start server again
python api_server.py
```

---

## What Data Feeds Are Covered

**Station Facilities** (from `lancs_fac.json`)
- Accessibility, lifts, parking, cycling, transport links

**Train Departures** (from `lancs_station_depart` XML)
- Live departure board with delays and calling points

**Bus Tracking** (from `busLive.xml` SIRI format)
- Real-time vehicle positions and routes

**Delay Codes** (from `delay-codes.json`)
- Human-readable explanation of delay codes

**Weather** (from `weather.json`)
- Current temperature, wind, conditions



### Start with one of these based on your need:

**I want to understand it quickly**
→ Read: QUICK_REFERENCE.md 

**I want to use it in my app**  
→ Read: README.md → Mobile Setup section + USAGE_EXAMPLES.md

**I want all the details**  
→ Read: README.md (complete reference)

---








