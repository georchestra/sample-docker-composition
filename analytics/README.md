Analytics container stops itself if it works correctly.

Needed to add in 100_analytics_init.sql:
```sql
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
CREATE EXTENSION IF NOT EXISTS timescaledb_toolkit;
```

You can point on a specific config yaml file in docker-compose.analytics.yaml.


If you need to refresh the continuous aggregate, you can connect to the database and run:
```sql
CALL refresh_continuous_aggregate('analytics.ogc_summary_hourly', NULL, NULL);
```

Don't forget to add a SUPERSET_ADMIN role.

