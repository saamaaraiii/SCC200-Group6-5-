"use strict";

const fs = require("fs");
const path = require("path");
const http = require("http");
const crypto = require("crypto");
const { URL } = require("url");

const PROJECT_ROOT = path.resolve(__dirname, "..");
const DATA_DIR = path.join(PROJECT_ROOT, "API+SERVER_Draft");
const SCRAPER_DIR = path.join(PROJECT_ROOT, "web scraping tools", "Scraper");
const TRAIN_SEED_PATH = path.join(
  PROJECT_ROOT,
  "TrainApp",
  "TrainApp",
  "Resources",
  "seed.sql"
);
const DEFAULT_STORE_PATH = path.join(__dirname, "data", "runtime_store.json");

const LANCASTER_STATION_COORDS = { lat: 54.0466, lon: -2.8007 };
const API_VERSION = "2.0.0";

class HttpError extends Error {
  constructor(status, message, details = undefined) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function nowIso() {
  return new Date().toISOString();
}

function decodeXml(text) {
  if (typeof text !== "string") return text;
  return text
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function normalizeText(text) {
  return String(text || "")
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenize(text) {
  return normalizeText(text)
    .split(" ")
    .map((t) => t.trim())
    .filter((t) => t.length > 1);
}

function toNumber(value, fieldName) {
  const num = Number(value);
  if (Number.isNaN(num)) {
    throw new HttpError(400, `${fieldName} must be a number`);
  }
  return num;
}

function toInteger(value, fieldName) {
  const num = Number.parseInt(value, 10);
  if (Number.isNaN(num)) {
    throw new HttpError(400, `${fieldName} must be an integer`);
  }
  return num;
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const r = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function randomId(prefix) {
  return `${prefix}_${crypto.randomBytes(6).toString("hex")}`;
}

function readFileText(filePath, fallback = "") {
  try {
    return fs.readFileSync(filePath, "utf8");
  } catch (_) {
    return fallback;
  }
}

function readJsonFile(filePath, fallback = null) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (_) {
    return fallback;
  }
}

function extractTag(block, tag) {
  const regex = new RegExp(
    `<${escapeRegExp(tag)}>([\\s\\S]*?)</${escapeRegExp(tag)}>`,
    "i"
  );
  const match = block.match(regex);
  return match ? decodeXml(String(match[1]).trim()) : null;
}

function extractBlocks(xml, tag) {
  const regex = new RegExp(
    `<${escapeRegExp(tag)}>([\\s\\S]*?)</${escapeRegExp(tag)}>`,
    "g"
  );
  const blocks = [];
  let match = regex.exec(xml);
  while (match) {
    blocks.push(match[1]);
    match = regex.exec(xml);
  }
  return blocks;
}

function parseDepartureBoard(xmlText) {
  const station = extractTag(xmlText, "lt4:locationName");
  const crs = extractTag(xmlText, "lt4:crs");
  const generatedAt = extractTag(xmlText, "lt4:generatedAt");

  const services = extractBlocks(xmlText, "lt8:service").map((serviceXml) => {
    const callingPoints = extractBlocks(serviceXml, "lt8:callingPoint").map(
      (cp) => ({
        location: extractTag(cp, "lt8:locationName"),
        scheduled_time: extractTag(cp, "lt8:st"),
        estimated_time: extractTag(cp, "lt8:et"),
        delay_reason: extractTag(cp, "lt8:delayReason"),
      })
    );

    const originBlock = extractTag(serviceXml, "lt5:origin") || "";
    const destinationBlock = extractTag(serviceXml, "lt5:destination") || "";

    return {
      scheduled_departure: extractTag(serviceXml, "lt4:std"),
      estimated_departure: extractTag(serviceXml, "lt4:etd"),
      platform: extractTag(serviceXml, "lt4:platform"),
      operator: extractTag(serviceXml, "lt4:operator"),
      operator_code: extractTag(serviceXml, "lt4:operatorCode"),
      service_type: extractTag(serviceXml, "lt4:serviceType"),
      service_id: extractTag(serviceXml, "lt4:serviceID"),
      origin: extractTag(originBlock, "lt4:locationName"),
      destination: extractTag(destinationBlock, "lt4:locationName"),
      delay_reason: extractTag(serviceXml, "lt4:delayReason"),
      calling_points: callingPoints,
    };
  });

  return { station, crs, generated_at: generatedAt, services };
}

function parseBusLive(xmlText) {
  const activities = extractBlocks(xmlText, "VehicleActivity");
  return activities.map((activityXml) => {
    const journey = extractTag(activityXml, "MonitoredVehicleJourney") || "";
    const location = extractTag(journey, "VehicleLocation") || "";
    const latRaw = extractTag(location, "Latitude");
    const lonRaw = extractTag(location, "Longitude");
    return {
      line_ref: extractTag(journey, "LineRef"),
      vehicle_ref: extractTag(journey, "VehicleRef"),
      direction: extractTag(journey, "DirectionRef"),
      origin_name: extractTag(journey, "OriginName"),
      destination_name: extractTag(journey, "DestinationName"),
      origin_time: extractTag(journey, "OriginAimedDepartureTime"),
      destination_time: extractTag(journey, "DestinationAimedArrivalTime"),
      published_line_name: extractTag(journey, "PublishedLineName"),
      operator_ref: extractTag(journey, "OperatorRef"),
      bearing: extractTag(journey, "Bearing"),
      recorded_at: extractTag(activityXml, "RecordedAtTime"),
      location:
        latRaw && lonRaw
          ? {
              latitude: Number(latRaw),
              longitude: Number(lonRaw),
            }
          : null,
    };
  });
}

function parseVms(xmlText) {
  const statuses = extractBlocks(xmlText, "vmsControllerStatus");
  return statuses.map((block) => {
    const refBlock = extractTag(block, "vmsControllerReference") || "";
    const extBlock = extractTag(block, "vmsStatusExtensionG") || "";
    const lines = [];
    const lineRegex = /<textLine>([^<]+)<\/textLine>/g;
    let lineMatch = lineRegex.exec(block);
    while (lineMatch) {
      const candidate = decodeXml(lineMatch[1]).trim();
      if (candidate) lines.push(candidate);
      lineMatch = lineRegex.exec(block);
    }
    const uniqueLines = [...new Set(lines)];
    return {
      sign_id: extractTag(refBlock, "idG"),
      working_status: (extractTag(block, "workingStatus") || "").trim(),
      message_set_by: extractTag(block, "messageSetBy"),
      reason: extractTag(block, "reasonForSetting"),
      time_last_set: extractTag(block, "timeLastSet"),
      road_name: extractTag(extBlock, "roadName"),
      location_description: extractTag(extBlock, "locationDescription"),
      latitude: Number(extractTag(extBlock, "latitude")),
      longitude: Number(extractTag(extBlock, "longitude")),
      message_lines: uniqueLines,
    };
  });
}

function parseNaptanStops(xmlText) {
  const stops = [];
  const blocks = extractBlocks(xmlText, "StopPoint");
  for (const block of blocks) {
    const descriptor = extractTag(block, "Descriptor") || "";
    const place = extractTag(block, "Place") || "";
    const location = extractTag(place, "Location") || "";
    const translation = extractTag(location, "Translation") || "";
    const stopClass = extractTag(block, "StopClassification") || "";
    const commonName = extractTag(descriptor, "CommonName");
    const atco = extractTag(block, "AtcoCode");
    if (!atco || !commonName) continue;

    const lat = extractTag(translation, "Latitude");
    const lon = extractTag(translation, "Longitude");

    stops.push({
      atco_code: atco,
      naptan_code: extractTag(block, "NaptanCode"),
      common_name: commonName,
      street: extractTag(descriptor, "Street"),
      indicator: extractTag(descriptor, "Indicator"),
      town: extractTag(place, "Town"),
      latitude: lat ? Number(lat) : null,
      longitude: lon ? Number(lon) : null,
      stop_type: extractTag(stopClass, "StopType"),
    });
  }
  return stops;
}

function parseTrainSeed(seedSql) {
  const stationBlock =
    (seedSql.match(/INSERT OR IGNORE INTO stations[\s\S]*?VALUES([\s\S]*?);/i) ||
      [])[1] || "";
  const segmentBlock =
    (seedSql.match(
      /INSERT OR IGNORE INTO route_segments[\s\S]*?VALUES([\s\S]*?);/i
    ) || [])[1] || "";

  const stations = [];
  const stationRegex =
    /\((\d+),\s*'((?:[^']|''|\\')*)',\s*'((?:[^']|''|\\')*)',\s*([-\d.]+),\s*([-\d.]+)\)/g;
  let stationMatch = stationRegex.exec(stationBlock);
  while (stationMatch) {
    stations.push({
      id: Number(stationMatch[1]),
      name: stationMatch[2].replace(/''/g, "'"),
      code: stationMatch[3].replace(/''/g, "'"),
      latitude: Number(stationMatch[4]),
      longitude: Number(stationMatch[5]),
    });
    stationMatch = stationRegex.exec(stationBlock);
  }

  const segments = [];
  const segmentRegex = /\((\d+),\s*(\d+),\s*([-\d.]+),\s*(\d+)\)/g;
  let segmentMatch = segmentRegex.exec(segmentBlock);
  while (segmentMatch) {
    segments.push({
      from_station_id: Number(segmentMatch[1]),
      to_station_id: Number(segmentMatch[2]),
      distance_km: Number(segmentMatch[3]),
      duration_mins: Number(segmentMatch[4]),
    });
    segmentMatch = segmentRegex.exec(segmentBlock);
  }

  const stationsById = new Map(stations.map((s) => [s.id, s]));
  const stationsByCode = new Map(stations.map((s) => [s.code.toUpperCase(), s]));
  const edgesByPair = new Map(
    segments.map((s) => [`${s.from_station_id}-${s.to_station_id}`, s])
  );

  return { stations, segments, stationsById, stationsByCode, edgesByPair };
}
function loadPlaces(placesDir) {
  const files = fs
    .readdirSync(placesDir)
    .filter((name) => /^lancashire_.*\.json$/i.test(name))
    .map((name) => path.join(placesDir, name));

  const byId = new Map();
  for (const filePath of files) {
    const records = readJsonFile(filePath, []);
    if (!Array.isArray(records)) continue;
    const categoryFromName = path
      .basename(filePath)
      .replace(/^lancashire_/i, "")
      .replace(/_\d{8}_\d{6}\.json$/i, "")
      .replace(/\.json$/i, "")
      .toLowerCase();
    for (const rec of records) {
      const osmId = Number(rec.osm_id);
      if (!Number.isFinite(osmId)) continue;
      const parsed = {
        osm_id: osmId,
        osm_type: rec.osm_type || null,
        category: String(rec.category || categoryFromName || "").toLowerCase(),
        type: rec.type || null,
        name: rec.name || "",
        cuisine: rec.cuisine || "",
        address: rec.address || "",
        postcode: rec.postcode || "",
        town: rec.town || "",
        latitude:
          rec.latitude !== undefined && rec.latitude !== ""
            ? Number(rec.latitude)
            : null,
        longitude:
          rec.longitude !== undefined && rec.longitude !== ""
            ? Number(rec.longitude)
            : null,
        phone: rec.phone || "",
        website: rec.website || "",
        opening_hours: rec.opening_hours || "",
        stars: rec.stars !== undefined && rec.stars !== "" ? Number(rec.stars) : null,
        wheelchair: rec.wheelchair || "",
        scraped_at: rec.scraped_at || null,
      };
      const existing = byId.get(osmId);
      if (!existing) {
        byId.set(osmId, parsed);
      } else {
        const existingTs = Date.parse(existing.scraped_at || "1970-01-01T00:00:00Z");
        const parsedTs = Date.parse(parsed.scraped_at || "1970-01-01T00:00:00Z");
        if (parsedTs >= existingTs) byId.set(osmId, parsed);
      }
    }
  }

  const records = [...byId.values()];
  const byCategory = records.reduce((acc, item) => {
    const key = item.category || "unknown";
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});

  const topTowns = {};
  for (const place of records) {
    if (!place.town) continue;
    const key = place.town;
    topTowns[key] = (topTowns[key] || 0) + 1;
  }

  return {
    records,
    byId,
    byCategory,
    topTowns,
  };
}

function defaultStore() {
  return {
    counters: {
      user_id: 1,
      journey_id: 1,
      leg_id: 1,
      ticket_id: 1,
      payment_id: 1,
      taxi_id: 1,
      claim_id: 1,
      report_id: 1,
      quote_id: 1,
    },
    users: [],
    railcards: [
      { railcard_id: 1, type: "16-25", discount_percent: 34 },
      { railcard_id: 2, type: "26-30", discount_percent: 34 },
      { railcard_id: 3, type: "Senior", discount_percent: 34 },
      { railcard_id: 4, type: "Two Together", discount_percent: 34 },
    ],
    journeys: [],
    journey_legs: [],
    tickets: [],
    payments: [],
    taxi_quotes: [],
    taxi_bookings: [],
    delay_claims: [],
    disruption_reports: [],
  };
}

function ensureStoreShape(store) {
  const fallback = defaultStore();
  for (const [key, value] of Object.entries(fallback)) {
    if (store[key] === undefined) {
      store[key] = value;
    }
  }
  if (typeof store.counters !== "object" || store.counters === null) {
    store.counters = { ...fallback.counters };
  }
  for (const [counter, value] of Object.entries(fallback.counters)) {
    if (!Number.isInteger(store.counters[counter])) {
      store.counters[counter] = value;
    }
  }

  const maxByCounter = {
    user_id: Math.max(0, ...store.users.map((u) => u.user_id || 0)),
    journey_id: Math.max(0, ...store.journeys.map((j) => j.journey_id || 0)),
    leg_id: Math.max(0, ...store.journey_legs.map((l) => l.leg_id || 0)),
    ticket_id: Math.max(0, ...store.tickets.map((t) => t.ticket_id || 0)),
    payment_id: Math.max(0, ...store.payments.map((p) => p.payment_id || 0)),
    taxi_id: Math.max(0, ...store.taxi_bookings.map((t) => t.taxi_id || 0)),
    claim_id: Math.max(0, ...store.delay_claims.map((c) => c.claim_id || 0)),
    report_id: Math.max(0, ...store.disruption_reports.map((r) => r.report_id || 0)),
    quote_id: Math.max(0, ...store.taxi_quotes.map((q) => q.quote_id || 0)),
  };
  for (const [counter, maxValue] of Object.entries(maxByCounter)) {
    if (store.counters[counter] <= maxValue) {
      store.counters[counter] = maxValue + 1;
    }
  }
}

function loadStore(storePath) {
  if (!fs.existsSync(storePath)) {
    const fresh = defaultStore();
    return fresh;
  }
  const parsed = readJsonFile(storePath, defaultStore());
  ensureStoreShape(parsed);
  return parsed;
}

function saveStore(storePath, store) {
  const dir = path.dirname(storePath);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(storePath, JSON.stringify(store, null, 2), "utf8");
}

function nextCounter(store, name) {
  const next = store.counters[name];
  store.counters[name] += 1;
  return next;
}

function hashPassword(password) {
  return crypto.createHash("sha256").update(password).digest("hex");
}

function sanitizeUser(user) {
  const clone = { ...user };
  delete clone.password_hash;
  return clone;
}

function resolveStation(network, value) {
  if (value === undefined || value === null) return null;
  const str = String(value).trim();
  if (!str) return null;
  if (/^\d+$/.test(str)) {
    const byId = network.stationsById.get(Number(str));
    if (byId) return byId;
  }
  const byCode = network.stationsByCode.get(str.toUpperCase());
  if (byCode) return byCode;
  const lowered = str.toLowerCase();
  const exactName = network.stations.find((s) => s.name.toLowerCase() === lowered);
  if (exactName) return exactName;
  return network.stations.find((s) => s.name.toLowerCase().includes(lowered)) || null;
}

function buildAdjacency(segments, weightKey) {
  const adjacency = new Map();
  for (const seg of segments) {
    if (!adjacency.has(seg.from_station_id)) adjacency.set(seg.from_station_id, []);
    adjacency.get(seg.from_station_id).push({
      to: seg.to_station_id,
      weight: seg[weightKey],
    });
  }
  return adjacency;
}

function bfsPath(adjacency, startId, endId) {
  if (startId === endId) return [startId];
  const queue = [[startId]];
  const visited = new Set([startId]);
  while (queue.length > 0) {
    const path = queue.shift();
    const node = path[path.length - 1];
    const next = adjacency.get(node) || [];
    for (const edge of next) {
      if (visited.has(edge.to)) continue;
      const candidate = [...path, edge.to];
      if (edge.to === endId) return candidate;
      visited.add(edge.to);
      queue.push(candidate);
    }
  }
  return null;
}

function dijkstraPath(adjacency, startId, endId) {
  const dist = new Map([[startId, 0]]);
  const prev = new Map();
  const queue = [{ node: startId, weight: 0 }];

  while (queue.length > 0) {
    queue.sort((a, b) => a.weight - b.weight);
    const { node, weight } = queue.shift();
    if (node === endId) break;
    if (weight > (dist.get(node) ?? Infinity)) continue;
    const neighbors = adjacency.get(node) || [];
    for (const edge of neighbors) {
      const alt = weight + edge.weight;
      if (alt < (dist.get(edge.to) ?? Infinity)) {
        dist.set(edge.to, alt);
        prev.set(edge.to, node);
        queue.push({ node: edge.to, weight: alt });
      }
    }
  }

  if (!dist.has(endId)) return null;
  const path = [endId];
  let current = endId;
  while (prev.has(current)) {
    current = prev.get(current);
    path.push(current);
  }
  path.reverse();
  return path;
}

function summarizePath(network, pathIds, algorithm, weight) {
  const legs = [];
  let totalDistance = 0;
  let totalDuration = 0;
  for (let i = 0; i < pathIds.length - 1; i += 1) {
    const fromId = pathIds[i];
    const toId = pathIds[i + 1];
    const seg = network.edgesByPair.get(`${fromId}-${toId}`);
    if (!seg) continue;
    totalDistance += seg.distance_km;
    totalDuration += seg.duration_mins;
    legs.push({
      from_stop: network.stationsById.get(fromId)?.name || `Station ${fromId}`,
      to_stop: network.stationsById.get(toId)?.name || `Station ${toId}`,
      from_stop_id: fromId,
      to_stop_id: toId,
      distance_km: seg.distance_km,
      duration_mins: seg.duration_mins,
    });
  }
  return {
    algorithm,
    weight,
    path: pathIds.map((id) => network.stationsById.get(id)).filter(Boolean),
    legs,
    changes: Math.max(pathIds.length - 2, 0),
    total_distance_km: Number(totalDistance.toFixed(2)),
    total_duration_mins: totalDuration,
  };
}

function inferCategoryHints(query) {
  const q = normalizeText(query);
  const hints = [];
  const mapping = [
    { category: "restaurants", words: ["dinner", "lunch", "eat", "food", "restaurant", "pizza"] },
    { category: "cafes", words: ["coffee", "cafe", "cake", "breakfast"] },
    { category: "pubs", words: ["pub", "ale", "bar", "drinks"] },
    { category: "hotels", words: ["hotel", "stay", "sleep", "accommodation", "overnight"] },
    { category: "attractions", words: ["museum", "park", "attraction", "family", "kids", "activity"] },
  ];
  for (const row of mapping) {
    if (row.words.some((w) => q.includes(w))) {
      hints.push(row.category);
    }
  }
  return hints;
}

function semanticExpandTokens(tokens) {
  const synonymMap = {
    romantic: ["date", "cozy", "dinner", "fine"],
    cheap: ["budget", "affordable", "lowcost", "deal"],
    family: ["kids", "children", "group"],
    outdoors: ["outside", "nature", "park", "walk"],
    lunch: ["sandwich", "brunch", "cafe"],
    pub: ["bar", "ale", "tavern"],
    hotel: ["stay", "sleep", "accommodation", "inn"],
    coffee: ["cafe", "espresso", "cake"],
  };
  const expanded = new Set(tokens);
  for (const token of tokens) {
    if (synonymMap[token]) {
      for (const mapped of synonymMap[token]) expanded.add(mapped);
    }
  }
  return [...expanded];
}

function pickFields(record, fieldsCsv) {
  if (!fieldsCsv) return record;
  const requested = new Set(
    fieldsCsv
      .split(",")
      .map((f) => f.trim())
      .filter(Boolean)
  );
  if (requested.size === 0) return record;
  const filtered = {};
  for (const key of Object.keys(record)) {
    if (requested.has(key)) filtered[key] = record[key];
  }
  return filtered;
}

function parseLimit(value, fallback, min = 1, max = 500) {
  if (value === null || value === undefined || value === "") return fallback;
  const parsed = Number.parseInt(String(value), 10);
  if (Number.isNaN(parsed)) throw new HttpError(400, "limit must be an integer");
  if (parsed < min || parsed > max) {
    throw new HttpError(400, `limit must be between ${min} and ${max}`);
  }
  return parsed;
}
function buildState(options = {}) {
  const dataDir = options.dataDir || DATA_DIR;
  const scraperDir = options.scraperDir || SCRAPER_DIR;
  const trainSeedPath = options.trainSeedPath || TRAIN_SEED_PATH;
  const storePath = options.storePath || DEFAULT_STORE_PATH;

  const datasets = {
    lancs_fac: readJsonFile(path.join(dataDir, "lancs_fac.json"), {}),
    weather: readJsonFile(path.join(dataDir, "weather.json"), {}),
    delay_codes: readJsonFile(path.join(dataDir, "delay-codes.json"), []),
    bus_reference: readJsonFile(path.join(dataDir, "bus.json"), { results: [] }),
    departures_xml: readFileText(path.join(dataDir, "lancs_station_depart"), ""),
    bus_live_xml: readFileText(path.join(dataDir, "busLive.xml"), ""),
    naptan_xml: readFileText(path.join(dataDir, "naptan.xml"), ""),
    vms_xml: readFileText(path.join(dataDir, "vms"), ""),
    corpus: readJsonFile(path.join(dataDir, "CORPUSExtract.json"), { TIPLOCDATA: [] }),
    smart: readJsonFile(path.join(dataDir, "SMARTExtract.json"), { BERTHDATA: [] }),
  };

  const parsed = {
    departures: parseDepartureBoard(datasets.departures_xml),
    bus_live: parseBusLive(datasets.bus_live_xml),
    vms: parseVms(datasets.vms_xml),
    naptan_stops: null,
    places: loadPlaces(scraperDir),
    train_network: parseTrainSeed(readFileText(trainSeedPath, "")),
  };

  const store = loadStore(storePath);
  ensureStoreShape(store);

  const sessions = new Map();
  const taxiProviders = [
    {
      provider_id: "LAN_CABS",
      name: "Lancaster Station Cabs",
      phone: "+44 1524 555111",
      base_fare_gbp: 3.8,
      per_km_gbp: 2.2,
    },
    {
      provider_id: "CITY_TAXI",
      name: "CityLine Taxi",
      phone: "+44 1524 555222",
      base_fare_gbp: 4.2,
      per_km_gbp: 2.05,
    },
    {
      provider_id: "NIGHT_RIDE",
      name: "NightRide Lancaster",
      phone: "+44 1524 555333",
      base_fare_gbp: 4.6,
      per_km_gbp: 2.45,
    },
  ];

  return {
    dataDir,
    scraperDir,
    trainSeedPath,
    storePath,
    datasets,
    parsed,
    store,
    sessions,
    taxiProviders,
  };
}

function getOrParseStops(state) {
  if (!state.parsed.naptan_stops) {
    state.parsed.naptan_stops = parseNaptanStops(state.datasets.naptan_xml);
  }
  return state.parsed.naptan_stops;
}

function getWeatherPayload(state) {
  const weatherRoot = state.datasets.weather?.weather || {};
  const main = weatherRoot.main || {};
  const weather0 = Array.isArray(weatherRoot.weather) ? weatherRoot.weather[0] || {} : {};
  return {
    status: "success",
    data: {
      location: {
        name: weatherRoot.name || null,
        latitude: weatherRoot.coord?.lat ?? null,
        longitude: weatherRoot.coord?.lon ?? null,
      },
      conditions: {
        main: weather0.main ?? null,
        description: weather0.description ?? null,
        icon: weather0.icon ?? null,
      },
      temperature: {
        current: main.temp ?? null,
        feels_like: main.feels_like ?? null,
        min: main.temp_min ?? null,
        max: main.temp_max ?? null,
      },
      wind: {
        speed: weatherRoot.wind?.speed ?? null,
        direction: weatherRoot.wind?.deg ?? null,
        gust: weatherRoot.wind?.gust ?? null,
      },
      humidity: main.humidity ?? null,
      visibility: weatherRoot.visibility ?? null,
      clouds: weatherRoot.clouds?.all ?? null,
      timestamp: weatherRoot.dt ?? null,
    },
  };
}

function computeDelayRisk(state, mode) {
  const services = state.parsed.departures.services || [];
  const delayed = services.filter((s) => {
    const etd = normalizeText(s.estimated_departure || "");
    return etd && etd !== "on time";
  }).length;
  const delayedRatio = services.length > 0 ? delayed / services.length : 0;
  const weatherData = getWeatherPayload(state).data;
  const wind = Number(weatherData.wind.speed || 0);
  const weatherMain = normalizeText(weatherData.conditions.main || "");
  let weatherRisk = 0;
  if (wind >= 15) weatherRisk += 0.3;
  if (wind >= 25) weatherRisk += 0.3;
  if (weatherMain.includes("rain") || weatherMain.includes("snow")) weatherRisk += 0.2;
  if (weatherMain.includes("storm")) weatherRisk += 0.4;
  weatherRisk = Math.min(weatherRisk, 1);

  let score = 0;
  if (mode === "train") score = delayedRatio * 0.75 + weatherRisk * 0.25;
  else if (mode === "bus") score = delayedRatio * 0.35 + weatherRisk * 0.65;
  else score = delayedRatio * 0.25 + weatherRisk * 0.75;

  const rounded = Math.max(0, Math.min(1, Number(score.toFixed(3))));
  const level = rounded < 0.25 ? "low" : rounded < 0.55 ? "medium" : "high";
  return {
    mode,
    risk_score: rounded,
    risk_level: level,
    factors: {
      delayed_services_ratio: Number(delayedRatio.toFixed(3)),
      weather_risk: Number(weatherRisk.toFixed(3)),
    },
  };
}

function authUserFromRequest(req, state) {
  const auth = req.headers.authorization || "";
  if (!auth.startsWith("Bearer ")) return null;
  const token = auth.slice("Bearer ".length).trim();
  const session = state.sessions.get(token);
  if (!session) return null;
  if (Date.now() > session.expires_at) {
    state.sessions.delete(token);
    return null;
  }
  return state.store.users.find((u) => u.user_id === session.user_id) || null;
}

function createRouter(state) {
  const routes = [];

  function compilePath(pattern) {
    const segments = pattern.split("/").filter((s) => s.length > 0);
    const keys = [];
    const regexSource = segments
      .map((segment) => {
        if (segment.startsWith(":")) {
          keys.push(segment.slice(1));
          return "([^/]+)";
        }
        return escapeRegExp(segment);
      })
      .join("/");
    return {
      regex: new RegExp(`^/${regexSource}$`),
      keys,
    };
  }

  function route(method, pattern, description, params, handler) {
    const compiled = compilePath(pattern);
    routes.push({
      method: method.toUpperCase(),
      pattern,
      description,
      params,
      compiled,
      handler,
    });
  }

  async function parseBody(req) {
    if (req.method === "GET" || req.method === "HEAD") return {};
    const chunks = [];
    return new Promise((resolve, reject) => {
      let bytes = 0;
      req.on("data", (chunk) => {
        bytes += chunk.length;
        if (bytes > 2_000_000) {
          reject(new HttpError(413, "Request body too large"));
          return;
        }
        chunks.push(chunk);
      });
      req.on("end", () => {
        if (chunks.length === 0) {
          resolve({});
          return;
        }
        const raw = Buffer.concat(chunks).toString("utf8");
        try {
          resolve(JSON.parse(raw));
        } catch (_) {
          reject(new HttpError(400, "Invalid JSON body"));
        }
      });
      req.on("error", () => reject(new HttpError(400, "Malformed request body")));
    });
  }

  function setCommonHeaders(res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
    res.setHeader("Content-Type", "application/json; charset=utf-8");
  }

  function sendJson(res, statusCode, payload) {
    setCommonHeaders(res);
    res.statusCode = statusCode;
    res.end(JSON.stringify(payload));
  }

  async function handle(req, res) {
    const url = new URL(req.url, "http://localhost");
    const pathname = url.pathname;

    if (req.method === "OPTIONS") {
      setCommonHeaders(res);
      res.statusCode = 204;
      res.end();
      return;
    }

    const routeMatch = routes.find((r) => {
      if (r.method !== req.method.toUpperCase()) return false;
      return r.compiled.regex.test(pathname);
    });

    if (!routeMatch) {
      sendJson(res, 404, { status: "error", message: "Endpoint not found" });
      return;
    }

    const matched = pathname.match(routeMatch.compiled.regex);
    const pathParams = {};
    routeMatch.compiled.keys.forEach((key, idx) => {
      pathParams[key] = decodeURIComponent(matched[idx + 1]);
    });

    try {
      const body = await parseBody(req);
      const context = {
        req,
        body,
        params: pathParams,
        query: url.searchParams,
        state,
        authUser: authUserFromRequest(req, state),
      };
      const result = await routeMatch.handler(context);
      if (!result) {
        sendJson(res, 204, {});
        return;
      }
      if (result.statusCode !== undefined && result.body !== undefined) {
        sendJson(res, result.statusCode, result.body);
      } else {
        sendJson(res, 200, result);
      }
    } catch (error) {
      if (error instanceof HttpError) {
        sendJson(res, error.status, {
          status: "error",
          message: error.message,
          details: error.details,
        });
        return;
      }
      sendJson(res, 500, {
        status: "error",
        message: "Internal server error",
        details: String(error?.message || error),
      });
    }
  }

  return { route, handle, routes };
}
function createAppServer(options = {}) {
  const state = buildState(options);
  const router = createRouter(state);

  router.route("GET", "/health", "Health check", null, async () => ({
    status: "healthy",
    timestamp: nowIso(),
    version: API_VERSION,
  }));

  router.route("GET", "/api/endpoints", "List all API endpoints", null, async () => ({
    status: "success",
    endpoints: router.routes.map((r) => ({
      method: r.method,
      path: r.pattern,
      description: r.description,
      params: r.params || undefined,
    })),
  }));

  router.route("GET", "/api/lancs_fac", "Lancaster station facility summary", null, async ({ state }) => {
    const data = state.datasets.lancs_fac || {};
    return {
      status: "success",
      data: {
        station_name: data.name || null,
        crs_code: data.crsCode || null,
        nrcc_code: data.nationalLocationCode || null,
        location: data.location || null,
        address: data.address || null,
        operator: data.stationOperator?.name || null,
        staffing_level: data.staffingLevel || null,
        alerts: data.stationAlerts || [],
      },
    };
  });

  router.route(
    "GET",
    "/api/lancs_fac/accessibility",
    "Station accessibility details",
    null,
    async ({ state }) => {
      const a = state.datasets.lancs_fac?.stationAccessibility || {};
      return {
        status: "success",
        data: {
          step_free_category: a.stepFreeCategory?.category || null,
          tactile_paving: a.tactilePaving || null,
          induction_loops: a.inductionLoop?.provision || null,
          wheelchairs_available: a.wheelchairsAvailable ?? null,
          passenger_assistance: a.passengerAssistance || [],
          train_ramp: a.trainRamp?.available ?? null,
          ticket_barriers: a.ticketBarriers?.available ?? null,
        },
      };
    }
  );

  router.route("GET", "/api/lancs_fac/lifts", "Station lifts information", null, async ({ state }) => {
    const lifts = state.datasets.lancs_fac?.lifts || {};
    return {
      status: "success",
      data: {
        available: lifts.available ?? null,
        statement: lifts.statement || null,
        lifts_info: lifts.liftsInfo || [],
      },
    };
  });

  router.route(
    "GET",
    "/api/lancs_fac/ticket_buying",
    "Ticket office and machines information",
    null,
    async ({ state }) => {
      const t = state.datasets.lancs_fac?.ticketBuying || {};
      return {
        status: "success",
        data: {
          ticket_office: t.ticketOffice || {},
          machines_available: t.ticketMachinesAvailable ?? null,
          collect_online: t.collectOnlineBookedTickets || {},
          pay_as_you_go: t.payAsYouGo || {},
        },
      };
    }
  );

  router.route(
    "GET",
    "/api/lancs_fac/transport_links",
    "Transport links from station",
    null,
    async ({ state }) => {
      const t = state.datasets.lancs_fac?.transportLinks || {};
      return {
        status: "success",
        data: {
          bus: t.bus?.available ?? null,
          replacement_bus: t.replacementBus?.available ?? null,
          taxi: t.taxi?.available ?? null,
          taxi_ranks: t.taxi?.taxiRanks || [],
          airport: t.airport?.available ?? null,
          underground: t.underground?.available ?? null,
          car_hire: t.carHire?.available ?? null,
          port: t.port?.available ?? null,
        },
      };
    }
  );

  router.route("GET", "/api/lancs_fac/cycling", "Station cycling facilities", null, async ({ state }) => {
    const c = state.datasets.lancs_fac?.cycling || {};
    return {
      status: "success",
      data: {
        storage_available: c.cycleStorageAvailable ?? null,
        spaces: c.spaces?.numberOfSpaces ?? null,
        storage_types: c.typesOfStorage || [],
        sheltered: c.sheltered ?? null,
        cctv: c.cctv ?? null,
        location: c.location ?? null,
      },
    };
  });

  router.route("GET", "/api/lancs_fac/parking", "Station parking details", null, async ({ state }) => ({
    status: "success",
    data: {
      car_parks: state.datasets.lancs_fac?.carParks || {},
    },
  }));

  router.route(
    "GET",
    "/api/departures",
    "Train departures from Lancaster station",
    "limit (optional)",
    async ({ state, query }) => {
      const limit = parseLimit(query.get("limit"), state.parsed.departures.services.length, 1, 2000);
      const data = state.parsed.departures.services.slice(0, limit);
      return {
        status: "success",
        station: state.parsed.departures.station,
        crs: state.parsed.departures.crs,
        timestamp: nowIso(),
        source_generated_at: state.parsed.departures.generated_at,
        count: data.length,
        data,
      };
    }
  );

  router.route(
    "GET",
    "/api/departures/search",
    "Search train departures by destination",
    "destination (required), limit (optional)",
    async ({ state, query }) => {
      const destination = normalizeText(query.get("destination") || "");
      if (!destination) throw new HttpError(400, "destination parameter required");
      const limit = parseLimit(query.get("limit"), 50, 1, 1000);
      const data = state.parsed.departures.services
        .filter((service) => normalizeText(service.destination).includes(destination))
        .slice(0, limit);
      return {
        status: "success",
        search_query: destination,
        count: data.length,
        data,
      };
    }
  );

  router.route(
    "GET",
    "/api/bus/live",
    "Live bus positions",
    "line (optional), limit (optional)",
    async ({ state, query }) => {
      const lineFilter = normalizeText(query.get("line") || "");
      const limit = parseLimit(query.get("limit"), 200, 1, 5000);
      let data = state.parsed.bus_live;
      if (lineFilter) {
        data = data.filter((bus) => normalizeText(bus.line_ref) === lineFilter);
      }
      data = data.slice(0, limit);
      return {
        status: "success",
        timestamp: nowIso(),
        count: data.length,
        data,
      };
    }
  );

  router.route(
    "GET",
    "/api/bus/live/location",
    "Specific bus current location",
    "vehicle (required)",
    async ({ state, query }) => {
      const vehicle = normalizeText(query.get("vehicle") || "");
      if (!vehicle) throw new HttpError(400, "vehicle parameter required");
      const found = state.parsed.bus_live.find(
        (bus) => normalizeText(bus.vehicle_ref) === vehicle
      );
      if (!found) throw new HttpError(404, "Vehicle not found");
      return { status: "success", data: found };
    }
  );

  router.route(
    "GET",
    "/api/bus/routes",
    "Static bus route/operator metadata",
    "line (optional), operator (optional), limit (optional)",
    async ({ state, query }) => {
      const line = normalizeText(query.get("line") || "");
      const operator = normalizeText(query.get("operator") || "");
      const limit = parseLimit(query.get("limit"), 100, 1, 5000);
      const all = Array.isArray(state.datasets.bus_reference?.results)
        ? state.datasets.bus_reference.results
        : [];
      let records = all.map((row) => ({
        id: row.id,
        name: row.name,
        noc: row.noc,
        operator_name: row.operatorName,
        status: row.status,
        description: row.description,
        lines: row.lines || [],
        localities: row.localities || [],
        created: row.created,
        modified: row.modified,
      }));
      if (operator) {
        records = records.filter((r) => normalizeText(r.operator_name).includes(operator));
      }
      if (line) {
        records = records.filter((r) =>
          (r.lines || []).some((item) => normalizeText(item).includes(line))
        );
      }
      records = records.slice(0, limit);
      return {
        status: "success",
        count: records.length,
        data: records,
      };
    }
  );

  router.route(
    "GET",
    "/api/bus/stops",
    "Bus stops from NaPTAN",
    "q,town,lat,lon,radius,limit (all optional)",
    async ({ state, query }) => {
      const stops = getOrParseStops(state);
      const q = normalizeText(query.get("q") || "");
      const town = normalizeText(query.get("town") || "");
      const latRaw = query.get("lat");
      const lonRaw = query.get("lon");
      const radius = query.get("radius") ? toNumber(query.get("radius"), "radius") : 2;
      const limit = parseLimit(query.get("limit"), 100, 1, 2000);

      let records = stops;
      if (q) {
        records = records.filter((stop) => {
          const hay = normalizeText(
            `${stop.common_name} ${stop.street || ""} ${stop.indicator || ""}`
          );
          return hay.includes(q);
        });
      }
      if (town) {
        records = records.filter((stop) => normalizeText(stop.town).includes(town));
      }

      if (latRaw !== null && lonRaw !== null) {
        const lat = toNumber(latRaw, "lat");
        const lon = toNumber(lonRaw, "lon");
        records = records
          .filter((stop) => Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude))
          .map((stop) => ({
            ...stop,
            distance_km: Number(
              haversineKm(lat, lon, stop.latitude, stop.longitude).toFixed(3)
            ),
          }))
          .filter((stop) => stop.distance_km <= radius)
          .sort((a, b) => a.distance_km - b.distance_km);
      }

      records = records.slice(0, limit);
      return {
        status: "success",
        count: records.length,
        data: records,
      };
    }
  );
  router.route(
    "GET",
    "/api/delay_codes",
    "Rail delay reason codes",
    "code (optional)",
    async ({ state, query }) => {
      const code = normalizeText(query.get("code") || "");
      let records = state.datasets.delay_codes || [];
      if (code) {
        records = records.filter((item) => normalizeText(item.Code) === code);
        if (records.length === 0) throw new HttpError(404, `Code ${code.toUpperCase()} not found`);
      }
      return {
        status: "success",
        count: records.length,
        data: records,
      };
    }
  );

  router.route(
    "GET",
    "/api/delay_codes/search",
    "Search delay codes by text",
    "q (required)",
    async ({ state, query }) => {
      const q = normalizeText(query.get("q") || "");
      if (!q) throw new HttpError(400, "q parameter required");
      const results = (state.datasets.delay_codes || []).filter((row) => {
        const hay = normalizeText(`${row.Code} ${row.Cause} ${row.Abbreviation}`);
        return hay.includes(q);
      });
      return {
        status: "success",
        query: q,
        count: results.length,
        data: results,
      };
    }
  );

  router.route("GET", "/api/weather", "Current weather snapshot", null, async ({ state }) =>
    getWeatherPayload(state)
  );

  router.route(
    "GET",
    "/api/predictions/delay",
    "Predict delay risk by mode",
    "mode (optional: train|bus|taxi)",
    async ({ state, query }) => {
      const modeRaw = normalizeText(query.get("mode") || "train");
      const mode = ["train", "bus", "taxi"].includes(modeRaw) ? modeRaw : "train";
      return {
        status: "success",
        timestamp: nowIso(),
        data: computeDelayRisk(state, mode),
      };
    }
  );

  router.route("GET", "/api/disruptions/vms", "Road disruption messages from VMS feed", null, async ({ state }) => ({
    status: "success",
    timestamp: nowIso(),
    count: state.parsed.vms.length,
    data: state.parsed.vms,
  }));
  router.route(
    "GET",
    "/api/places/nearby",
    "Places near a location",
    "lat,lon,radius,category,limit,fields (all optional)",
    async ({ state, query }) => {
      const lat =
        query.get("lat") !== null
          ? toNumber(query.get("lat"), "lat")
          : LANCASTER_STATION_COORDS.lat;
      const lon =
        query.get("lon") !== null
          ? toNumber(query.get("lon"), "lon")
          : LANCASTER_STATION_COORDS.lon;
      const radius =
        query.get("radius") !== null ? toNumber(query.get("radius"), "radius") : 1.0;
      const category = normalizeText(query.get("category") || "");
      const limit = parseLimit(query.get("limit"), 20, 1, 500);
      const fields = query.get("fields");

      let records = state.parsed.places.records.filter(
        (p) => Number.isFinite(p.latitude) && Number.isFinite(p.longitude)
      );
      if (category) {
        records = records.filter((p) => normalizeText(p.category) === category);
      }
      records = records
        .map((p) => ({
          ...p,
          distance_km: Number(haversineKm(lat, lon, p.latitude, p.longitude).toFixed(3)),
        }))
        .filter((p) => p.distance_km <= radius)
        .sort((a, b) => a.distance_km - b.distance_km)
        .slice(0, limit)
        .map((p) => pickFields(p, fields));

      return {
        status: "success",
        lat,
        lon,
        radius_km: radius,
        count: records.length,
        data: records,
      };
    }
  );

  router.route(
    "GET",
    "/api/places/search",
    "Keyword place search",
    "q (required), category,town,limit,fields",
    async ({ state, query }) => {
      const q = normalizeText(query.get("q") || "");
      if (!q) throw new HttpError(400, "q parameter required");
      const category = normalizeText(query.get("category") || "");
      const town = normalizeText(query.get("town") || "");
      const limit = parseLimit(query.get("limit"), 20, 1, 500);
      const fields = query.get("fields");
      let records = state.parsed.places.records;
      if (category) {
        records = records.filter((p) => normalizeText(p.category) === category);
      }
      if (town) {
        records = records.filter((p) => normalizeText(p.town).includes(town));
      }
      records = records
        .map((p) => {
          const hay = normalizeText(
            `${p.name} ${p.cuisine} ${p.address} ${p.town} ${p.category} ${p.type}`
          );
          const score = hay.includes(q) ? q.length : 0;
          return { ...p, _score: score };
        })
        .filter((p) => p._score > 0)
        .sort((a, b) => b._score - a._score || (b.stars || 0) - (a.stars || 0))
        .slice(0, limit)
        .map((p) => {
          const clone = { ...p };
          delete clone._score;
          return pickFields(clone, fields);
        });

      return {
        status: "success",
        query: q,
        count: records.length,
        data: records,
      };
    }
  );

  router.route(
    "GET",
    "/api/places/semantic",
    "Semantic place search",
    "q (required), category,limit,threshold,fields",
    async ({ state, query }) => {
      const qRaw = query.get("q") || "";
      const q = normalizeText(qRaw);
      if (!q) throw new HttpError(400, "q parameter required");
      const category = normalizeText(query.get("category") || "");
      const limit = parseLimit(query.get("limit"), 20, 1, 200);
      const threshold =
        query.get("threshold") !== null
          ? toNumber(query.get("threshold"), "threshold")
          : 0.2;
      const fields = query.get("fields");

      const queryTokens = tokenize(qRaw);
      const expanded = semanticExpandTokens(queryTokens);
      const categoryHints = inferCategoryHints(qRaw);
      let records = state.parsed.places.records;
      if (category) {
        records = records.filter((p) => normalizeText(p.category) === category);
      }

      records = records
        .map((place) => {
          const hay = normalizeText(
            `${place.name} ${place.cuisine} ${place.address} ${place.town} ${place.category} ${place.type}`
          );
          let matchCount = 0;
          for (const token of expanded) {
            if (hay.includes(token)) matchCount += 1;
          }
          let score = expanded.length > 0 ? matchCount / expanded.length : 0;
          if (categoryHints.includes(place.category)) score += 0.2;
          score = Number(Math.min(score, 1).toFixed(3));
          return { ...place, relevance_score: score };
        })
        .filter((p) => p.relevance_score >= threshold)
        .sort(
          (a, b) =>
            b.relevance_score - a.relevance_score || (b.stars || 0) - (a.stars || 0)
        )
        .slice(0, limit)
        .map((p) => pickFields(p, fields));

      return {
        status: "success",
        query: qRaw,
        mode: "semantic",
        count: records.length,
        data: records,
      };
    }
  );

  router.route("GET", "/api/places/categories", "Place category counts", null, async ({ state }) => {
    const data = Object.entries(state.parsed.places.byCategory)
      .map(([category, count]) => ({ category, count }))
      .sort((a, b) => b.count - a.count);
    return { status: "success", data };
  });

  router.route("GET", "/api/places/stats", "Places database stats", null, async ({ state }) => {
    const byCategory = Object.entries(state.parsed.places.byCategory)
      .map(([category, count]) => ({ category, count }))
      .sort((a, b) => b.count - a.count);
    const topTowns = Object.entries(state.parsed.places.topTowns)
      .map(([town, count]) => ({ town, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 15);
    return {
      status: "success",
      data: {
        total_places: state.parsed.places.records.length,
        by_category: byCategory,
        top_towns: topTowns,
      },
    };
  });

  router.route("GET", "/api/places/:osm_id", "Get place by OSM id", "osm_id path param", async ({ state, params }) => {
    const osmId = toInteger(params.osm_id, "osm_id");
    const found = state.parsed.places.byId.get(osmId);
    if (!found) throw new HttpError(404, "Place not found");
    return { status: "success", data: found };
  });
  router.route("GET", "/api/taxi/ranks", "Taxi ranks near station", null, async ({ state }) => ({
    status: "success",
    data: state.datasets.lancs_fac?.transportLinks?.taxi?.taxiRanks || [],
  }));

  router.route("GET", "/api/taxi/providers", "Configured taxi providers", null, async ({ state }) => ({
    status: "success",
    count: state.taxiProviders.length,
    data: state.taxiProviders,
  }));

  router.route(
    "POST",
    "/api/taxi/quote",
    "Create taxi quote",
    "pickup_latitude,pickup_longitude,dropoff_latitude,dropoff_longitude required",
    async ({ state, body }) => {
      const pickupLat = toNumber(body.pickup_latitude, "pickup_latitude");
      const pickupLon = toNumber(body.pickup_longitude, "pickup_longitude");
      const dropoffLat = toNumber(body.dropoff_latitude, "dropoff_latitude");
      const dropoffLon = toNumber(body.dropoff_longitude, "dropoff_longitude");
      const passengers = body.passengers ? toInteger(body.passengers, "passengers") : 1;
      const priority = Boolean(body.priority_pickup);

      const distance = haversineKm(pickupLat, pickupLon, dropoffLat, dropoffLon);
      const etaMinutes = Math.max(3, Math.round((distance / 28) * 60 + (priority ? -2 : 2)));

      let providers = state.taxiProviders;
      if (body.provider_id) {
        providers = providers.filter((p) => p.provider_id === String(body.provider_id));
        if (providers.length === 0) throw new HttpError(404, "provider_id not found");
      }

      const quotes = providers.map((provider) => {
        let fare =
          provider.base_fare_gbp + provider.per_km_gbp * distance + Math.max(passengers - 1, 0) * 0.8;
        if (priority) fare += 2.5;
        fare = Number(fare.toFixed(2));
        return {
          quote_id: nextCounter(state.store, "quote_id"),
          provider_id: provider.provider_id,
          provider_name: provider.name,
          pickup_location: {
            latitude: pickupLat,
            longitude: pickupLon,
          },
          dropoff_location: {
            latitude: dropoffLat,
            longitude: dropoffLon,
          },
          passengers,
          priority_pickup: priority,
          distance_km: Number(distance.toFixed(3)),
          estimated_duration_mins: etaMinutes,
          estimated_fare_gbp: fare,
          created_at: nowIso(),
          expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
        };
      });

      state.store.taxi_quotes.push(...quotes);
      saveStore(state.storePath, state.store);

      return {
        status: "success",
        count: quotes.length,
        data: quotes,
      };
    }
  );

  router.route(
    "POST",
    "/api/taxi/bookings",
    "Book taxi from quote",
    "quote_id (required), journey_id,pickup_location,dropoff_location optional",
    async ({ state, body }) => {
      const quoteId = body.quote_id ? toInteger(body.quote_id, "quote_id") : null;
      let quote = null;
      if (quoteId !== null) {
        quote = state.store.taxi_quotes.find((q) => q.quote_id === quoteId) || null;
        if (!quote) throw new HttpError(404, "quote_id not found");
      }

      const journeyId = body.journey_id ? toInteger(body.journey_id, "journey_id") : null;
      if (journeyId !== null) {
        const journeyExists = state.store.journeys.some((j) => j.journey_id === journeyId);
        if (!journeyExists) throw new HttpError(404, "journey_id not found");
      }

      const now = nowIso();
      const booking = {
        taxi_id: nextCounter(state.store, "taxi_id"),
        journey_id: journeyId,
        quote_id: quote?.quote_id || null,
        provider_id: quote?.provider_id || body.provider_id || null,
        pickup_location: body.pickup_location || quote?.pickup_location || null,
        dropoff_location: body.dropoff_location || quote?.dropoff_location || null,
        booking_time: now,
        status: "confirmed",
        estimated_fare_gbp: quote?.estimated_fare_gbp ?? null,
        estimated_duration_mins: quote?.estimated_duration_mins ?? null,
      };

      state.store.taxi_bookings.push(booking);
      saveStore(state.storePath, state.store);

      return {
        status: "success",
        data: booking,
      };
    }
  );

  router.route(
    "GET",
    "/api/taxi/bookings/:taxi_id",
    "Get taxi booking by id",
    "taxi_id path param",
    async ({ state, params }) => {
      const taxiId = toInteger(params.taxi_id, "taxi_id");
      const booking = state.store.taxi_bookings.find((b) => b.taxi_id === taxiId);
      if (!booking) throw new HttpError(404, "Taxi booking not found");
      return { status: "success", data: booking };
    }
  );

  router.route(
    "GET",
    "/api/stations",
    "Stations available for route planning",
    "q (optional), limit (optional)",
    async ({ state, query }) => {
      const q = normalizeText(query.get("q") || "");
      const limit = parseLimit(query.get("limit"), 100, 1, 1000);
      let stations = state.parsed.train_network.stations;
      if (q) {
        stations = stations.filter((s) =>
          normalizeText(`${s.name} ${s.code}`).includes(q)
        );
      }
      stations = stations.slice(0, limit);
      return {
        status: "success",
        count: stations.length,
        data: stations,
      };
    }
  );

  router.route(
    "GET",
    "/api/routes/find",
    "Find route via BFS or Dijkstra",
    "from,to required; algorithm=bfs|dijkstra; weight=duration|distance",
    async ({ state, query }) => {
      const from = query.get("from");
      const to = query.get("to");
      if (!from || !to) throw new HttpError(400, "from and to parameters are required");

      const algorithmRaw = normalizeText(query.get("algorithm") || "dijkstra");
      const weightRaw = normalizeText(query.get("weight") || "duration");
      const algorithm = algorithmRaw === "bfs" ? "bfs" : "dijkstra";
      const weight = weightRaw === "distance" ? "distance" : "duration";

      const network = state.parsed.train_network;
      const fromStation = resolveStation(network, from);
      const toStation = resolveStation(network, to);
      if (!fromStation) throw new HttpError(404, "Origin station not found");
      if (!toStation) throw new HttpError(404, "Destination station not found");

      let pathIds = null;
      if (algorithm === "bfs") {
        pathIds = bfsPath(
          buildAdjacency(network.segments, "duration_mins"),
          fromStation.id,
          toStation.id
        );
      } else {
        pathIds = dijkstraPath(
          buildAdjacency(
            network.segments,
            weight === "distance" ? "distance_km" : "duration_mins"
          ),
          fromStation.id,
          toStation.id
        );
      }
      if (!pathIds || pathIds.length === 0) throw new HttpError(404, "No route found");

      return {
        status: "success",
        data: summarizePath(network, pathIds, algorithm, weight),
      };
    }
  );

  router.route(
    "POST",
    "/api/users/register",
    "Register user account",
    "name,email,password required",
    async ({ state, body }) => {
      const name = String(body.name || "").trim();
      const email = String(body.email || "").trim().toLowerCase();
      const password = String(body.password || "");
      const accessibilityNeeds = Boolean(body.accessibility_needs);
      if (!name || !email || !password) {
        throw new HttpError(400, "name, email, and password are required");
      }
      const existing = state.store.users.find((u) => u.email.toLowerCase() === email);
      if (existing) throw new HttpError(409, "email already registered");

      const user = {
        user_id: nextCounter(state.store, "user_id"),
        name,
        email,
        password_hash: hashPassword(password),
        accessibility_needs: accessibilityNeeds,
        created_at: nowIso(),
      };
      state.store.users.push(user);
      saveStore(state.storePath, state.store);
      return {
        status: "success",
        data: sanitizeUser(user),
      };
    }
  );

  router.route("POST", "/api/users/login", "Login and get auth token", "email,password required", async ({ state, body }) => {
    const email = String(body.email || "").trim().toLowerCase();
    const password = String(body.password || "");
    if (!email || !password) throw new HttpError(400, "email and password are required");
    const user = state.store.users.find((u) => u.email.toLowerCase() === email);
    if (!user) throw new HttpError(401, "invalid credentials");
    if (user.password_hash !== hashPassword(password)) {
      throw new HttpError(401, "invalid credentials");
    }
    const token = randomId("tok");
    state.sessions.set(token, {
      user_id: user.user_id,
      expires_at: Date.now() + 24 * 60 * 60 * 1000,
    });
    return {
      status: "success",
      data: {
        token,
        expires_in_seconds: 24 * 60 * 60,
        user: sanitizeUser(user),
      },
    };
  });
  router.route("GET", "/api/users/:user_id", "Get user profile", "user_id path param", async ({ state, params }) => {
    const userId = toInteger(params.user_id, "user_id");
    const user = state.store.users.find((u) => u.user_id === userId);
    if (!user) throw new HttpError(404, "User not found");
    return { status: "success", data: sanitizeUser(user) };
  });

  router.route(
    "GET",
    "/api/users/:user_id/journeys",
    "List journeys for a user",
    "user_id path param, limit optional",
    async ({ state, params, query }) => {
      const userId = toInteger(params.user_id, "user_id");
      const limit = parseLimit(query.get("limit"), 100, 1, 1000);
      const journeys = state.store.journeys
        .filter((j) => j.user_id === userId)
        .sort((a, b) => Date.parse(b.start_time) - Date.parse(a.start_time))
        .slice(0, limit);
      return {
        status: "success",
        count: journeys.length,
        data: journeys,
      };
    }
  );

  router.route(
    "POST",
    "/api/journeys",
    "Create a journey record",
    "origin,destination required; user_id optional with auth; mode optional",
    async ({ state, body, authUser }) => {
      const modeRaw = normalizeText(body.mode || "train");
      const mode = ["train", "bus", "taxi", "mixed"].includes(modeRaw) ? modeRaw : "train";
      const origin = String(body.origin || "").trim();
      const destination = String(body.destination || "").trim();
      if (!origin || !destination) {
        throw new HttpError(400, "origin and destination are required");
      }

      const userIdFromBody = body.user_id ? toInteger(body.user_id, "user_id") : null;
      const userId = userIdFromBody || authUser?.user_id || null;
      if (!userId) throw new HttpError(400, "user_id required (or Bearer token)");
      const userExists = state.store.users.some((u) => u.user_id === userId);
      if (!userExists) throw new HttpError(404, "user_id not found");

      const journeyId = nextCounter(state.store, "journey_id");
      const startTime = body.start_time || nowIso();
      const status = String(body.status || "planned");
      let endTime = body.end_time || null;
      const journey = {
        journey_id: journeyId,
        user_id: userId,
        start_time: startTime,
        end_time: endTime,
        status,
        mode,
        origin,
        destination,
        created_at: nowIso(),
      };

      if (mode === "train" || mode === "mixed") {
        const network = state.parsed.train_network;
        const fromStation = resolveStation(network, origin);
        const toStation = resolveStation(network, destination);
        if (fromStation && toStation) {
          const pathIds = dijkstraPath(
            buildAdjacency(network.segments, "duration_mins"),
            fromStation.id,
            toStation.id
          );
          if (pathIds && pathIds.length > 1) {
            const summary = summarizePath(network, pathIds, "dijkstra", "duration");
            const legs = summary.legs.map((leg, idx) => ({
              leg_id: nextCounter(state.store, "leg_id"),
              journey_id: journeyId,
              route_id: null,
              from_stop: leg.from_stop,
              to_stop: leg.to_stop,
              from_stop_id: leg.from_stop_id,
              to_stop_id: leg.to_stop_id,
              departure_time: idx === 0 ? startTime : null,
              arrival_time: null,
              duration_mins: leg.duration_mins,
              distance_km: leg.distance_km,
              transport_mode: "train",
            }));
            state.store.journey_legs.push(...legs);
            endTime =
              journey.end_time ||
              new Date(Date.parse(startTime) + summary.total_duration_mins * 60 * 1000).toISOString();
            journey.end_time = endTime;
            journey.estimated_duration_mins = summary.total_duration_mins;
            journey.estimated_distance_km = summary.total_distance_km;
          }
        }
      }

      state.store.journeys.push(journey);
      saveStore(state.storePath, state.store);
      return { status: "success", data: journey };
    }
  );

  router.route("GET", "/api/journeys/:journey_id", "Get journey details", "journey_id path param", async ({ state, params }) => {
    const journeyId = toInteger(params.journey_id, "journey_id");
    const journey = state.store.journeys.find((j) => j.journey_id === journeyId);
    if (!journey) throw new HttpError(404, "Journey not found");
    const legs = state.store.journey_legs.filter((l) => l.journey_id === journeyId);
    return { status: "success", data: { ...journey, legs } };
  });

  router.route(
    "GET",
    "/api/journeys/:journey_id/legs",
    "Get legs for a journey",
    "journey_id path param",
    async ({ state, params }) => {
      const journeyId = toInteger(params.journey_id, "journey_id");
      const legs = state.store.journey_legs.filter((l) => l.journey_id === journeyId);
      return {
        status: "success",
        count: legs.length,
        data: legs,
      };
    }
  );

  router.route(
    "POST",
    "/api/tickets",
    "Issue a ticket for a journey",
    "journey_id,price required",
    async ({ state, body }) => {
      const journeyId = toInteger(body.journey_id, "journey_id");
      const journey = state.store.journeys.find((j) => j.journey_id === journeyId);
      if (!journey) throw new HttpError(404, "journey_id not found");
      const price = toNumber(body.price, "price");
      const ticket = {
        ticket_id: nextCounter(state.store, "ticket_id"),
        journey_id: journeyId,
        price: Number(price.toFixed(2)),
        currency: body.currency || "GBP",
        ticket_type: body.ticket_type || "standard",
        seat_info: body.seat_info || null,
        purchase_time: nowIso(),
        status: "issued",
      };
      state.store.tickets.push(ticket);
      saveStore(state.storePath, state.store);
      return { status: "success", data: ticket };
    }
  );

  router.route("GET", "/api/tickets/:ticket_id", "Get ticket by id", "ticket_id path param", async ({ state, params }) => {
    const ticketId = toInteger(params.ticket_id, "ticket_id");
    const ticket = state.store.tickets.find((t) => t.ticket_id === ticketId);
    if (!ticket) throw new HttpError(404, "Ticket not found");
    return { status: "success", data: ticket };
  });

  router.route(
    "POST",
    "/api/payments",
    "Record payment for ticket",
    "ticket_id,amount,method required",
    async ({ state, body }) => {
      const ticketId = toInteger(body.ticket_id, "ticket_id");
      const ticket = state.store.tickets.find((t) => t.ticket_id === ticketId);
      if (!ticket) throw new HttpError(404, "ticket_id not found");
      const amount = toNumber(body.amount, "amount");
      const method = String(body.method || "").trim();
      if (!method) throw new HttpError(400, "method is required");

      const payment = {
        payment_id: nextCounter(state.store, "payment_id"),
        ticket_id: ticketId,
        amount: Number(amount.toFixed(2)),
        method,
        payment_time: nowIso(),
        status: "completed",
      };
      state.store.payments.push(payment);
      ticket.status = "paid";
      saveStore(state.storePath, state.store);
      return { status: "success", data: payment };
    }
  );

  router.route(
    "GET",
    "/api/payments/:payment_id",
    "Get payment by id",
    "payment_id path param",
    async ({ state, params }) => {
      const paymentId = toInteger(params.payment_id, "payment_id");
      const payment = state.store.payments.find((p) => p.payment_id === paymentId);
      if (!payment) throw new HttpError(404, "Payment not found");
      return { status: "success", data: payment };
    }
  );

  router.route(
    "POST",
    "/api/delay_claims",
    "Create compensation claim",
    "ticket_id,delay_minutes required",
    async ({ state, body }) => {
      const ticketId = toInteger(body.ticket_id, "ticket_id");
      const ticket = state.store.tickets.find((t) => t.ticket_id === ticketId);
      if (!ticket) throw new HttpError(404, "ticket_id not found");
      const delayMinutes = toInteger(body.delay_minutes, "delay_minutes");

      let pct = 0;
      if (delayMinutes >= 60) pct = 1;
      else if (delayMinutes >= 30) pct = 0.5;
      else if (delayMinutes >= 15) pct = 0.25;

      const compensation = Number((ticket.price * pct).toFixed(2));
      const claim = {
        claim_id: nextCounter(state.store, "claim_id"),
        ticket_id: ticketId,
        delay_minutes: delayMinutes,
        compensation_amount: compensation,
        status: compensation > 0 ? "pending" : "rejected",
        created_at: nowIso(),
      };
      state.store.delay_claims.push(claim);
      saveStore(state.storePath, state.store);

      return {
        status: "success",
        data: claim,
      };
    }
  );

  router.route(
    "GET",
    "/api/delay_claims/:claim_id",
    "Get delay claim by id",
    "claim_id path param",
    async ({ state, params }) => {
      const claimId = toInteger(params.claim_id, "claim_id");
      const claim = state.store.delay_claims.find((c) => c.claim_id === claimId);
      if (!claim) throw new HttpError(404, "Delay claim not found");
      return { status: "success", data: claim };
    }
  );

  router.route(
    "POST",
    "/api/disruption_reports",
    "Submit a disruption report",
    "user_id,description required; stop_id optional",
    async ({ state, body }) => {
      const userId = toInteger(body.user_id, "user_id");
      const user = state.store.users.find((u) => u.user_id === userId);
      if (!user) throw new HttpError(404, "user_id not found");
      const description = String(body.description || "").trim();
      if (!description) throw new HttpError(400, "description is required");

      const report = {
        report_id: nextCounter(state.store, "report_id"),
        user_id: userId,
        stop_id: body.stop_id || null,
        description,
        report_time: nowIso(),
        confirmed: false,
      };
      state.store.disruption_reports.push(report);
      saveStore(state.storePath, state.store);
      return { status: "success", data: report };
    }
  );

  router.route(
    "GET",
    "/api/disruption_reports",
    "List disruption reports",
    "confirmed (optional), limit(optional)",
    async ({ state, query }) => {
      const confirmedRaw = query.get("confirmed");
      const limit = parseLimit(query.get("limit"), 200, 1, 2000);
      let records = state.store.disruption_reports;
      if (confirmedRaw !== null) {
        const confirmed = normalizeText(confirmedRaw) === "true";
        records = records.filter((r) => Boolean(r.confirmed) === confirmed);
      }
      records = records
        .slice()
        .sort((a, b) => Date.parse(b.report_time) - Date.parse(a.report_time))
        .slice(0, limit);
      return {
        status: "success",
        count: records.length,
        data: records,
      };
    }
  );

  router.route(
    "POST",
    "/api/disruption_reports/:report_id/confirm",
    "Confirm a disruption report",
    "report_id path param",
    async ({ state, params }) => {
      const reportId = toInteger(params.report_id, "report_id");
      const report = state.store.disruption_reports.find((r) => r.report_id === reportId);
      if (!report) throw new HttpError(404, "Disruption report not found");
      report.confirmed = true;
      report.confirmed_at = nowIso();
      saveStore(state.storePath, state.store);
      return { status: "success", data: report };
    }
  );

  router.route("GET", "/api/operators", "Transport operators summary", null, async ({ state }) => {
    const operators = new Map();
    for (const service of state.parsed.departures.services) {
      const code = service.operator_code || service.operator || "UNKNOWN";
      if (!operators.has(code)) {
        operators.set(code, {
          operator_id: operators.size + 1,
          name: service.operator || code,
          operator_code: code,
          type: "rail",
        });
      }
    }
    for (const row of state.datasets.bus_reference.results || []) {
      const code = row.noc || row.operatorName || String(row.id);
      if (!operators.has(code)) {
        operators.set(code, {
          operator_id: operators.size + 1,
          name: row.operatorName || row.name || code,
          operator_code: code,
          type: "bus",
        });
      }
    }
    return {
      status: "success",
      count: operators.size,
      data: [...operators.values()],
    };
  });

  router.route(
    "GET",
    "/api/vehicles/live",
    "Live vehicles view",
    "line optional",
    async ({ state, query }) => {
      const line = normalizeText(query.get("line") || "");
      let vehicles = state.parsed.bus_live.map((item) => ({
        vehicle_id: item.vehicle_ref,
        operator_id: item.operator_ref,
        vehicle_type: "bus",
        line_ref: item.line_ref,
        location: item.location,
        recorded_at: item.recorded_at,
      }));
      if (line) vehicles = vehicles.filter((v) => normalizeText(v.line_ref) === line);
      return { status: "success", count: vehicles.length, data: vehicles };
    }
  );

  router.route(
    "GET",
    "/api/vehicles/:vehicle_id/location",
    "Vehicle location by id",
    "vehicle_id path param",
    async ({ state, params }) => {
      const vehicle = normalizeText(params.vehicle_id);
      const found = state.parsed.bus_live.find(
        (item) => normalizeText(item.vehicle_ref) === vehicle
      );
      if (!found) throw new HttpError(404, "Vehicle not found");
      return {
        status: "success",
        data: {
          location_id: randomId("loc"),
          vehicle_id: found.vehicle_ref,
          latitude: found.location?.latitude ?? null,
          longitude: found.location?.longitude ?? null,
          timestamp: found.recorded_at,
          occupancy_level: null,
        },
      };
    }
  );

  const server = http.createServer((req, res) => router.handle(req, res));
  return { server, router, state };
}

if (require.main === module) {
  const app = createAppServer();
  const port = Number(process.env.PORT || 5000);
  app.server.listen(port, "0.0.0.0", () => {
    console.log(`[backend] listening on http://0.0.0.0:${port}`);
  });
}

module.exports = {
  createAppServer,
  buildState,
  parseDepartureBoard,
  parseBusLive,
  parseVms,
  parseNaptanStops,
};
