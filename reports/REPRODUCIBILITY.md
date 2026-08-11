# Reproducing the one million event validation

The checked-in report was reproduced on August 11, 2026 with the commands below.

```bash
python tools/generate_synthetic_events.py \
  --output /tmp/player-events-1m.jsonl.gz \
  --players 20000 \
  --days 60 \
  --events 1000000 \
  --seed 20260811

python tools/validate_metrics.py \
  --input /tmp/player-events-1m.jsonl.gz \
  --report reports/validation_summary.md \
  --json reports/validation_summary.json \
  --minimum-group-size 20
```

The regenerated JSON matched `reports/validation_summary.json` exactly.

Observed local execution time in the validation environment:

- generation: 20.57 seconds
- validation: 7.07 seconds
- compressed event file: 42,888,477 bytes

These timings describe only the deterministic Python validation harness in one local environment. They are not Spark, Kafka, Iceberg, HDFS, Cassandra, or production throughput benchmarks.
