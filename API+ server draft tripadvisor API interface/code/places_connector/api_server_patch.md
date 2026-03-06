# Patch for api_server.py

Add these **2 lines** to `api_server.py` after `app = Flask(__name__)` and `CORS(app)`:

```python
# === Lancashire Places Integration ===
from places_connector import register_places_routes
register_places_routes(app)
# =====================================
```

Also add to the `/api/endpoints` route list:

```python
{"method": "GET", "path": "/api/places/nearby",     "description": "Places near a location",     "params": "lat, lon, radius, category, limit, fields"},
{"method": "GET", "path": "/api/places/search",      "description": "Keyword search for places",  "params": "q (required), category, town, limit, fields"},
{"method": "GET", "path": "/api/places/semantic",    "description": "AI semantic place search",   "params": "q (required), category, limit, threshold, fields"},
{"method": "GET", "path": "/api/places/categories",  "description": "List place categories"},
{"method": "GET", "path": "/api/places/stats",       "description": "Places database summary"},
```

### Full context (where to insert):

```python
app = Flask(__name__)
CORS(app)  # Enable CORS for mobile clients

# === Lancashire Places Integration ===          <-- ADD THIS
from places_connector import register_places_routes  # <-- ADD THIS
register_places_routes(app)                          # <-- ADD THIS
# =====================================          <-- ADD THIS

# Setup logging
logging.basicConfig(level=logging.INFO)
```
