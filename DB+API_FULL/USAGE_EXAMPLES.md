# USAGE EXAMPLES - Complete Guide

## Quick Start

### 1. Running the Server

```bash
# Install dependencies
pip install -r requirements.txt

# Start the server (development)
python api_server.py

# Or with gunicorn (production)
gunicorn -w 4 -b 0.0.0.0:5000 api_server:app
```

Server will be available at `http://localhost:5000`

### 2. Running Tests

```bash
# Run all tests automatically
python test_api.py

# Run tests interactively
python test_api.py --interactive

# Test specific API URL
python test_api.py --url http://192.168.1.100:5000
```

## API Usage Examples

### JavaScript / Web

#### Route Planning

```javascript
// Fetch shortest route from Lancaster to Manchester
async function planRoute() {
  const response = await fetch(
    'http://localhost:5000/api/route?from=LAN&to=MCV'
  );
  
  const data = await response.json();
  
  if (data.status === 'success') {
    console.log(`Journey: ${data.total_time_minutes} minutes`);
    
    data.legs.forEach((leg, i) => {
      console.log(`Leg ${i+1}: ${leg.from_name} → ${leg.to_name}`);
      console.log(`  Mode: ${leg.mode}`);
      console.log(`  Duration: ${leg.duration_minutes} minutes`);
    });
  }
}

planRoute();
```

#### Get Station Information

```javascript
async function getStationInfo(crs) {
  const response = await fetch(`http://localhost:5000/api/station/${crs}`);
  const data = await response.json();
  
  if (data.status === 'success') {
    const station = data.data;
    console.log(`Station: ${station.name}`);
    console.log(`Coordinates: (${station.coordinates.lat}, ${station.coordinates.lon})`);
    console.log(`Connections: ${station.connections}`);
  }
}

getStationInfo('LAN');
```

### Python Examples

#### Simple Route Lookup

```python
import requests

def get_route(from_crs, to_crs):
    """Get shortest route between two stations"""
    url = f"http://localhost:5000/api/route?from={from_crs}&to={to_crs}"
    
    response = requests.get(url)
    
    if response.status_code == 200:
        data = response.json()
        if data['status'] == 'success':
            print(f"Route: {from_crs} → {to_crs}")
            print(f"Total time: {data['total_time_minutes']} minutes")
            print(f"Path: {' → '.join(data['path'])}")
            return data
    return None

# Usage
get_route('LAN', 'PRE')
```

#### Graph Analysis

```python
import requests

def analyze_graph():
    """Analyze transport network graph"""
    
    # Get statistics
    stats_response = requests.get("http://localhost:5000/api/graph/stats")
    stats = stats_response.json()['data']
    
    print("Network Statistics:")
    print(f"  Total nodes: {stats['nodes']['total']}")
    print(f"  Total edges: {stats['edges']['total']}")

analyze_graph()
```

### curl / Command Line

#### Route Calculation

```bash
# Simple route
curl -s "http://localhost:5000/api/route?from=LAN&to=PRE" | python -m json.tool

# Pretty-printed output
curl -s "http://localhost:5000/api/route?from=LAN&to=MCV" | jq '.legs[] | "\(.from_name) -> \(.to_name): \(.duration_minutes) min"'
```

#### Station Information

```bash
# Get station details
curl -s "http://localhost:5000/api/station/LAN" | python -m json.tool
```

## Troubleshooting

### Server Won't Start

```bash
# Check if port 5000 is in use
lsof -i :5000

# Try different port
python api_server.py --port 8000
```

### Route Not Found

```bash
# Check if stations exist
curl "http://localhost:5000/api/stations" | grep "LAN"
```

## Performance Tuning

### Use Production Server

```bash
# gunicorn with multiple workers
gunicorn -w 8 -b 0.0.0.0:5000 api_server:app
```
---

## Scenario 1: Train Journey Planner (iOS)

**Goal**: Show user their next 3 trains to Glasgow and check accessibility

```swift
import SwiftUI

struct TrainBoardView: View {
    @State private var departures: [TrainService] = []
    @State private var accessibilityInfo: AccessibilityData? = nil
    @State private var isLoading = true
    @State private var errorMessage: String? = nil
    
    var body: some View {
        NavigationStack {
            VStack {
                if isLoading {
                    ProgressView()
                } else if let error = errorMessage {
                    Text("Error: \(error)")
                        .foregroundColor(.red)
                } else {
                    List(departures, id: \.scheduled_departure) { service in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(service.destination ?? "Unknown")
                                        .font(.headline)
                                    Text(service.origin ?? "")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                                
                                Spacer()
                                
                                VStack(alignment: .trailing) {
                                    Text(service.estimated_departure ?? "On time")
                                        .font(.headline)
                                        .foregroundColor(
                                            service.estimated_departure == "On time" ? .green : .orange
                                        )
                                    Text("Platform \(service.platform ?? "?")")
                                        .font(.caption)
                                }
                            }
                            
                            if let reason = service.delay_reason {
                                Text(reason)
                                    .font(.caption2)
                                    .foregroundColor(.orange)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
            .navigationTitle("Lancaster Departures")
            .onAppear {
                Task {
                    await loadTrains()
                }
            }
        }
    }
    
    private func loadTrains() async {
        do {
            // Get departures to Glasgow
            let response = try await DBSccAPIClient.shared.searchDepartures(
                destination: "Glasgow",
                limit: 3
            )
            
            DispatchQueue.main.async {
                self.departures = response.data
                self.isLoading = false
            }
            
            // Also fetch accessibility info
            let accessResponse = try await DBSccAPIClient.shared.getAccessibilityInfo()
            DispatchQueue.main.async {
                self.accessibilityInfo = accessResponse.data
            }
            
        } catch {
            DispatchQueue.main.async {
                self.errorMessage = error.localizedDescription
                self.isLoading = false
            }
        }
    }
}

#Preview {
    TrainBoardView()
}
```

---

## Scenario 2: Real-Time Bus Tracking (Android)

**Goal**: Show user all buses on lines 74 and 75 on a map

```java
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng

class BusTrackingViewModel : ViewModel() {
    private val apiClient = DBSccAPIClient()
    
    var buses by mutableStateOf<List<BusVehicle>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    
    fun loadBuses() {
        viewModelScope.launch {
            isLoading = true
            
            // Get all buses on line 74 or 75
            val result74 = apiClient.getBusLive(line = "74", limit = 10)
            val result75 = apiClient.getBusLive(line = "75", limit = 10)
            
            result74.onSuccess { response74 ->
                result75.onSuccess { response75 ->
                    buses = (response74.data + response75.data)
                        .filter { it.location != null }
                    isLoading = false
                }
            }.onFailure { err ->
                error = err.message
                isLoading = false
            }
        }
    }
}

@Composable
fun BusTrackingScreen(viewModel: BusTrackingViewModel) {
    val buses by remember { derivedStateOf { viewModel.buses } }
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    
    LaunchedEffect(Unit) {
        viewModel.loadBuses()
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(buses) { bus ->
                    BusCard(bus)
                }
            }
        }
    }
}

@Composable
fun BusCard(bus: BusVehicle) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Line ${bus.line_ref}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${bus.origin_name} → ${bus.destination_name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "Vehicle: ${bus.vehicle_ref}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (bus.location != null) {
                Text(
                    text = "📍 ${bus.location.latitude.format(4)}°, ${bus.location.longitude.format(4)}°",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            if (bus.bearing != null) {
                Text(
                    text = "Direction: ${bus.bearing}°",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Helper extension
fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
```

---

## Scenario 3: Accessibility-Focused Journey (iOS + Python API Call)

**Goal**: Plan journey with accessibility considerations

```python
# Python: Helper function for accessibility filtering
def get_accessible_journey(destination: str, base_url: str = "http://localhost:5000"):
    """
    Get train journey with accessibility information
    """
    import requests
    
    # Get accessibility features at station
    access_resp = requests.get(f"{base_url}/api/lancs_fac/accessibility")
    accessibility = access_resp.json()['data']
    
    # Get departures to destination
    dep_resp = requests.get(
        f"{base_url}/api/departures/search",
        params={"destination": destination, "limit": 5}
    )
    departures = dep_resp.json()['data']
    
    # Get lift info
    lift_resp = requests.get(f"{base_url}/api/lancs_fac/lifts")
    lifts = lift_resp.json()['data']
    
    return {
        "accessibility_at_station": {
            "step_free": accessibility.get("step_free_category"),
            "lifts_available": lifts.get("available"),
            "tactile_paving": accessibility.get("tactile_paving"),
            "assistance": accessibility.get("passenger_assistance")
        },
        "available_trains": [
            {
                "destination": d.get("destination"),
                "departure": d.get("estimated_departure"),
                "platform": d.get("platform"),
                "operator": d.get("operator")
            }
            for d in departures
        ]
    }
```

```swift
// iOS: Display accessible journey options
struct AccessibleJourneyView: View {
    @State private var journey: AccessibleJourney? = nil
    @State private var selectedDestination = "Glasgow"
    
    var body: some View {
        VStack(spacing: 16) {
            // Accessibility Status
            if let journey = journey {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Accessibility at Lancaster")
                        .font(.headline)
                    
                    if journey.accessibility_at_station["lifts_available"] as? Bool == true {
                        Label("Lifts Available", systemImage: "figure.move")
                            .foregroundColor(.green)
                    }
                    
                    if let tactile = journey.accessibility_at_station["tactile_paving"] as? String {
                        Text(tactile)
                            .font(.caption)
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(8)
                
                // Available trains
                List(journey.available_trains, id: \.destination) { train in
                    VStack(alignment: .leading) {
                        Text(train.destination ?? "Unknown")
                            .font(.headline)
                        HStack {
                            Text(train.departure ?? "")
                            Spacer()
                            Text("Platform \(train.platform ?? "?")")
                        }
                        .font(.caption)
                    }
                }
            }
        }
        .onAppear {
            Task {
                await loadAccessibleJourney()
            }
        }
    }
    
    private func loadAccessibleJourney() async {
        do {
            let accessibility = try await DBSccAPIClient.shared.getAccessibilityInfo()
            let departures = try await DBSccAPIClient.shared.searchDepartures(
                destination: selectedDestination
            )
            let lifts = try await DBSccAPIClient.shared.getLiftInfo()
            
            self.journey = AccessibleJourney(
                accessibility_at_station: [
                    "lifts_available": lifts.data.available ?? false,
                    "step_free": accessibility.data.step_free_category ?? "",
                    "tactile_paving": accessibility.data.tactile_paving ?? ""
                ],
                available_trains: departures.data.map { service in
                    TrainInfo(
                        destination: service.destination,
                        departure: service.estimated_departure,
                        platform: service.platform,
                        operator: service.operator
                    )
                }
            )
        } catch {
            print("Error: \(error)")
        }
    }
}

struct AccessibleJourney {
    let accessibility_at_station: [String: Any]
    let available_trains: [TrainInfo]
}

struct TrainInfo {
    let destination: String?
    let departure: String?
    let platform: String?
    let operator: String?
}
```

---

## Scenario 4: Delay Investigation (Web Dashboard)

**Goal**: User sees delay reason in human-readable format

```html
<!DOCTYPE html>
<html>
<head>
    <title>Train Status Dashboard</title>
    <style>
        body { font-family: Arial; margin: 20px; }
        .departure { 
            border: 1px solid #ccc; 
            padding: 10px; 
            margin: 10px 0;
            border-radius: 5px;
        }
        .delayed { background-color: #fff3cd; }
        .on-time { background-color: #d4edda; }
    </style>
</head>
<body>
    <h1>Lancaster Departures</h1>
    <div id="departures"></div>

    <script>
    async function loadDepartures() {
        // Get departures
        const response = await fetch('http://localhost:5000/api/departures');
        const departures = await response.json();
        
        const container = document.getElementById('departures');
        
        for (const service of departures.data) {
            // If there's a delay, get the delay code explanation
            let delayExplanation = '';
            if (service.delay_reason) {
                // Extract delay code from reason if possible
                // Otherwise use the full reason text
                delayExplanation = service.delay_reason;
            }
            
            const isDelayed = service.estimated_departure !== 'On time';
            const statusClass = isDelayed ? 'delayed' : 'on-time';
            
            const html = `
                <div class="departure ${statusClass}">
                    <h3>${service.destination}</h3>
                    <p><strong>From:</strong> ${service.origin}</p>
                    <p><strong>Scheduled:</strong> ${service.scheduled_departure}</p>
                    <p><strong>Estimated:</strong> ${service.estimated_departure}</p>
                    <p><strong>Platform:</strong> ${service.platform}</p>
                    <p><strong>Operator:</strong> ${service.operator}</p>
                    ${delayExplanation ? `<p style="color: red;"><strong>Status:</strong> ${delayExplanation}</p>` : ''}
                </div>
            `;
            container.innerHTML += html;
        }
    }
    
    loadDepartures();
    </script>
</body>
</html>
```

---

## Scenario 5: Multi-Modal Journey Assistant (Android)

**Goal**: Show buses, trains, and facilities for complete journey

```kotlin
class JourneyAssistantViewModel : ViewModel() {
    private val apiClient = DBSccAPIClient()
    
    var journeyData by mutableStateOf<JourneyData?>(null)
    var isLoading by mutableStateOf(false)
    
    data class JourneyData(
        val station: String?,
        val nextTrains: List<TrainService>,
        val busRoutes: List<BusVehicle>,
        val facilities: StationData,
        val weather: WeatherData
    )
    
    fun planJourney(destination: String) {
        viewModelScope.launch {
            isLoading = true
            
            try {
                val station = apiClient.getStationInfo()
                val trains = apiClient.searchDepartures(destination, limit = 3)
                val buses = apiClient.getBusLive(limit = 5)
                val weather = apiClient.getWeather()
                
                val journey = JourneyData(
                    station = station.data.station_name,
                    nextTrains = trains.data,
                    busRoutes = buses.data,
                    facilities = station.data,
                    weather = weather.data
                )
                
                journeyData = journey
                isLoading = false
                
            } catch (e: Exception) {
                println("Error: ${e.message}")
                isLoading = false
            }
        }
    }
}

@Composable
fun JourneyAssistantScreen(viewModel: JourneyAssistantViewModel) {
    val journey = viewModel.journeyData
    val isLoading = viewModel.isLoading
    
    if (isLoading) {
        CircularProgressIndicator()
    } else if (journey != null) {
        Column(modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())) {
            
            // Station Info
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(journey.station ?: "Unknown Station", 
                         style = MaterialTheme.typography.headlineMedium)
                    Text("Current: ${journey.weather.temperature.current}°C",
                         style = MaterialTheme.typography.bodySmall)
                }
            }
            
            // Trains
            Text("Next Trains", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            
            LazyColumn {
                items(journey.nextTrains) { train ->
                    TrainCard(train)
                }
            }
            
            // Buses
            Text("Available Buses", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            
            LazyColumn {
                items(journey.busRoutes) { bus ->
                    BusCard(bus)
                }
            }
        }
    }
}
```

---

## Scenario 6: Automated Monitoring System (Python)

**Goal**: Monitor delays and send alerts

```python
import time
import requests
from datetime import datetime

class TrainMonitoringSystem:
    def __init__(self, base_url="http://localhost:5000", alert_threshold_mins=10):
        self.base_url = base_url
        self.alert_threshold = alert_threshold_mins
    
    def check_delays(self):
        """Monitor trains for delays"""
        try:
            resp = requests.get(f"{self.base_url}/api/departures")
            departures = resp.json()['data']
            
            delays = []
            for service in departures:
                # Check if delayed
                if service['estimated_departure'] != 'On time':
                    delay_text = service['delay_reason'] or 'Unknown reason'
                    
                    delays.append({
                        'destination': service['destination'],
                        'operator': service['operator'],
                        'reason': delay_text,
                        'estimated_time': service['estimated_departure'],
                        'timestamp': datetime.now().isoformat()
                    })
            
            if delays:
                self.alert_on_delays(delays)
            
            return delays
        
        except Exception as e:
            print(f"Error checking delays: {e}")
            return []
    
    def alert_on_delays(self, delays):
        """Send alert for delays"""
        for delay in delays:
            print(f"   ALERT: {delay['destination']} train delayed")
            print(f"   Reason: {delay['reason']}")
            print(f"   Operator: {delay['operator']}")
            print(f"   New time: {delay['estimated_time']}")
            
            # Could send email, SMS, webhook, etc. here
    
    def run_monitoring_loop(self, check_interval_secs=30):
        """Continuously monitor for delays"""
        print(f"Starting monitoring (check every {check_interval_secs}s)")
        while True:
            self.check_delays()
            time.sleep(check_interval_secs)

# Usage
if __name__ == "__main__":
    monitor = TrainMonitoringSystem()
    monitor.run_monitoring_loop(check_interval_secs=60)
```

---

## Scenario 7: Weather-Based Journey Advisory (iOS)

**Goal**: Show weather impact on journey

```swift
struct WeatherAdvisoryView: View {
    @State private var weather: WeatherData? = nil
    @State private var advisory = ""
    
    var body: some View {
        VStack {
            if let weather = weather {
                HStack {
                    Text(weather.conditions.description?.capitalized ?? "")
                        .font(.headline)
                    Spacer()
                    Text("\(Int(weather.temperature.current ?? 0))°C")
                        .font(.title2)
                }
                
                // Generate advisory based on weather
                if let advisory = generateAdvisory(weather: weather) {
                    HStack {
                        Image(systemName: getWeatherIcon(weather: weather))
                        Text(advisory)
                            .font(.caption)
                    }
                    .padding()
                    .background(Color(.systemYellow).opacity(0.3))
                    .cornerRadius(8)
                }
            }
        }
        .onAppear {
            Task {
                let response = try await DBSccAPIClient.shared.getWeather()
                weather = response.data
            }
        }
    }
    
    private func generateAdvisory(weather: WeatherData) -> String? {
        guard let windSpeed = weather.wind.speed else { return nil }
        
        if windSpeed > 20 {
            return "⚠️ High winds expected. Trains may experience delays."
        } else if weather.conditions.main?.contains("Rain") == true {
            return "🌧️ Rain expected. Bring an umbrella."
        } else if weather.humidity ?? 0 > 80 {
            return "💧 Very humid. Stay hydrated."
        }
        return nil
    }
    
    private func getWeatherIcon(weather: WeatherData) -> String {
        switch weather.conditions.main?.lowercased() {
        case "clouds": return "cloud.fill"
        case "rain": return "cloud.rain.fill"
        case "clear": return "sun.max.fill"
        case "snow": return "snowflake"
        default: return "cloud.fill"
        }
    }
}
```

---


