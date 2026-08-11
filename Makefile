.PHONY: test build sample validate million services-up services-down clean

test:
	sbt test

build:
	sbt assembly

sample:
	python tools/generate_synthetic_events.py --output sample/events.jsonl --players 500 --days 45 --events 5000

validate:
	python tools/validate_metrics.py --input sample/events.jsonl --report reports/sample_validation.md --json reports/sample_validation.json

million:
	python tools/generate_synthetic_events.py --output /tmp/player-events-1m.jsonl.gz --players 20000 --days 60 --events 1000000
	python tools/validate_metrics.py --input /tmp/player-events-1m.jsonl.gz --report reports/validation_summary.md --json reports/validation_summary.json

services-up:
	docker compose up -d

services-down:
	docker compose down

clean:
	rm -rf target project/target project/project
