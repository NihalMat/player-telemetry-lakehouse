#!/usr/bin/env python3
"""Validate event quality and calculate reference game analytics metrics."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from datetime import date, datetime
from pathlib import Path
from typing import IO

SUPPORTED = {
    "user",
    "session_end",
    "business",
    "progression",
    "resource",
    "design",
    "error",
    "ad",
    "impression",
}


def open_input(path: Path) -> IO[str]:
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def percent(numerator: int, denominator: int) -> float:
    return round(100.0 * numerator / denominator, 2) if denominator else 0.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--minimum-group-size", type=int, default=20)
    args = parser.parse_args()

    event_count = 0
    invalid_count = 0
    duplicate_count = 0
    event_ids: set[str] = set()
    categories: Counter[str] = Counter()
    players: set[str] = set()
    sessions: set[str] = set()
    error_sessions: set[str] = set()
    active_days: dict[str, set[date]] = defaultdict(set)
    signup_dates: dict[str, date] = {}
    level_started: Counter[str] = Counter()
    level_completed: Counter[str] = Counter()
    revenue_cents = 0
    privacy_groups: Counter[tuple[str, date]] = Counter()
    sample_hashes: list[str] = []

    with open_input(args.input) as handle:
        for line in handle:
            event_count += 1
            try:
                event = json.loads(line)
                category = str(event.get("category", "")).lower()
                event_uuid = str(event.get("event_uuid", ""))
                player = str(event.get("user_id", ""))
                session = str(event.get("session_id", ""))
                event_day = datetime.fromisoformat(event["event_time"].replace("Z", "+00:00")).date()
                signup_day = date.fromisoformat(event["signup_date"])
            except (KeyError, TypeError, ValueError, json.JSONDecodeError):
                invalid_count += 1
                continue

            if category not in SUPPORTED or not event_uuid or not player or not session:
                invalid_count += 1
                continue

            if event_uuid in event_ids:
                duplicate_count += 1
            else:
                event_ids.add(event_uuid)

            categories[category] += 1
            players.add(player)
            sessions.add(session)
            active_days[player].add(event_day)
            signup_dates[player] = min(signup_dates.get(player, signup_day), signup_day)
            privacy_groups[(str(event.get("country_code", "unknown")), event_day)] += 1

            if len(sample_hashes) < 5:
                sample_hashes.append(hashlib.sha256(("portfolio-salt" + player).encode()).hexdigest())

            if category == "error":
                error_sessions.add(session)
            elif category == "business":
                revenue_cents += int(event.get("amount") or 0)
            elif category == "progression":
                level = str(event.get("progression_01") or "unknown")
                status = str(event.get("progression_status") or "")
                if status == "start":
                    level_started[level] += 1
                elif status == "complete":
                    level_completed[level] += 1

    retained = {1: 0, 7: 0, 30: 0}
    eligible = {1: 0, 7: 0, 30: 0}
    max_event_date = max((day for days in active_days.values() for day in days), default=date.min)
    for player, signup_day in signup_dates.items():
        offsets = {(day - signup_day).days for day in active_days[player]}
        for day_number in retained:
            if (max_event_date - signup_day).days >= day_number:
                eligible[day_number] += 1
                if day_number in offsets:
                    retained[day_number] += 1

    levels = sorted(level_started)
    hardest_levels = sorted(
        (
            {
                "level": level,
                "starts": level_started[level],
                "completions": level_completed[level],
                "completion_rate": percent(level_completed[level], level_started[level]),
            }
            for level in levels
            if level_started[level] >= args.minimum_group_size
        ),
        key=lambda row: row["completion_rate"],
    )[:5]

    suppressed_groups = sum(1 for size in privacy_groups.values() if size < args.minimum_group_size)
    metrics = {
        "events": event_count,
        "valid_events": event_count - invalid_count,
        "invalid_events": invalid_count,
        "duplicate_event_ids": duplicate_count,
        "players": len(players),
        "sessions": len(sessions),
        "category_counts": dict(sorted(categories.items())),
        "d1_retention_percent": percent(retained[1], eligible[1]),
        "d7_retention_percent": percent(retained[7], eligible[7]),
        "d30_retention_percent": percent(retained[30], eligible[30]),
        "error_session_rate_percent": percent(len(error_sessions), len(sessions)),
        "revenue_usd": round(revenue_cents / 100.0, 2),
        "privacy_groups_suppressed": suppressed_groups,
        "privacy_threshold": args.minimum_group_size,
        "hardest_levels": hardest_levels,
        "sample_hashed_player_keys": sample_hashes,
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Synthetic Validation Run",
        "",
        "This report was generated from synthetic gameplay events. It does not contain Apple, employer, customer, or personal data.",
        "",
        "## Data quality",
        "",
        f"* Events scanned: **{event_count:,}**",
        f"* Valid events: **{metrics['valid_events']:,}**",
        f"* Invalid events: **{invalid_count:,}**",
        f"* Duplicate event identifiers: **{duplicate_count:,}**",
        f"* Players: **{len(players):,}**",
        f"* Sessions: **{len(sessions):,}**",
        "",
        "## Reference metrics",
        "",
        f"* D1 retention: **{metrics['d1_retention_percent']:.2f}%**",
        f"* D7 retention: **{metrics['d7_retention_percent']:.2f}%**",
        f"* D30 retention: **{metrics['d30_retention_percent']:.2f}%**",
        f"* Sessions with an error event: **{metrics['error_session_rate_percent']:.2f}%**",
        f"* Synthetic in app purchase revenue: **${metrics['revenue_usd']:,.2f}**",
        "",
        "## Privacy controls exercised",
        "",
        "* Raw player identifiers are transformed with SHA-256 and a salt before analytical storage.",
        f"* Aggregate country and day groups below **{args.minimum_group_size}** records are marked for suppression.",
        f"* Groups marked for suppression in this run: **{suppressed_groups:,}**",
        "",
        "## Lowest completion levels with sufficient volume",
        "",
        "| Level | Starts | Completions | Completion rate |",
        "|---|---:|---:|---:|",
    ]
    for row in hardest_levels:
        lines.append(
            f"| {row['level']} | {row['starts']:,} | {row['completions']:,} | {row['completion_rate']:.2f}% |"
        )
    lines.extend(["", "## Event category counts", ""])
    for category, count in metrics["category_counts"].items():
        lines.append(f"* {category}: {count:,}")
    lines.append("")
    args.report.write_text("\n".join(lines), encoding="utf-8")

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
