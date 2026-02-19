-- Seed data: sample UK-style stations and segments for pathfinding

INSERT OR IGNORE INTO stations (id, name, code, latitude, longitude) VALUES
(1, 'London Euston', 'EUS', 51.5282, -0.1337),
(2, 'Birmingham New Street', 'BHM', 52.4778, -1.8980),
(3, 'Manchester Piccadilly', 'MAN', 53.4770, -2.2301),
(4, 'Liverpool Lime Street', 'LIV', 53.4075, -2.9988),
(5, 'Leeds', 'LDS', 53.7944, -1.5473),
(6, 'Sheffield', 'SHF', 53.3781, -1.4620),
(7, 'York', 'YRK', 53.9581, -1.0916),
(8, 'Newcastle', 'NCL', 54.9683, -1.6174),
(9, 'Edinburgh Waverley', 'EDB', 55.9521, -3.1895),
(10, 'Glasgow Central', 'GLC', 55.8599, -4.2572);

-- Route segments: from_station_id, to_station_id, distance_km, duration_mins
-- (Bidirectional where applicable)
INSERT OR IGNORE INTO route_segments (from_station_id, to_station_id, distance_km, duration_mins) VALUES
(1, 2, 170, 82),   -- Euston -> Birmingham
(2, 1, 170, 82),
(2, 3, 120, 88),   -- Birmingham -> Manchester
(3, 2, 120, 88),
(3, 4, 50, 35),    -- Manchester -> Liverpool
(4, 3, 50, 35),
(3, 6, 60, 48),    -- Manchester -> Sheffield
(6, 3, 60, 48),
(6, 5, 40, 35),    -- Sheffield -> Leeds
(5, 6, 40, 35),
(5, 7, 45, 25),    -- Leeds -> York
(7, 5, 45, 25),
(7, 8, 80, 55),    -- York -> Newcastle
(8, 7, 80, 55),
(8, 9, 160, 90),   -- Newcastle -> Edinburgh
(9, 8, 160, 90),
(9, 10, 75, 50),   -- Edinburgh -> Glasgow
(10, 9, 75, 50),
(2, 6, 95, 58),    -- Birmingham -> Sheffield
(6, 2, 95, 58),
(6, 7, 55, 38),    -- Sheffield -> York
(7, 6, 55, 38);
