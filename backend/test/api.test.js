const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

const { createAppServer } = require("../main");

const tempStorePath = path.join(__dirname, "tmp-runtime-store.json");

let app;
let server;
let baseUrl;
let authToken;
let userId;
let journeyId;
let ticketId;
let paymentId;
let claimId;
let taxiQuoteId;
let taxiBookingId;
let disruptionReportId;

function buildUrl(endpoint) {
  return `${baseUrl}${endpoint}`;
}

async function request(method, endpoint, payload = null, token = null) {
  const headers = {};
  if (payload !== null) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(buildUrl(endpoint), {
    method,
    headers,
    body: payload !== null ? JSON.stringify(payload) : undefined,
  });

  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = null;
  }

  return { response, data };
}

async function get(endpoint, token = null) {
  return request("GET", endpoint, null, token);
}

async function post(endpoint, payload, token = null) {
  return request("POST", endpoint, payload, token);
}

test.before(async () => {
  if (fs.existsSync(tempStorePath)) fs.unlinkSync(tempStorePath);
  app = createAppServer({ storePath: tempStorePath });
  server = app.server;
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  baseUrl = `http://127.0.0.1:${port}`;
});

test.after(async () => {
  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }
  if (fs.existsSync(tempStorePath)) fs.unlinkSync(tempStorePath);
});

test("health and endpoint discovery", async () => {
  const health = await get("/health");
  assert.equal(health.response.status, 200);
  assert.equal(health.data.status, "healthy");
  assert.equal(typeof health.data.timestamp, "string");

  const endpoints = await get("/api/endpoints");
  assert.equal(endpoints.response.status, 200);
  assert.equal(endpoints.data.status, "success");
  assert.ok(Array.isArray(endpoints.data.endpoints));
  const paths = endpoints.data.endpoints.map((e) => e.path);
  assert.ok(paths.includes("/api/departures"));
  assert.ok(paths.includes("/api/places/search"));
  assert.ok(paths.includes("/api/taxi/quote"));
  assert.ok(paths.includes("/api/journeys"));
});

test("station facilities and weather endpoints", async () => {
  const fac = await get("/api/lancs_fac");
  assert.equal(fac.response.status, 200);
  assert.equal(fac.data.status, "success");
  assert.ok(fac.data.data.station_name);

  const access = await get("/api/lancs_fac/accessibility");
  assert.equal(access.response.status, 200);
  assert.equal(access.data.status, "success");

  const weather = await get("/api/weather");
  assert.equal(weather.response.status, 200);
  assert.equal(weather.data.status, "success");
  assert.ok(weather.data.data.temperature);

  const prediction = await get("/api/predictions/delay?mode=train");
  assert.equal(prediction.response.status, 200);
  assert.equal(prediction.data.status, "success");
  assert.ok(["low", "medium", "high"].includes(prediction.data.data.risk_level));
});

test("departures and buses endpoints", async () => {
  const departures = await get("/api/departures?limit=3");
  assert.equal(departures.response.status, 200);
  assert.equal(departures.data.status, "success");
  assert.equal(departures.data.count, departures.data.data.length);
  assert.ok(departures.data.data.length <= 3);

  const depSearch = await get("/api/departures/search?destination=Glasgow&limit=5");
  assert.equal(depSearch.response.status, 200);
  assert.equal(depSearch.data.status, "success");
  assert.ok(depSearch.data.data.length >= 1);

  const depSearchError = await get("/api/departures/search");
  assert.equal(depSearchError.response.status, 400);

  const busLive = await get("/api/bus/live?line=74&limit=5");
  assert.equal(busLive.response.status, 200);
  assert.equal(busLive.data.status, "success");
  assert.ok(busLive.data.data.every((b) => String(b.line_ref) === "74"));

  const busLocation = await get("/api/bus/live/location?vehicle=BU25_YSD");
  assert.equal(busLocation.response.status, 200);
  assert.equal(busLocation.data.status, "success");
  assert.equal(busLocation.data.data.vehicle_ref, "BU25_YSD");

  const busLocationErr = await get("/api/bus/live/location");
  assert.equal(busLocationErr.response.status, 400);
});

test("delay codes and disruptions endpoints", async () => {
  const delaySearch = await get("/api/delay_codes/search?q=signal");
  assert.equal(delaySearch.response.status, 200);
  assert.equal(delaySearch.data.status, "success");
  assert.ok(Array.isArray(delaySearch.data.data));

  const delayExact = await get("/api/delay_codes?code=IA");
  assert.equal(delayExact.response.status, 200);
  assert.equal(delayExact.data.count, 1);

  const vms = await get("/api/disruptions/vms");
  assert.equal(vms.response.status, 200);
  assert.equal(vms.data.status, "success");
  assert.ok(Array.isArray(vms.data.data));
});

test("places endpoints", async () => {
  const categories = await get("/api/places/categories");
  assert.equal(categories.response.status, 200);
  assert.equal(categories.data.status, "success");
  assert.ok(categories.data.data.length > 0);

  const stats = await get("/api/places/stats");
  assert.equal(stats.response.status, 200);
  assert.equal(stats.data.status, "success");
  assert.ok(stats.data.data.total_places > 0);

  const nearby = await get("/api/places/nearby?radius=50&limit=10");
  assert.equal(nearby.response.status, 200);
  assert.equal(nearby.data.status, "success");
  assert.ok(nearby.data.count <= 10);

  const search = await get("/api/places/search?q=hotel&limit=5");
  assert.equal(search.response.status, 200);
  assert.equal(search.data.status, "success");

  const semantic = await get("/api/places/semantic?q=family day out&threshold=0&limit=5");
  assert.equal(semantic.response.status, 200);
  assert.equal(semantic.data.status, "success");
  assert.equal(semantic.data.mode, "semantic");
});

test("taxi, routes, account, and ticketing flow", async () => {
  const quote = await post("/api/taxi/quote", {
    pickup_latitude: 54.0466,
    pickup_longitude: -2.8007,
    dropoff_latitude: 54.0501,
    dropoff_longitude: -2.7899,
    passengers: 2,
  });
  assert.equal(quote.response.status, 200);
  assert.equal(quote.data.status, "success");
  assert.ok(quote.data.data.length >= 1);
  taxiQuoteId = quote.data.data[0].quote_id;

  const taxiBooking = await post("/api/taxi/bookings", { quote_id: taxiQuoteId });
  assert.equal(taxiBooking.response.status, 200);
  assert.equal(taxiBooking.data.status, "success");
  taxiBookingId = taxiBooking.data.data.taxi_id;

  const taxiBookingGet = await get(`/api/taxi/bookings/${taxiBookingId}`);
  assert.equal(taxiBookingGet.response.status, 200);
  assert.equal(taxiBookingGet.data.data.taxi_id, taxiBookingId);

  const route = await get("/api/routes/find?from=EUS&to=GLC&algorithm=dijkstra&weight=duration");
  assert.equal(route.response.status, 200);
  assert.equal(route.data.status, "success");
  assert.ok(route.data.data.legs.length > 0);

  const register = await post("/api/users/register", {
    name: "Test User",
    email: "test.user@example.com",
    password: "Secret123!",
    accessibility_needs: true,
  });
  assert.equal(register.response.status, 200);
  assert.equal(register.data.status, "success");
  userId = register.data.data.user_id;

  const registerDup = await post("/api/users/register", {
    name: "Test User",
    email: "test.user@example.com",
    password: "Secret123!",
  });
  assert.equal(registerDup.response.status, 409);

  const login = await post("/api/users/login", {
    email: "test.user@example.com",
    password: "Secret123!",
  });
  assert.equal(login.response.status, 200);
  assert.equal(login.data.status, "success");
  authToken = login.data.data.token;
  assert.ok(authToken);

  const journey = await post(
    "/api/journeys",
    {
      origin: "EUS",
      destination: "GLC",
      mode: "train",
    },
    authToken
  );
  assert.equal(journey.response.status, 200);
  assert.equal(journey.data.status, "success");
  journeyId = journey.data.data.journey_id;

  const userJourneys = await get(`/api/users/${userId}/journeys`);
  assert.equal(userJourneys.response.status, 200);
  assert.ok(userJourneys.data.data.some((j) => j.journey_id === journeyId));

  const ticket = await post("/api/tickets", {
    journey_id: journeyId,
    price: 42.5,
    currency: "GBP",
    ticket_type: "return",
  });
  assert.equal(ticket.response.status, 200);
  ticketId = ticket.data.data.ticket_id;

  const payment = await post("/api/payments", {
    ticket_id: ticketId,
    amount: 42.5,
    method: "card",
  });
  assert.equal(payment.response.status, 200);
  paymentId = payment.data.data.payment_id;

  const paymentGet = await get(`/api/payments/${paymentId}`);
  assert.equal(paymentGet.response.status, 200);
  assert.equal(paymentGet.data.data.payment_id, paymentId);

  const claim = await post("/api/delay_claims", {
    ticket_id: ticketId,
    delay_minutes: 35,
  });
  assert.equal(claim.response.status, 200);
  claimId = claim.data.data.claim_id;
  assert.equal(claim.data.data.compensation_amount, 21.25);

  const claimGet = await get(`/api/delay_claims/${claimId}`);
  assert.equal(claimGet.response.status, 200);

  const report = await post("/api/disruption_reports", {
    user_id: userId,
    description: "Signal issue reported at platform 3",
  });
  assert.equal(report.response.status, 200);
  disruptionReportId = report.data.data.report_id;

  const reports = await get("/api/disruption_reports?confirmed=false");
  assert.equal(reports.response.status, 200);
  assert.ok(reports.data.data.some((r) => r.report_id === disruptionReportId));

  const confirm = await post(`/api/disruption_reports/${disruptionReportId}/confirm`, {});
  assert.equal(confirm.response.status, 200);
  assert.equal(confirm.data.data.confirmed, true);
});

test("not found and validation handling", async () => {
  const badRoute = await get("/api/routes/find?from=UNKNOWN&to=GLC");
  assert.equal(badRoute.response.status, 404);

  const missingTaxi = await get("/api/taxi/bookings/9999999");
  assert.equal(missingTaxi.response.status, 404);

  const unknown = await get("/api/not-real");
  assert.equal(unknown.response.status, 404);
});
