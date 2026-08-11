# Architecture notes

## Data flow

1. A game client or synthetic generator publishes immutable JSON events to Kafka.
2. The Scala streaming job applies the event schema, normalizes the category, removes unsupported records, uses event-time watermarking, deduplicates event identifiers, and pseudonymizes player identifiers.
3. Iceberg stores the replayable analytical history on HDFS.
4. Scala batch jobs build sessions, engagement metrics, retention cohorts, progression funnels, economy flows, and release views by app build.
5. Groups with fewer than 20 supporting players are withheld before publication.
6. Analytical tables remain in Iceberg while Cassandra serves known studio-facing reads.
7. Airflow runs quality checks before analytics and serving-table publication, then performs Iceberg maintenance.

## Why Iceberg and HDFS

HDFS supplies distributed storage. Iceberg adds table-level atomicity, snapshots, schema evolution, partition evolution, and reliable overwrite semantics. The event table partitions by event day and game bucket, while analytical tables partition by month and game bucket.

## Why Cassandra

The serving queries are known in advance: retrieve a title and time window, then break metrics down by platform, app build, territory, level, or economy item. Cassandra is limited to these compact access patterns. Ad hoc exploration remains on Iceberg.

## Reliability model

- Kafka retains source events for replay.
- Every event includes a unique identifier.
- Spark uses event-time watermarking and duplicate removal.
- Iceberg provides atomic commits and snapshots.
- Airflow retries failed jobs and prevents publication when validation fails.
- The validation job checks empty partitions, missing keys, duplicate identifiers, and unsupported categories.
- Iceberg maintenance compacts small files and expires older snapshots.

## Privacy model

- Raw player identifiers are removed after salted SHA-256 pseudonymization.
- The canonical event contract excludes direct identifiers and precise location.
- Aggregate tables retain the supporting-player count.
- Groups below the minimum cohort threshold are not published.
- Breakdown dimensions use coarse territory rather than exact location.
