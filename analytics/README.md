Analytics container stops itself if it works correctly.

Needed to add in 100_analytics_init.sql:
```sql
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
CREATE EXTENSION IF NOT EXISTS timescaledb_toolkit;
```

You can point on a specific config yaml file in docker-compose.analytics.yaml.