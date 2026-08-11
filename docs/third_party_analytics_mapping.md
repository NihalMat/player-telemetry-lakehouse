# Third-party game analytics concept mapping

This document demonstrates familiarity with common game analytics event models. It is not a claim of a deployed vendor integration.

## Canonical event mapping

| Project event | GameAnalytics | PlayFab | Unity Analytics | Product question |
| --- | --- | --- | --- | --- |
| user, session_end | user and session_end categories | user-generated telemetry | standard or custom session events | How often and how long do players engage? |
| business | business event | economy or commerce telemetry | transaction event | What is revenue per active player? |
| progression | start, fail, and complete progression events | player statistics and telemetry | custom progression events | Where do players stop progressing? |
| resource | source and sink resource events | economy telemetry | custom economy events | Is the virtual economy balanced? |
| design | custom design event hierarchy | custom telemetry event | custom event | Which features and interactions drive engagement? |
| error | severity and error message | performance or crash telemetry | diagnostics or custom error event | Which app builds reduce crash-free sessions? |
| ad, impression | ad and impression events | advertising telemetry | ad impression event | Which placements create value without harming engagement? |

## Apple-facing metrics

The project also produces metrics that align conceptually with App Store Connect analytics and peer benchmarks, including sessions, D1 and D7 retention, crash rate, and proceeds per paying or active user. The implementation uses synthetic title telemetry and does not ingest Apple analytics exports.

## Normalization approach

A source adapter translates vendor-specific events into a stable canonical schema before Kafka ingestion. Downstream Scala, Spark, Iceberg, and Cassandra jobs remain independent of the telemetry vendor. This reduces coupling when a studio adds or replaces an SDK.

## References

- GameAnalytics event types: https://docs.gameanalytics.com/event-tracking-and-integrations/sdks-and-collection-api/api/event-types/
- GameAnalytics progression events: https://docs.gameanalytics.com/events-metrics-and-filtering/event-types/progression-events/
- PlayFab Telemetry overview: https://learn.microsoft.com/en-us/xbox/playfab/data-analytics/ingest-data/telemetry-overview
- PlayFab statistics with telemetry: https://learn.microsoft.com/en-us/xbox/playfab/player-progression/statistics/statistics-with-playstream-and-telemetry
- Unity Analytics Event Manager: https://docs.unity.com/en-us/analytics/events/event-manager
- Apple App Store Connect metric definitions: https://developer.apple.com/help/app-store-connect-analytics/reference/metrics-definitions
- Apple peer group benchmarks: https://developer.apple.com/help/app-store-connect-analytics/benchmarks/peer-group-benchmarks/
