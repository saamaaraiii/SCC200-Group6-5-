# Lancashire Places Scraper

Scrapes restaurants, cafes, pubs, attractions and hotels across Lancashire using the **OpenStreetMap Overpass API** — completely free, no API key needed.

Supports **incremental updates** — re-run anytime to pick up new places and changed details without duplicating records.

## What it scrapes

| Category    | Count (latest run) |
|-------------|-------------------|
| Restaurants | 1,171             |
| Cafes       | 468               |
| Pubs        | 938               |
| Attractions | 383               |
| Hotels      | 176               |

## Data fields

Each record includes:
- Name, category, type
- Full address + postcode + town
- GPS coordinates (latitude/longitude)
- Phone number & website
- Opening hours
- Star rating (hotels)
- Cuisine type (restaurants)
- `first_seen` — when the place was first scraped
- `last_updated` — when details last changed
- `last_checked` — last time it was verified unchanged

## Usage

```bash
pip install requests

# First run — scrape everything
python lancashire_scraper.py

# Re-run to update (adds new, updates changed, keeps everything else)
python lancashire_scraper.py

# Specific category
python lancashire_scraper.py --type restaurants
python lancashire_scraper.py --type attractions
python lancashire_scraper.py --type cafes
python lancashire_scraper.py --type pubs
python lancashire_scraper.py --type hotels

# Start fresh, ignore existing data
python lancashire_scraper.py --fresh

# Custom output directory
python lancashire_scraper.py --output ./my_output/
```

## Output

Fixed filenames (e.g. `lancashire_restaurants.json`) — updated in place each run so you always have one clean file per category, not a pile of timestamped duplicates.

## Scheduling (run automatically)

Add to crontab to update weekly:
```bash
crontab -e
# Add:
0 3 * * 1 cd /path/to/scraper && python lancashire_scraper.py >> scraper.log 2>&1
```
