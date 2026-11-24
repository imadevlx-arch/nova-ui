#!/usr/bin/env python3
"""Construit events.json : matchs et soirées UFC d'hier à J+2, pour toutes les compétitions
de events-config.json. Tourne dans GitHub Actions (une seule source pour toutes les apps,
au lieu que chaque appareil martèle l'API et se fasse bloquer par Cloudflare)."""
import json, sys, time, urllib.request, datetime as dt

CONFIG = "player/src/main/assets/events-config.json"
API = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d={d}&l={id}"
DELAY = 2.5  # < 30 requêtes / minute (limite de la clé gratuite)

def get(url, tries=3):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "NovaPlayer-feed/1.0"})
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            print(f"  essai {i+1} échoué : {e}", file=sys.stderr)
            time.sleep(20 * (i + 1))
    return None

def ts(e):
    s = (e.get("strTimestamp") or "")[:19]
    if len(s) == 19:
        return int(dt.datetime.strptime(s, "%Y-%m-%dT%H:%M:%S").replace(tzinfo=dt.timezone.utc).timestamp() * 1000)
    d, t = e.get("dateEvent"), (e.get("strTime") or "")[:8]
    if d and len(t) >= 5:
        t = t if len(t) == 8 else t[:5] + ":00"
        return int(dt.datetime.strptime(f"{d}T{t}", "%Y-%m-%dT%H:%M:%S").replace(tzinfo=dt.timezone.utc).timestamp() * 1000)
    return None

def main():
    cfg = json.load(open(CONFIG, encoding="utf-8"))
    today = dt.datetime.now(dt.timezone.utc).date()
    days = [(today + dt.timedelta(days=k)).isoformat() for k in (-1, 0, 1, 2)]
    events, failures = [], 0
    for comp in cfg["competitions"]:
        for d in days:
            data = get(API.format(d=d, id=comp["id"]))
            time.sleep(DELAY)
            if data is None:
                failures += 1
                continue
            for e in data.get("events") or []:
                start = ts(e)
                if start is None:
                    continue
                home, away = e.get("strHomeTeam"), e.get("strAwayTeam")
                events.append({
                    "id": e.get("idEvent"),
                    "competition": comp["name"],
                    "title": f"{home} – {away}" if comp.get("sport") == "foot" and home and away else e.get("strEvent"),
                    "home": home, "away": away,
                    "start": start,
                    "end": start + int(comp.get("hours", 2.2) * 3600_000),
                    "homeBadge": e.get("strHomeTeamBadge"), "awayBadge": e.get("strAwayTeamBadge"),
                })
    seen, unique = set(), []
    for e in sorted(events, key=lambda x: x["start"]):
        if e["id"] not in seen:
            seen.add(e["id"]); unique.append(e)
    out = {"generatedAt": int(time.time() * 1000), "failures": failures, "events": unique}
    json.dump(out, open("events.json", "w", encoding="utf-8"), ensure_ascii=False)
    print(f"{len(unique)} événements, {failures} requêtes en échec")
    # Ne pas écraser un bon flux avec un flux vide si l'API était bloquée
    if not unique and failures > len(cfg["competitions"]):
        sys.exit(1)

if __name__ == "__main__":
    main()
