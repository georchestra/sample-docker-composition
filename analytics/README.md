# About

This directory contains a docker composition to showcase [Analyticsv2](https://github.com/georchestra/improvement-proposals/issues/11).

The architecture slightly differs from Analyticsv1, and is based on the following components:

* The [OpenTelemetry Java agent](http://opentelemetry.io/docs/zero-code/java/agent/) set onto the geOrchestra Gateway container,
* A [vector instance](https://vector.dev/docs/introduction/) which will receive the logs sent by the agent
* A PostGreSQL database along with the [timescaledb extension](https://github.com/timescale/timescaledb),
* A [geOrchestra analytics](https://github.com/georchestra/analytics/) process which run at regular pace to transform logs into timescaledb specific tables,
* A [Superset instance](https://superset.apache.org/), along with its dependencies, to display dashboards fed by the datas from the timescaledb database.

# Notes

* the Analytics container stops itself if it runs correctly, and can be further re-run
* You can point to a specific yaml configuration file in `docker-compose.analytics.yaml`.

If you need to refresh the continuous aggregate by hand, you can connect to the database and run:
```sql
CALL refresh_continuous_aggregate('analytics.ogc_summary_hourly', NULL, NULL);
```
