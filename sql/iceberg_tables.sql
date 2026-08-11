CREATE NAMESPACE IF NOT EXISTS game.raw;
CREATE NAMESPACE IF NOT EXISTS game.analytics;

CREATE TABLE IF NOT EXISTS game.raw.game_events (
  event_uuid STRING,
  event_time TIMESTAMP,
  game_id STRING,
  session_id STRING,
  category STRING,
  event_id STRING,
  progression_status STRING,
  progression_01 STRING,
  progression_02 STRING,
  progression_03 STRING,
  currency STRING,
  amount BIGINT,
  item_type STRING,
  item_id STRING,
  flow_type STRING,
  virtual_currency STRING,
  severity STRING,
  message STRING,
  value DOUBLE,
  platform STRING,
  os_version STRING,
  build STRING,
  country_code STRING,
  signup_date DATE,
  event_date DATE,
  ingested_at TIMESTAMP,
  player_key STRING
) USING iceberg
PARTITIONED BY (days(event_time), bucket(16, game_id));

CREATE TABLE IF NOT EXISTS game.analytics.player_sessions (
  game_id STRING,
  player_key STRING,
  session_id STRING,
  event_date DATE,
  platform STRING,
  build STRING,
  country_code STRING,
  session_started_at TIMESTAMP,
  session_ended_at TIMESTAMP,
  event_count BIGINT,
  has_error INT,
  session_seconds BIGINT
) USING iceberg
PARTITIONED BY (months(event_date), bucket(16, game_id));

CREATE TABLE IF NOT EXISTS game.analytics.daily_engagement (
  game_id STRING,
  event_date DATE,
  platform STRING,
  build STRING,
  country_code STRING,
  dau BIGINT,
  sessions BIGINT,
  avg_session_seconds DOUBLE,
  p95_session_seconds BIGINT,
  crash_free_session_rate DOUBLE,
  revenue_usd DOUBLE
) USING iceberg
PARTITIONED BY (months(event_date), bucket(16, game_id));

CREATE TABLE IF NOT EXISTS game.analytics.cohort_retention (
  game_id STRING,
  cohort_date DATE,
  platform STRING,
  build STRING,
  country_code STRING,
  cohort_size BIGINT,
  retained_d1_players BIGINT,
  retained_d7_players BIGINT,
  retained_d30_players BIGINT,
  d1_retention DOUBLE,
  d7_retention DOUBLE,
  d30_retention DOUBLE
) USING iceberg
PARTITIONED BY (months(cohort_date), bucket(16, game_id));

CREATE TABLE IF NOT EXISTS game.analytics.level_progression (
  game_id STRING,
  event_date DATE,
  platform STRING,
  build STRING,
  country_code STRING,
  level STRING,
  players_started BIGINT,
  players_failed BIGINT,
  players_completed BIGINT,
  completion_rate DOUBLE
) USING iceberg
PARTITIONED BY (months(event_date), bucket(16, game_id));

CREATE TABLE IF NOT EXISTS game.analytics.economy_flows (
  game_id STRING,
  event_date DATE,
  platform STRING,
  build STRING,
  country_code STRING,
  virtual_currency STRING,
  item_type STRING,
  supporting_players BIGINT,
  currency_source BIGINT,
  currency_sink BIGINT,
  net_currency_flow BIGINT
) USING iceberg
PARTITIONED BY (months(event_date), bucket(16, game_id));
