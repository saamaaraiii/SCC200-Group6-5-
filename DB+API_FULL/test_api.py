#!/usr/bin/env python3
"""
Quick Start Guide & Testing Script for DB_Scc Transport Routing API

This script demonstrates:
1. Starting the API server
2. Testing all major endpoints
3. Validating data and graph structure
"""

import requests
import json
import time
import sys
from pathlib import Path

# Configuration
API_URL = "http://localhost:5000"
TIMEOUT = 10

# Colors for terminal output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    ENDC = '\033[0m'

def print_success(msg):
    print(f"{Colors.GREEN}✓{Colors.ENDC} {msg}")

def print_error(msg):
    print(f"{Colors.RED}✗{Colors.ENDC} {msg}")

def print_info(msg):
    print(f"{Colors.BLUE}ℹ{Colors.ENDC} {msg}")

def print_warning(msg):
    print(f"{Colors.YELLOW}⚠{Colors.ENDC} {msg}")

def test_health():
    """Test server health check"""
    print("\n--- Health Check ---")
    try:
        response = requests.get(f"{API_URL}/health", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()
            print_success(f"Server is healthy: {data.get('status')}")
            return True
        else:
            print_error(f"Health check failed: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Could not connect to server: {e}")
        return False

def test_endpoints():
    """List all available endpoints"""
    print("\n--- Available Endpoints ---")
    try:
        response = requests.get(f"{API_URL}/api/endpoints", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()
            endpoints = data.get('endpoints', [])
            print_success(f"Found {len(endpoints)} endpoints:")
            for ep in endpoints[:5]:  # Show first 5
                print(f"  {ep['method']:4} {ep['path']:40} - {ep['description']}")
            if len(endpoints) > 5:
                print(f"  ... and {len(endpoints) - 5} more")
            return True
        else:
            print_error(f"Could not fetch endpoints: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Endpoints request failed: {e}")
        return False

def test_graph_stats():
    """Get graph statistics"""
    print("\n--- Graph Statistics ---")
    try:
        response = requests.get(f"{API_URL}/api/graph/stats", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()['data']
            print_success("Graph loaded successfully:")
            print(f"  Nodes: {data['nodes']['total']} (rail: {data['nodes']['rail_stations']}, bus: {data['nodes']['bus_stops']})")
            print(f"  Edges: {data['edges']['total']} (rail: {data['edges']['rail']}, bus: {data['edges']['bus']}, walk: {data['edges']['walk']})")
            return True
        else:
            print_error(f"Could not fetch graph stats: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Graph stats request failed: {e}")
        return False

def test_stations():
    """List available stations"""
    print("\n--- Available Stations ---")
    try:
        response = requests.get(f"{API_URL}/api/stations?limit=10", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()
            stations = data['data']
            print_success(f"Found {data['count']} stations (showing first 10):")
            for station in stations:
                print(f"  {station['crs']:4} {station['name']:30} ({station['type']:10}) - {station['connections']} connections")
            return True
        else:
            print_error(f"Could not fetch stations: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Stations request failed: {e}")
        return False

def test_station_info(crs="LAN"):
    """Get station information"""
    print(f"\n--- Station Info ({crs}) ---")
    try:
        response = requests.get(f"{API_URL}/api/station/{crs}", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()['data']
            print_success(f"Station {crs} found:")
            print(f"  Name: {data['name']}")
            print(f"  Type: {data['type']}")
            print(f"  Coordinates: ({data['coordinates']['lat']}, {data['coordinates']['lon']})")
            print(f"  Connections: {data['connections']}")
            return True
        elif response.status_code == 404:
            print_warning(f"Station {crs} not found in database")
            return False
        else:
            print_error(f"Could not fetch station info: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Station info request failed: {e}")
        return False

def test_route(from_crs="LAN", to_crs="PRE"):
    """Test shortest route calculation"""
    print(f"\n--- Shortest Route ({from_crs} → {to_crs}) ---")
    try:
        response = requests.get(
            f"{API_URL}/api/route?from={from_crs}&to={to_crs}",
            timeout=TIMEOUT
        )
        if response.status_code == 200:
            data = response.json()
            print_success(f"Route found:")
            print(f"  Total time: {data['total_time_minutes']} minutes")
            print(f"  Number of legs: {data['num_legs']}")
            print(f"  Path: {' → '.join(data['path'])}")
            if data.get('legs'):
                print("\n  Legs:")
                for i, leg in enumerate(data['legs'], 1):
                    print(f"    {i}. {leg['from_name']:20} → {leg['to_name']:20} ({leg['duration_minutes']:3} min, {leg['mode']})")
            return True
        elif response.status_code == 404:
            print_warning(f"No route found between {from_crs} and {to_crs}")
            return False
        elif response.status_code == 400:
            print_error(f"Invalid request: check CRS codes")
            return False
        else:
            print_error(f"Route calculation failed: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Route request failed: {e}")
        return False

def test_realtime_status():
    """Check real-time update status"""
    print("\n--- Real-Time Updates Status ---")
    try:
        response = requests.get(f"{API_URL}/api/realtime/status", timeout=TIMEOUT)
        if response.status_code == 200:
            data = response.json()['realtime']
            if data['connected']:
                print_success("Real-time connection: ACTIVE")
            else:
                print_warning("Real-time connection: INACTIVE (not enabled)")
            print(f"  Subscriptions: {len(data['subscriptions'])}")
            print(f"  Recent updates: {data['recent_updates']}")
            print(f"  Known delays: {data['known_delays']}")
            return True
        else:
            print_error(f"Could not fetch realtime status: {response.status_code}")
            return False
    except Exception as e:
        print_error(f"Realtime status request failed: {e}")
        return False

def run_all_tests():
    """Run all test suites"""
    print(f"\n{'='*70}")
    print(f"  DB_Scc Transport Routing API - Test Suite")
    print(f"  API URL: {API_URL}")
    print(f"{'='*70}")
    
    tests = [
        ("Health Check", test_health),
        ("Endpoints List", test_endpoints),
        ("Graph Statistics", test_graph_stats),
        ("Stations List", test_stations),
        ("Station Info", test_station_info),
        ("Route Calculation", test_route),
        ("Real-Time Status", test_realtime_status),
    ]
    
    results = {}
    for test_name, test_func in tests:
        try:
            results[test_name] = test_func()
        except Exception as e:
            print_error(f"Test '{test_name}' crashed: {e}")
            results[test_name] = False
    
    # Summary
    print(f"\n{'='*70}")
    print("  Test Summary")
    print(f"{'='*70}")
    
    passed = sum(1 for v in results.values() if v)
    total = len(results)
    
    for test_name, passed_flag in results.items():
        status = f"{Colors.GREEN}PASS{Colors.ENDC}" if passed_flag else f"{Colors.RED}FAIL{Colors.ENDC}"
        print(f"  {test_name:30} {status}")
    
    print(f"\n  Results: {passed}/{total} tests passed")
    
    if passed == total:
        print_success("All tests passed! API is working correctly.")
        return 0
    else:
        print_warning(f"{total - passed} tests failed. Check server logs for details.")
        return 1

def interactive_mode():
    """Interactive testing mode"""
    print(f"\n{'='*70}")
    print("  Interactive Mode - Test Individual Endpoints")
    print(f"{'='*70}")
    
    while True:
        print("\nOptions:")
        print("  1. Test route between two stations")
        print("  2. Get station information")
        print("  3. List all stations")
        print("  4. Get graph statistics")
        print("  5. Check real-time status")
        print("  6. Run all tests")
        print("  0. Exit")
        
        choice = input("\nEnter choice (0-6): ").strip()
        
        if choice == '0':
            break
        elif choice == '1':
            from_crs = input("From (CRS code, e.g., LAN): ").strip().upper()
            to_crs = input("To (CRS code, e.g., PRE): ").strip().upper()
            test_route(from_crs, to_crs)
        elif choice == '2':
            crs = input("CRS code (e.g., LAN): ").strip().upper()
            test_station_info(crs)
        elif choice == '3':
            test_stations()
        elif choice == '4':
            test_graph_stats()
        elif choice == '5':
            test_realtime_status()
        elif choice == '6':
            run_all_tests()
        else:
            print("Invalid choice")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="DB_Scc Transport API Testing")
    parser.add_argument('--interactive', '-i', action='store_true', help='Interactive mode')
    parser.add_argument('--url', default='http://localhost:5000', help='API URL')
    args = parser.parse_args()
    
    API_URL = args.url
    
    print(f"\nTesting API at: {API_URL}")
    print("Make sure the server is running: python api_server.py")
    
    try:
        if args.interactive:
            interactive_mode()
        else:
            exit_code = run_all_tests()
            sys.exit(exit_code)
    except KeyboardInterrupt:
        print("\n\nTest interrupted by user")
        sys.exit(1)
