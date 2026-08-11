from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator

JAR = "/opt/player-analytics/player-telemetry-lakehouse-assembly-0.1.0.jar"

COMMON_ENV = {
    "ICEBERG_CATALOG": "game",
    "ICEBERG_WAREHOUSE": "hdfs://namenode:9000/warehouse",
    "RAW_EVENT_TABLE": "game.raw.game_events",
    "CASSANDRA_HOST": "cassandra",
}

with DAG(
    dag_id="player_analytics_daily",
    start_date=datetime(2026, 1, 1),
    schedule="15 4 * * *",
    catchup=False,
    default_args={
        "owner": "data-engineering",
        "retries": 2,
        "retry_delay": timedelta(minutes=10),
    },
    tags=["gaming", "scala", "spark", "iceberg", "cassandra"],
) as dag:
    validate_events = SparkSubmitOperator(
        task_id="validate_game_events",
        application=JAR,
        java_class="com.nihal.games.jobs.ValidateGameEvents",
        application_args=["--run-date={{ ds }}"],
        env_vars=COMMON_ENV,
        conn_id="spark_default",
    )

    build_metrics = SparkSubmitOperator(
        task_id="build_player_analytics",
        application=JAR,
        java_class="com.nihal.games.jobs.BuildPlayerAnalytics",
        env_vars=COMMON_ENV,
        conn_id="spark_default",
    )

    publish_metrics = SparkSubmitOperator(
        task_id="publish_metrics_to_cassandra",
        application=JAR,
        java_class="com.nihal.games.jobs.PublishToCassandra",
        env_vars=COMMON_ENV,
        conn_id="spark_default",
    )

    maintain_iceberg = SparkSubmitOperator(
        task_id="maintain_iceberg_tables",
        application=JAR,
        java_class="com.nihal.games.jobs.IcebergMaintenance",
        env_vars=COMMON_ENV,
        conn_id="spark_default",
    )

    validate_events >> build_metrics >> publish_metrics >> maintain_iceberg
