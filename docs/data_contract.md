# Gameplay telemetry contract

The project uses a canonical event envelope that can normalize common game analytics exports without tying downstream metrics to one vendor.

## Required envelope

| Field | Type | Purpose |
| --- | --- | --- |
| event_uuid | string | Idempotency and duplicate control |
| event_time | timestamp | UTC client event time |
| game_id | string | Game or title identifier |
| user_id | string | Source identifier, removed after salted hashing |
| session_id | string | Client session identifier |
| category | string | Canonical analytics category |
| platform | string | Client platform |
| build | string | Game release or app version |
| country_code | string | Coarse territory code |
| signup_date | date | Retention cohort date |

## Supported categories

| Category | Important attributes | Developer use |
| --- | --- | --- |
| user | signup_date | Acquisition cohorts and active players |
| session_end | value | Session duration and frequency |
| progression | progression_status, progression_01 | Level start, failure, and completion funnels |
| business | currency, amount, item_id | Purchases and revenue |
| resource | flow_type, virtual_currency, amount | Virtual economy sources and sinks |
| design | event_id, value | Custom interactions and achievements |
| error | severity, message | Crash-free sessions and release quality |
| ad or impression | event_id | Placement and exposure analysis |

## Contract rules

- Required identifiers and timestamps must be present before an event enters the Iceberg table.
- Event timestamps are interpreted in UTC.
- Unsupported categories are rejected.
- Duplicate event UUIDs are removed within the streaming watermark.
- Raw user identifiers are replaced with salted SHA-256 values before analytical storage.
- Additive optional fields preserve schema compatibility for future telemetry sources.
