# Train App Project – Build Plan

**SCC200 Group 6-5**  
This plan is derived from the **Activity_network.drawio** (project activity diagram). The design PDF (`Design Group 6-5.pdf`) could not be read as text; if you have screenshots or exported text from it (e.g. UI mockups, ERD, user stories), add them to the repo or paste key points so the plan can be refined.

---

## 1. Project Overview

A **train journey application** with:

- **Route finding** (BFS + Dijkstra) for journeys between stations  
- **Database** for stations, routes, schedules, and bookings  
- **GUI** for search, results, and booking  
- **Ticket Suite** (search, select, pay, confirm)  
- **Account Manager** (login, profile, journey history)  
- **Client-side caching** for performance  
- Optional: accessibility, trip advisor, weather/crowd predictions, SMS, journey history  

---

## 2. Architecture (from Activity Network)

Dependency order from the diagram:

| Order | Component           | Description |
|-------|---------------------|-------------|
| 1     | **BFS + Dijkstra**   | Pathfinding: find routes between stations (graph of stations/segments). |
| 2     | **Event Handler**   | User and system events (clicks, search, booking, errors). |
| 3     | **Mapping**         | Map stations/routes to internal data (IDs, names, coordinates if needed). |
| 4     | **DB Implementation** | Schema, migrations, CRUD for stations, routes, trips, bookings, users. |
| 5     | **DB Parser**       | Load/parse DB data (e.g. from files or API) into app structures. |
| 6     | **GUI**             | Screens: home, search, results, booking, account, history. |
| 7     | **Client Caching**  | Cache routes, station list, recent searches to reduce DB/API calls. |
| 8     | **Ticket Suite**    | Search → select journey → seat/ticket type → payment → confirmation. |
| 9     | **Account Manager** | Register, login, profile, (optional) journey history. |

**Additional features** (can be phased later):  
Accessibility, Trip Advisor, Weather Predictive Delays, Crowd Prediction, SMS, Journey History.

**Testing:** Pentesting, stress testing, debugging (as in the diagram).

---

## 3. Suggested Tech Stack

- **Frontend:**  
  - **Option A:** Web – HTML/CSS/JavaScript (or React/Vue) for a browser app.  
  - **Option B:** Desktop – JavaFX or Python (e.g. Tkinter/PyQt) if the brief specifies desktop.
- **Backend (if needed):**  
  - Small project: SQLite + a simple server (e.g. Node/Express or Python/Flask).  
  - Larger: PostgreSQL/MySQL + REST API.
- **Pathfinding:**  
  - Implement BFS and Dijkstra in one module (e.g. `RouteFinder` or `PathfindingService`) using a graph of stations and segments (with times/distances for Dijkstra).
- **Database:**  
  - SQLite for local/dev; same schema can be used for another DB later.

Choose stack to match module requirements (e.g. “must use Java” or “web only”).

---

## 4. Database Design

Typical schema for a train app (align with your PDF ERD if it differs):

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    stations     │     │  route_segments  │     │     routes      │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ id (PK)         │────<│ id (PK)         │     │ id (PK)         │
│ name            │     │ from_station_id │     │ name            │
│ code            │     │ to_station_id   │     │ (optional)      │
│ (lat, lng opt)  │     │ distance_km     │     └────────┬────────┘
└─────────────────┘     │ duration_mins  │              │
        │               └─────────────────┘              │
        │                                                 │
        ▼                                                 ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│      trips      │     │   trip_times    │     │    bookings     │
├─────────────────┤     ├─────────────────┤     ├─────────────────┤
│ id (PK)         │────<│ id (PK)         │     │ id (PK)         │
│ route_id        │     │ trip_id         │     │ user_id         │
│ train_name/no  │     │ station_id      │     │ trip_id (or leg) │
│ (date optional)│     │ arrival_time    │     │ seat_info       │
└─────────────────┘     │ departure_time │     │ status          │
                         └─────────────────┘     │ created_at      │
                                                 └─────────────────┘
┌─────────────────┐
│      users      │
├─────────────────┤
│ id (PK)         │
│ email           │
│ password_hash   │
│ name            │
│ (phone for SMS) │
└─────────────────┘
```

- **stations:** Station names, codes, optional coordinates.  
- **route_segments:** Edges for the graph (from_station_id, to_station_id, duration_mins and/or distance_km for Dijkstra).  
- **routes:** Optional named routes (e.g. “West Coast Main Line”).  
- **trips / trip_times:** Scheduled services and times at each station (for “next trains” and timetable display).  
- **bookings:** Links user + trip (or segment) + seat/class; status (pending/paid/cancelled).  
- **users:** For Account Manager and Ticket Suite.

Implement **DB Implementation** (schema + migrations) and **DB Parser** (read DB into in-memory graph and timetable structures) as separate steps.

---

## 5. UI Screens to Build (match to your PDF mockups)

If your PDF has specific wireframes, replace this list with the exact screens and layout described there.

1. **Home / Search**  
   - Origin and destination (dropdown or autocomplete from stations).  
   - Date and time (or “next available”).  
   - “Search” button.

2. **Search Results**  
   - List of journeys (departure, arrival, duration, changes, price if applicable).  
   - “Select” per journey.

3. **Journey Detail / Booking**  
   - Selected journey summary.  
   - Seat/ticket type (e.g. standard/first, single/return).  
   - Optional seat map.  
   - “Continue to payment” or “Book”.

4. **Login / Register**  
   - For Account Manager and for saving bookings.  
   - Links: “Login”, “Register”, “Forgot password?” (if required).

5. **Account / Profile**  
   - View/edit profile.  
   - (Optional) Journey history list.

6. **Booking Confirmation**  
   - After payment/booking: reference number, journey summary, optional “Add to calendar” or “Send by SMS”.

7. **Optional:**  
   - Map view of route (if Mapping includes geographic display).  
   - Accessibility settings (from Additional Features).  
   - Trip Advisor–style tips (if in scope).

Implement **GUI** and **Event Handler** so these screens call **RouteFinder** (BFS/Dijkstra), **DB Parser**, and **Ticket Suite** without tight coupling.

---

## 6. Implementation Phases

### Phase 1 – Foundation (weeks 1–2)

1. **Repo and stack**  
   - Choose stack (e.g. web vs desktop, language).  
   - Set up project layout (see Section 7).  
   - Add README and this BUILD_PLAN.

2. **DB Implementation**  
   - Create schema (stations, route_segments, routes, trips, trip_times, users, bookings).  
   - Migrations or init scripts.  
   - Seed minimal data (a few stations and segments).

3. **DB Parser**  
   - Load stations and route_segments into an in-memory graph (nodes = stations, edges = segments with cost).  
   - Expose a simple interface: e.g. `getStations()`, `getSegments()` or `getGraph()`.

4. **BFS + Dijkstra**  
   - Implement graph representation (adjacency list or matrix).  
   - BFS: unweighted shortest path (e.g. fewest changes).  
   - Dijkstra: weighted by time or distance.  
   - Single module with clear API: e.g. `findRoutes(originId, destId, options)`.

5. **Mapping**  
   - Map station IDs to names/codes for display.  
   - Map route results to trip times if you have trip_times data.

**Checkpoint:** CLI or minimal UI: “From A to B” → algorithm returns a list of routes; data comes from DB via parser.

---

### Phase 2 – Core app (weeks 3–4)

6. **Event Handler**  
   - Central place for user actions: search, select journey, login, book.  
   - Can be simple callbacks or a small event bus; avoid logic in GUI code.

7. **GUI – main screens**  
   - Home (search form).  
   - Results list.  
   - Journey detail / booking start.  
   - Use the same RouteFinder + DB Parser; GUI only displays and sends events.

8. **Ticket Suite (basic)**  
   - From “Select journey” to “Confirm booking” (no real payment: e.g. “Confirm” creates a booking record).  
   - Store booking in DB and show confirmation screen.

9. **Account Manager (basic)**  
   - Register and login (passwords hashed).  
   - Link bookings to `user_id`.  
   - Optional: profile page.

**Checkpoint:** Full flow: search → see results → select → “book” → login/register → confirmation.

---

### Phase 3 – Polish and extras (weeks 5–6)

10. **Client Caching**  
    - Cache station list, recent search results, and maybe last N routes per user.  
    - Set simple cache expiry (e.g. 5–15 minutes for routes).

11. **Trip times and real data**  
    - If not done earlier: fill `trips` and `trip_times` from real or realistic data.  
    - Show departure/arrival times in results and on confirmation.

12. **Additional features (pick as per PDF/brief)**  
    - Accessibility (e.g. contrast, font size, keyboard nav).  
    - Journey history (list of past bookings).  
    - Optional: Trip Advisor, weather/crowd, SMS (stub or real).

13. **Testing and debugging**  
    - Unit tests for BFS, Dijkstra, DB Parser, and booking logic.  
    - Stress test: many concurrent searches or bookings (if required).  
    - Fix bugs and document known issues.

---

## 7. Suggested Folder Structure

```
SCC200-Group6-5/
├── README.md
├── BUILD_PLAN.md                 # this file
├── Design Group 6-5.pdf          # design doc (reference)
├── Activity_network.drawio        # activity diagram
│
├── docs/                         # optional: extra design notes, ERD exports
│   └── (screenshots or text from PDF)
│
├── backend/                      # if using a server
│   ├── db/
│   │   ├── schema.sql
│   │   ├── migrations/
│   │   └── seed.sql
│   ├── api/                      # REST endpoints
│   └── services/
│       ├── pathfinding.js        # or .py / .java: BFS + Dijkstra
│       ├── db-parser.js
│       └── booking.js
│
├── frontend/                     # if web
│   ├── index.html
│   ├── css/
│   ├── js/
│   │   ├── app.js
│   │   ├── event-handler.js
│   │   ├── api.js                # calls backend or reads local DB
│   │   └── cache.js
│   └── pages/                    # or components
│
├── shared/                       # if logic shared (e.g. pathfinding in both)
│   └── pathfinding.js
│
└── tests/
    ├── pathfinding.test.js
    ├── db-parser.test.js
    └── booking.test.js
```

For a single-codebase (e.g. desktop app), merge `backend` and `frontend` into one app structure (e.g. `src/`, `db/`, `tests/`).

---

## 8. Next Steps

1. **Confirm requirements**  
   - From the PDF or module brief: exact screens, ERD, must-use technologies, and which additional features are mandatory.

2. **Align this plan with the PDF**  
   - If you export the PDF’s ERD or UI as images/text, add them under `docs/` and update:  
     - Section 4 (database) to match the PDF ERD.  
     - Section 5 (UI) to match the PDF screens.

3. **Set up repo and Phase 1**  
   - Create `schema.sql`, seed data, DB Parser, and BFS/Dijkstra module; then run a minimal “from A to B” test.

4. **Implement in order of the activity network**  
   - Keep Event Handler, Mapping, GUI, Ticket Suite, and Account Manager consistent with the dependency order in Section 2.

If you paste or upload the PDF’s key points (or ERD and UI descriptions), the plan can be updated to match them exactly.
