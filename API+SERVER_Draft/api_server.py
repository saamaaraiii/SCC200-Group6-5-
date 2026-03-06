"""
DB_Scc REST API Server
Handles GET requests from iOS (Swift) and Android (APK) clients
Serves data from JSON, XML, and text data streams
Each function returns standardized JSON responses for client consumption
"""

import Flask, request, jsonify
import CORS
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime
import logging

app = Flask(__name__)
CORS(app)  # Enable CORS for mobile clients

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Data directory path
DATA_DIR = Path(__file__).parent

# ============================================================================
# LANCASTER FACILITY DATA PARSERS/nptg/nptg.xml
 
# ============================================================================

@app.route('/api/lancs_fac', methods=['GET'])
def get_lancs_fac():
    """
    GET /api/lancs_fac
    Returns complete Lancaster station facility information.
    Used by: iOS/Android for displaying station info, accessibility, facilities
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        return jsonify({
            "status": "success",
            "data": {
                "station_name": data.get("name"),
                "crs_code": data.get("crsCode"),
                "nrcc_code": data.get("nationalLocationCode"),
                "location": data.get("location"),
                "address": data.get("address"),
                "operator": data.get("stationOperator", {}).get("name"),
                "staffing_level": data.get("staffingLevel"),
                "alerts": data.get("stationAlerts", [])
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching lancs_fac: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/accessibility', methods=['GET'])
def get_lancs_accessibility():
    """
    GET /api/lancs_fac/accessibility
    Returns accessibility features (step-free, lifts, tactile paving, etc.)
    Used by: iOS/Android accessibility filtering
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        accessibility = data.get("stationAccessibility", {})
        return jsonify({
            "status": "success",
            "data": {
                "step_free_category": accessibility.get("stepFreeCategory", {}).get("category"),
                "tactile_paving": accessibility.get("tactilePaving"),
                "induction_loops": accessibility.get("inductionLoop", {}).get("provision"),
                "wheelchairs_available": accessibility.get("wheelchairsAvailable"),
                "passenger_assistance": accessibility.get("passengerAssistance", []),
                "train_ramp": accessibility.get("trainRamp", {}).get("available"),
                "ticket_barriers": accessibility.get("ticketBarriers", {}).get("available"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching accessibility: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/lifts', methods=['GET'])
def get_lancs_lifts():
    """
    GET /api/lancs_fac/lifts
    Returns lift information and locations
    Used by: iOS/Android for navigation planning
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        lifts_data = data.get("lifts", {})
        return jsonify({
            "status": "success",
            "data": {
                "available": lifts_data.get("available"),
                "statement": lifts_data.get("statement"),
                "lifts_info": lifts_data.get("liftsInfo", [])
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching lifts: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/ticket_buying', methods=['GET'])
def get_lancs_ticket_buying():
    """
    GET /api/lancs_fac/ticket_buying
    Returns ticket office, machines, and online booking info
    Used by: iOS/Android for journey planning
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        tickets = data.get("ticketBuying", {})
        return jsonify({
            "status": "success",
            "data": {
                "ticket_office": tickets.get("ticketOffice", {}),
                "machines_available": tickets.get("ticketMachinesAvailable"),
                "collect_online": tickets.get("collectOnlineBookedTickets", {}),
                "pay_as_you_go": tickets.get("payAsYouGo", {})
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching ticket info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/transport_links', methods=['GET'])
def get_lancs_transport_links():
    """
    GET /api/lancs_fac/transport_links
    Returns onward transport connectivity (bus, taxi, airport, etc.)
    Used by: iOS/Android for multi-modal journey planning
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        transport = data.get("transportLinks", {})
        return jsonify({
            "status": "success",
            "data": {
                "bus": transport.get("bus", {}).get("available"),
                "replacement_bus": transport.get("replacementBus", {}).get("available"),
                "taxi": transport.get("taxi", {}).get("available"),
                "taxi_ranks": transport.get("taxi", {}).get("taxiRanks", []),
                "airport": transport.get("airport", {}).get("available"),
                "underground": transport.get("underground", {}).get("available"),
                "car_hire": transport.get("carHire", {}).get("available"),
                "port": transport.get("port", {}).get("available"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching transport links: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/cycling', methods=['GET'])
def get_lancs_cycling():
    """
    GET /api/lancs_fac/cycling
    Returns cycle storage and hire information
    Used by: iOS/Android for cyclist journey planning
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        cycling = data.get("cycling", {})
        return jsonify({
            "status": "success",
            "data": {
                "storage_available": cycling.get("cycleStorageAvailable"),
                "spaces": cycling.get("spaces", {}).get("numberOfSpaces"),
                "storage_types": cycling.get("typesOfStorage", []),
                "sheltered": cycling.get("sheltered"),
                "cctv": cycling.get("cctv"),
                "location": cycling.get("location"),
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching cycling info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/lancs_fac/parking', methods=['GET'])
def get_lancs_parking():
    """
    GET /api/lancs_fac/parking
    Returns car park information (spaces, accessibility, charges)
    Used by: iOS/Android for parking lookup
    """
    try:
        with open(DATA_DIR / 'lancs_fac.json', 'r') as f:
            data = json.load(f)
        
        car_parks = data.get("carParks", {})
        return jsonify({
            "status": "success",
            "data": {
                "car_parks": car_parks
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching parking info: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# TRAIN DEPARTURE BOARD PARSERS (XML)
# ============================================================================

def parse_xml_departure_board(xml_path: Path) -> List[Dict[str, Any]]:
    """
    Parse XML departure board and extract train services
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        # Define namespaces
        namespaces = {
            'lt4': 'http://thalesgroup.com/RTTI/2015-11-27/ldb/types',
            'lt8': 'http://thalesgroup.com/RTTI/2021-11-01/ldb/types',
            'lt5': 'http://thalesgroup.com/RTTI/2016-02-16/ldb/types'
        }
        
        services = []
        
        # Extract station info
        station_name = root.findtext('.//lt4:locationName', namespaces=namespaces)
        crs = root.findtext('.//lt4:crs', namespaces=namespaces)
        
        # Extract all train services
        for service in root.findall('.//lt8:service', namespaces=namespaces):
            std = service.findtext('lt4:std', namespaces=namespaces)
            etd = service.findtext('lt4:etd', namespaces=namespaces)
            platform = service.findtext('lt4:platform', namespaces=namespaces)
            operator = service.findtext('lt4:operator', namespaces=namespaces)
            delay_reason = service.findtext('lt4:delayReason', namespaces=namespaces)
            
            # Extract origin and destination
            origin_elem = service.find('.//lt5:origin', namespaces=namespaces)
            dest_elem = service.find('.//lt5:destination', namespaces=namespaces)
            
            origin = origin_elem.findtext('lt4:location/lt4:locationName', namespaces=namespaces) if origin_elem else None
            destination = dest_elem.findtext('lt4:location/lt4:locationName', namespaces=namespaces) if dest_elem else None
            
            # Extract calling points
            calling_points = []
            for cp in service.findall('.//lt8:callingPoint', namespaces=namespaces):
                calling_points.append({
                    "location": cp.findtext('lt8:locationName', namespaces=namespaces),
                    "scheduled_time": cp.findtext('lt8:st', namespaces=namespaces),
                    "estimated_time": cp.findtext('lt8:et', namespaces=namespaces)
                })
            
            services.append({
                "scheduled_departure": std,
                "estimated_departure": etd,
                "platform": platform,
                "operator": operator,
                "origin": origin,
                "destination": destination,
                "delay_reason": delay_reason,
                "calling_points": calling_points
            })
        
        return {
            "station": station_name,
            "crs": crs,
            "services": services
        }
    except Exception as e:
        logger.error(f"Error parsing XML: {str(e)}")
        raise


@app.route('/api/departures', methods=['GET'])
def get_departures():
    """
    GET /api/departures
    Returns train departures from Lancaster station (from XML feed)
    Query params: ?limit=5 (default: all)
    Used by: iOS/Android for real-time departure info
    """
    try:
        limit = request.args.get('limit', type=int, default=None)
        
        board_data = parse_xml_departure_board(DATA_DIR / 'lancs_station_depart')
        
        services = board_data['services']
        if limit:
            services = services[:limit]
        
        return jsonify({
            "status": "success",
            "station": board_data['station'],
            "crs": board_data['crs'],
            "timestamp": datetime.now().isoformat(),
            "count": len(services),
            "data": services
        }), 200
    except Exception as e:
        logger.error(f"Error fetching departures: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/departures/search', methods=['GET'])
def search_departures():
    """
    GET /api/departures/search?destination=Glasgow&limit=3
    Search departures by destination
    Used by: iOS/Android for filtering by destination
    """
    try:
        destination = request.args.get('destination', '', type=str).lower()
        limit = request.args.get('limit', type=int, default=None)
        
        if not destination:
            return jsonify({"status": "error", "message": "destination parameter required"}), 400
        
        board_data = parse_xml_departure_board(DATA_DIR / 'lancs_station_depart')
        
        filtered = [s for s in board_data['services'] if destination in (s.get('destination') or '').lower()]
        
        if limit:
            filtered = filtered[:limit]
        
        return jsonify({
            "status": "success",
            "search_query": destination,
            "count": len(filtered),
            "data": filtered
        }), 200
    except Exception as e:
        logger.error(f"Error searching departures: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# BUS LIVE TRACKING PARSERS (XML)
# ============================================================================

def parse_bus_xml(xml_path: Path) -> List[Dict[str, Any]]:
    """
    Parse SIRI bus live tracking XML
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        namespaces = {
            'siri': 'http://www.siri.org.uk/siri'
        }
        
        vehicles = []
        
        for vehicle in root.findall('.//siri:VehicleActivity', namespaces=namespaces):
            journey = vehicle.find('.//siri:MonitoredVehicleJourney', namespaces=namespaces)
            
            if journey is None:
                continue
            
            location = journey.find('.//siri:VehicleLocation', namespaces=namespaces)
            
            vehicles.append({
                "line_ref": journey.findtext('siri:LineRef', namespaces=namespaces),
                "vehicle_ref": journey.findtext('siri:VehicleRef', namespaces=namespaces),
                "direction": journey.findtext('siri:DirectionRef', namespaces=namespaces),
                "origin_name": journey.findtext('siri:OriginName', namespaces=namespaces),
                "destination_name": journey.findtext('siri:DestinationName', namespaces=namespaces),
                "origin_time": journey.findtext('siri:OriginAimedDepartureTime', namespaces=namespaces),
                "destination_time": journey.findtext('siri:DestinationAimedArrivalTime', namespaces=namespaces),
                "location": {
                    "latitude": float(location.findtext('siri:Latitude', namespaces=namespaces) or 0),
                    "longitude": float(location.findtext('siri:Longitude', namespaces=namespaces) or 0)
                } if location is not None else None,
                "bearing": journey.findtext('siri:Bearing', namespaces=namespaces),
                "recorded_at": vehicle.findtext('siri:RecordedAtTime', namespaces=namespaces)
            })
        
        return vehicles
    except Exception as e:
        logger.error(f"Error parsing bus XML: {str(e)}")
        raise


@app.route('/api/bus/live', methods=['GET'])
def get_bus_live():
    """
    GET /api/bus/live
    Returns live bus tracking data
    Query params: ?line=74 (optional line filter), ?limit=10
    Used by: iOS/Android for real-time bus tracking
    """
    try:
        line_filter = request.args.get('line', type=str, default=None)
        limit = request.args.get('limit', type=int, default=None)
        
        vehicles = parse_bus_xml(DATA_DIR / 'busLive.xml')
        
        if line_filter:
            vehicles = [v for v in vehicles if v['line_ref'] == line_filter]
        
        if limit:
            vehicles = vehicles[:limit]
        
        return jsonify({
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            "count": len(vehicles),
            "data": vehicles
        }), 200
    except Exception as e:
        logger.error(f"Error fetching bus live data: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/bus/live/location', methods=['GET'])
def get_bus_location():
    """
    GET /api/bus/live/location?vehicle=BU25_YSD
    Returns specific vehicle location
    Used by: iOS/Android for vehicle tracking
    """
    try:
        vehicle_ref = request.args.get('vehicle', type=str, default=None)
        
        if not vehicle_ref:
            return jsonify({"status": "error", "message": "vehicle parameter required"}), 400
        
        vehicles = parse_bus_xml(DATA_DIR / 'busLive.xml')
        vehicle = next((v for v in vehicles if v['vehicle_ref'] == vehicle_ref), None)
        
        if not vehicle:
            return jsonify({"status": "error", "message": "Vehicle not found"}), 404
        
        return jsonify({
            "status": "success",
            "data": vehicle
        }), 200
    except Exception as e:
        logger.error(f"Error fetching bus location: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# DELAY CODES PARSER (JSON)
# ============================================================================

@app.route('/api/delay_codes', methods=['GET'])
def get_delay_codes():
    """
    GET /api/delay_codes
    Returns all delay codes (reason + abbreviation)
    Query params: ?code=IA (search by specific code)
    Used by: iOS/Android for understanding delay reasons
    """
    try:
        code_search = request.args.get('code', type=str, default=None)
        
        with open(DATA_DIR / 'delay-codes.json', 'r') as f:
            codes = json.load(f)
        
        if code_search:
            codes = [c for c in codes if c['Code'].upper() == code_search.upper()]
            if not codes:
                return jsonify({"status": "error", "message": f"Code {code_search} not found"}), 404
        
        return jsonify({
            "status": "success",
            "count": len(codes),
            "data": codes
        }), 200
    except Exception as e:
        logger.error(f"Error fetching delay codes: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/api/delay_codes/search', methods=['GET'])
def search_delay_codes():
    """
    GET /api/delay_codes/search?q=signalling
    Search delay codes by keyword
    Used by: iOS/Android for delay reason lookup
    """
    try:
        query = request.args.get('q', type=str, default='').lower()
        
        if not query:
            return jsonify({"status": "error", "message": "q parameter required"}), 400
        
        with open(DATA_DIR / 'delay-codes.json', 'r') as f:
            codes = json.load(f)
        
        results = [c for c in codes if query in c['Cause'].lower() or query in c['Abbreviation'].lower()]
        
        return jsonify({
            "status": "success",
            "query": query,
            "count": len(results),
            "data": results
        }), 200
    except Exception as e:
        logger.error(f"Error searching delay codes: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# WEATHER DATA PARSER (JSON)
# ============================================================================

@app.route('/api/weather', methods=['GET'])
def get_weather():
    """
    GET /api/weather
    Returns current weather information for Lancaster area
    Used by: iOS/Android for weather context
    """
    try:
        with open(DATA_DIR / 'weather.json', 'r') as f:
            weather_data = json.load(f)
        
        weather = weather_data.get('weather', {})
        main = weather.get('main', {})
        
        return jsonify({
            "status": "success",
            "data": {
                "location": {
                    "name": weather.get('name'),
                    "latitude": weather.get('coord', {}).get('lat'),
                    "longitude": weather.get('coord', {}).get('lon')
                },
                "conditions": {
                    "main": weather.get('weather', [{}])[0].get('main'),
                    "description": weather.get('weather', [{}])[0].get('description'),
                    "icon": weather.get('weather', [{}])[0].get('icon')
                },
                "temperature": {
                    "current": main.get('temp'),
                    "feels_like": main.get('feels_like'),
                    "min": main.get('temp_min'),
                    "max": main.get('temp_max')
                },
                "wind": {
                    "speed": weather.get('wind', {}).get('speed'),
                    "direction": weather.get('wind', {}).get('deg'),
                    "gust": weather.get('wind', {}).get('gust')
                },
                "humidity": main.get('humidity'),
                "visibility": weather.get('visibility'),
                "clouds": weather.get('clouds', {}).get('all'),
                "timestamp": weather.get('dt')
            }
        }), 200
    except Exception as e:
        logger.error(f"Error fetching weather: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# ============================================================================
# HEALTH CHECK & STATUS ENDPOINTS
# ============================================================================

@app.route('/health', methods=['GET'])
def health_check():
    """
    GET /health
    Health check endpoint for monitoring
    """
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "version": "1.0"
    }), 200


@app.route('/api/endpoints', methods=['GET'])
def list_endpoints():
    """
    GET /api/endpoints
    Lists all available API endpoints
    Used by: iOS/Android for endpoint discovery
    """
    endpoints = [
        {"method": "GET", "path": "/health", "description": "Health check"},
        {"method": "GET", "path": "/api/lancs_fac", "description": "Lancaster facility data"},
        {"method": "GET", "path": "/api/lancs_fac/accessibility", "description": "Accessibility info"},
        {"method": "GET", "path": "/api/lancs_fac/lifts", "description": "Lift information"},
        {"method": "GET", "path": "/api/lancs_fac/ticket_buying", "description": "Ticket office info"},
        {"method": "GET", "path": "/api/lancs_fac/transport_links", "description": "Transport connectivity"},
        {"method": "GET", "path": "/api/lancs_fac/cycling", "description": "Cycle storage/hire"},
        {"method": "GET", "path": "/api/lancs_fac/parking", "description": "Car park info"},
        {"method": "GET", "path": "/api/departures", "description": "Train departures", "params": "limit (optional)"},
        {"method": "GET", "path": "/api/departures/search", "description": "Search departures by destination", "params": "destination (required), limit (optional)"},
        {"method": "GET", "path": "/api/bus/live", "description": "Live bus tracking", "params": "line (optional), limit (optional)"},
        {"method": "GET", "path": "/api/bus/live/location", "description": "Specific bus location", "params": "vehicle (required)"},
        {"method": "GET", "path": "/api/delay_codes", "description": "All delay codes", "params": "code (optional)"},
        {"method": "GET", "path": "/api/delay_codes/search", "description": "Search delay codes", "params": "q (required)"},
        {"method": "GET", "path": "/api/weather", "description": "Current weather"},
        {"method": "GET", "path": "/api/endpoints", "description": "This endpoint list"},
    ]
    return jsonify({"status": "success", "endpoints": endpoints}), 200


# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.errorhandler(404)
def not_found(error):
    return jsonify({"status": "error", "message": "Endpoint not found"}), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({"status": "error", "message": "Internal server error"}), 500


if __name__ == '__main__':
    logger.info("Starting DB_Scc API Server...")
    app.run(host='0.0.0.0', port=5000, debug=False)
