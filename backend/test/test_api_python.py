import json
import os
import sys
import unittest
from pathlib import Path

# Ensure backend/ is importable
TEST_DIR = Path(__file__).resolve().parent
BACKEND_DIR = TEST_DIR.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from main import create_app  # noqa: E402


class BackendApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_store = TEST_DIR / "tmp-python-runtime-store.json"
        if cls.temp_store.exists():
            cls.temp_store.unlink()

        cls.app = create_app({"store_path": str(cls.temp_store)})
        cls.client = cls.app.test_client()

        cls.auth_token = None
        cls.user_id = None
        cls.journey_id = None
        cls.ticket_id = None

    @classmethod
    def tearDownClass(cls):
        if cls.temp_store.exists():
            cls.temp_store.unlink()

    def _get(self, endpoint, token=None):
        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        response = self.client.get(endpoint, headers=headers)
        return response, response.get_json(silent=True)

    def _post(self, endpoint, payload, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        response = self.client.post(endpoint, data=json.dumps(payload), headers=headers)
        return response, response.get_json(silent=True)

    def test_01_health_and_discovery(self):
        response, data = self._get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "healthy")

        response, data = self._get("/api/endpoints")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        paths = [item["path"] for item in data["endpoints"]]
        self.assertIn("/api/departures", paths)
        self.assertIn("/api/places/search", paths)
        self.assertIn("/api/taxi/quote", paths)

    def test_02_core_data_endpoints(self):
        response, data = self._get("/api/lancs_fac")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertTrue(data["data"]["station_name"])

        response, data = self._get("/api/departures?limit=3")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertLessEqual(data["count"], 3)

        response, data = self._get("/api/departures/search?destination=Glasgow&limit=5")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

        response, data = self._get("/api/bus/live?line=74&limit=5")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        for row in data["data"]:
            self.assertEqual(str(row.get("line_ref")), "74")

        response, data = self._get("/api/weather")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

    def test_03_delay_and_places_endpoints(self):
        response, data = self._get("/api/delay_codes/search?q=signal")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

        response, data = self._get("/api/places/categories")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertGreater(len(data["data"]), 0)

        response, data = self._get("/api/places/stats")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertGreater(data["data"]["total_places"], 0)

        response, data = self._get("/api/places/nearby?radius=50&limit=10")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

        response, data = self._get("/api/places/semantic?q=family day out&threshold=0&limit=5")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

    def test_04_taxi_route_user_ticket_flow(self):
        response, data = self._post(
            "/api/taxi/quote",
            {
                "pickup_latitude": 54.0466,
                "pickup_longitude": -2.8007,
                "dropoff_latitude": 54.0501,
                "dropoff_longitude": -2.7899,
                "passengers": 2,
            },
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        quote_id = data["data"][0]["quote_id"]

        response, data = self._post("/api/taxi/bookings", {"quote_id": quote_id})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

        response, data = self._get("/api/routes/find?from=EUS&to=GLC&algorithm=dijkstra&weight=duration")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertGreater(len(data["data"]["legs"]), 0)

        response, data = self._get("/api/routes/find?from=LAN&to=PRE")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.assertGreater(len(data["data"]["legs"]), 0)

        response, data = self._post(
            "/api/users/register",
            {
                "name": "Python Test User",
                "email": "python.user@example.com",
                "password": "Secret123!",
                "accessibility_needs": True,
            },
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.__class__.user_id = data["data"]["user_id"]

        response, _ = self._post(
            "/api/users/register",
            {
                "name": "Python Test User",
                "email": "python.user@example.com",
                "password": "Secret123!",
            },
        )
        self.assertEqual(response.status_code, 409)

        response, data = self._post(
            "/api/users/login",
            {"email": "python.user@example.com", "password": "Secret123!"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.__class__.auth_token = data["data"]["token"]

        response, data = self._post(
            "/api/journeys",
            {"origin": "EUS", "destination": "GLC", "mode": "train"},
            token=self.__class__.auth_token,
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")
        self.__class__.journey_id = data["data"]["journey_id"]

        response, data = self._post(
            "/api/tickets",
            {
                "journey_id": self.__class__.journey_id,
                "price": 42.5,
                "currency": "GBP",
                "ticket_type": "return",
            },
        )
        self.assertEqual(response.status_code, 200)
        self.__class__.ticket_id = data["data"]["ticket_id"]

        response, data = self._post(
            "/api/payments",
            {"ticket_id": self.__class__.ticket_id, "amount": 42.5, "method": "card"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], "success")

        response, data = self._post(
            "/api/delay_claims",
            {"ticket_id": self.__class__.ticket_id, "delay_minutes": 35},
        )
        self.assertEqual(response.status_code, 200)
        self.assertAlmostEqual(data["data"]["compensation_amount"], 21.25)

    def test_05_disruption_flow_and_errors(self):
        response, data = self._post(
            "/api/disruption_reports",
            {
                "user_id": self.__class__.user_id,
                "description": "Signal issue reported at platform 3",
            },
        )
        self.assertEqual(response.status_code, 200)
        report_id = data["data"]["report_id"]

        response, data = self._get("/api/disruption_reports?confirmed=false")
        self.assertEqual(response.status_code, 200)
        ids = [item["report_id"] for item in data["data"]]
        self.assertIn(report_id, ids)

        response, data = self._post(f"/api/disruption_reports/{report_id}/confirm", {})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(data["data"]["confirmed"])

        response, _ = self._get("/api/departures/search")
        self.assertEqual(response.status_code, 400)

        response, _ = self._get("/api/routes/find?from=UNKNOWN&to=GLC")
        self.assertEqual(response.status_code, 404)

        response, _ = self._get("/api/not-real")
        self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()
