CREATE KEYSPACE IF NOT EXISTS game_analytics
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS game_analytics.daily_engagement_by_game_day (
  game_id text,
  activity_month text,
  activity_date date,
  platform text,
  build text,
  country_code text,
  dau bigint,
  sessions bigint,
  avg_session_seconds double,
  p95_session_seconds bigint,
  crash_free_session_rate double,
  revenue_usd double,
  PRIMARY KEY ((game_id, activity_month), activity_date, platform, build, country_code)
) WITH CLUSTERING ORDER BY (activity_date DESC, platform ASC, build ASC, country_code ASC);

CREATE TABLE IF NOT EXISTS game_analytics.cohort_retention_by_game_day (
  game_id text,
  cohort_month text,
  cohort_date date,
  platform text,
  build text,
  country_code text,
  cohort_size bigint,
  retained_d1_players bigint,
  retained_d7_players bigint,
  retained_d30_players bigint,
  d1_retention double,
  d7_retention double,
  d30_retention double,
  PRIMARY KEY ((game_id, cohort_month), cohort_date, platform, build, country_code)
) WITH CLUSTERING ORDER BY (cohort_date DESC, platform ASC, build ASC, country_code ASC);

CREATE TABLE IF NOT EXISTS game_analytics.level_progression_by_game_day (
  game_id text,
  event_date date,
  platform text,
  build text,
  country_code text,
  level text,
  players_started bigint,
  players_failed bigint,
  players_completed bigint,
  completion_rate double,
  PRIMARY KEY ((game_id, event_date), platform, build, country_code, level)
);

CREATE TABLE IF NOT EXISTS game_analytics.economy_flows_by_game_day (
  game_id text,
  event_date date,
  platform text,
  build text,
  country_code text,
  virtual_currency text,
  item_type text,
  supporting_players bigint,
  currency_source bigint,
  currency_sink bigint,
  net_currency_flow bigint,
  PRIMARY KEY ((game_id, event_date), platform, build, country_code, virtual_currency, item_type)
);
