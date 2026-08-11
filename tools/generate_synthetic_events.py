#!/usr/bin/env python3
"""Generate deterministic synthetic game telemetry in a GameAnalytics-style schema."""

from __future__ import annotations

import argparse
import gzip
import json
import random
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import IO, Any

CATEGORIES = (
    "user",
    "session_end",
    "business",
    "progression",
    "resource",
    "design",
    "error",
    "ad",
    "impression",
)


def open_output(path: Path) -> IO[str]:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".gz":
        return gzip.open(path, "wt", encoding="utf-8")
    return path.open("w", encoding="utf-8")


def iso_time(day: date, seconds: int) -> str:
    value = datetime.combine(day, datetime.min.time(), tzinfo=timezone.utc) + timedelta(seconds=seconds)
    return value.isoformat().replace("+00:00", "Z")


def base_event(
    rng: random.Random,
    player: dict[str, Any],
    session_id: str,
    event_time: str,
    category: str,
) -> dict[str, Any]:
    return {
        "event_uuid": str(uuid.UUID(int=rng.getrandbits(128))),
        "event_time": event_time,
        "game_id": "nebula-racers",
        "user_id": player["user_id"],
        "session_id": session_id,
        "category": category,
        "event_id": None,
        "progression_status": None,
        "progression_01": None,
        "progression_02": None,
        "progression_03": None,
        "currency": None,
        "amount": None,
        "item_type": None,
        "item_id": None,
        "flow_type": None,
        "virtual_currency": None,
        "severity": None,
        "message": None,
        "value": None,
        "platform": player["platform"],
        "os_version": player["os_version"],
        "build": player["build"],
        "country_code": player["country_code"],
        "signup_date": player["signup_date"].isoformat(),
    }


def choose_day_offset(rng: random.Random, max_day: int) -> int:
    bucket = rng.random()
    if bucket < 0.32:
        offset = 0
    elif bucket < 0.44:
        offset = 1
    elif bucket < 0.64:
        offset = rng.randint(2, 6)
    elif bucket < 0.72:
        offset = 7
    elif bucket < 0.91:
        offset = rng.randint(8, min(29, max_day))
    elif bucket < 0.96 and max_day >= 30:
        offset = 30
    else:
        offset = rng.randint(min(31, max_day), max_day)
    return min(offset, max_day)


def emit_session(rng: random.Random, player: dict[str, Any], active_day: date) -> list[dict[str, Any]]:
    session_id = f"s-{rng.getrandbits(64):016x}"
    start_second = rng.randint(0, 82_000)
    duration = max(45, int(rng.lognormvariate(6.0, 0.65)))
    events: list[dict[str, Any]] = []

    events.append(base_event(rng, player, session_id, iso_time(active_day, start_second), "user"))

    if (active_day - player["signup_date"]).days <= 2 and rng.random() < 0.65:
        tutorial = base_event(rng, player, session_id, iso_time(active_day, start_second + 5), "design")
        tutorial["event_id"] = f"tutorial:step:{rng.randint(1, 5)}"
        events.append(tutorial)

    max_level = max(1, min(40, 1 + int((active_day - player["signup_date"]).days * 0.8) + rng.randint(0, 4)))
    level = rng.randint(max(1, max_level - 4), max_level)

    progression_start = base_event(rng, player, session_id, iso_time(active_day, start_second + 10), "progression")
    progression_start.update(
        {
            "progression_status": "start",
            "progression_01": f"level_{level:02d}",
            "progression_02": "race",
            "progression_03": rng.choice(["normal", "hard"]),
        }
    )
    events.append(progression_start)

    completion_probability = max(0.34, 0.92 - level * 0.012)
    status = "complete" if rng.random() < completion_probability else "fail"
    progression_end = base_event(
        rng,
        player,
        session_id,
        iso_time(active_day, min(86_399, start_second + duration - 10)),
        "progression",
    )
    progression_end.update(
        {
            "progression_status": status,
            "progression_01": f"level_{level:02d}",
            "progression_02": "race",
            "progression_03": progression_start["progression_03"],
            "value": float(rng.randint(500, 12_000)),
        }
    )
    events.append(progression_end)

    resource = base_event(rng, player, session_id, iso_time(active_day, start_second + 20), "resource")
    resource.update(
        {
            "flow_type": "source" if status == "complete" else "sink",
            "virtual_currency": "credits",
            "amount": rng.randint(25, 300),
            "item_type": "race_reward" if status == "complete" else "retry",
            "item_id": f"level_{level:02d}",
        }
    )
    events.append(resource)

    if rng.random() < 0.018:
        purchase = base_event(rng, player, session_id, iso_time(active_day, start_second + 30), "business")
        purchase.update(
            {
                "currency": "USD",
                "amount": rng.choice([99, 199, 499, 999]),
                "item_type": "upgrade_pack",
                "item_id": rng.choice(["starter", "speed", "cosmetic"]),
            }
        )
        events.append(purchase)

    if rng.random() < 0.012:
        error = base_event(rng, player, session_id, iso_time(active_day, start_second + 35), "error")
        error.update(
            {
                "severity": rng.choices(["warning", "error", "critical"], weights=[0.55, 0.4, 0.05])[0],
                "message": rng.choice(["asset_load_timeout", "network_retry_exhausted", "render_state_failure"]),
            }
        )
        events.append(error)

    if rng.random() < 0.35:
        impression = base_event(rng, player, session_id, iso_time(active_day, start_second + 40), "impression")
        impression["event_id"] = rng.choice(["home:featured_game", "store:upgrade_pack", "race:rewarded_ad"])
        events.append(impression)

    session_end = base_event(
        rng,
        player,
        session_id,
        iso_time(active_day, min(86_399, start_second + duration)),
        "session_end",
    )
    session_end["value"] = float(duration)
    events.append(session_end)
    return events


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--players", type=int, default=20_000)
    parser.add_argument("--days", type=int, default=60)
    parser.add_argument("--events", type=int, default=1_000_000)
    parser.add_argument("--seed", type=int, default=20260811)
    args = parser.parse_args()

    if args.players <= 0 or args.days <= 1 or args.events <= 0:
        raise ValueError("players, days, and events must be positive")

    rng = random.Random(args.seed)
    start_date = date(2026, 1, 1)
    platforms = [
        ("iOS", "iOS 19.0"),
        ("iPadOS", "iPadOS 19.0"),
        ("macOS", "macOS 16.0"),
        ("tvOS", "tvOS 19.0"),
    ]
    countries = ["US", "CA", "GB", "DE", "FR", "JP", "IN", "AU"]
    builds = ["1.0.0", "1.1.0", "1.2.0"]

    players = []
    for index in range(args.players):
        platform, os_version = rng.choices(platforms, weights=[0.58, 0.18, 0.16, 0.08])[0]
        signup_offset = rng.randint(0, min(20, args.days - 1))
        players.append(
            {
                "user_id": f"player-{index:07d}",
                "platform": platform,
                "os_version": os_version,
                "build": rng.choice(builds),
                "country_code": rng.choice(countries),
                "signup_date": start_date + timedelta(days=signup_offset),
            }
        )

    written = 0
    with open_output(args.output) as handle:
        while written < args.events:
            player = rng.choice(players)
            max_day = max(1, args.days - 1 - (player["signup_date"] - start_date).days)
            active_day = player["signup_date"] + timedelta(days=choose_day_offset(rng, max_day))
            for event in emit_session(rng, player, active_day):
                if written >= args.events:
                    break
                handle.write(json.dumps(event, separators=(",", ":")) + "\n")
                written += 1

    print(f"wrote {written:,} events to {args.output}")


if __name__ == "__main__":
    main()
