# Maelstro + Datafeeder development composition

This directory is the development environment for Maelstro synchronization of
Datafeeder-managed datasets.

## Current status

The composition is self-contained inside this directory. It defines the
Maelstro and geOrchestra services directly and keeps their configuration,
environment files, bootstrap utilities and sample data locally. It also
includes the classic Datafeeder backend and frontend.

The composition also includes GeoNetwork UI Datahub and Metadata Editor. The
classic Datafeeder services are intended only to validate the local integration while
the supported modern Datafeeder contract is investigated. The environment is
not the final ticket deliverable. The following still need to be added:

- modern Datafeeder backend and frontend with local tasks, without Airflow;
- reproducible Datafeeder fixtures and integrity links;
- health checks and end-to-end smoke tests.

No modern Datafeeder endpoint or task configuration is being inferred from the
classic application.

It does not include or mount files from another repository. Maelstro uses its
published backend and frontend images, so cloning `sample-docker-composition`
is sufficient to start this environment.

## Usage

```bash
make config
make up
make bootstrap  # first initialization only
make smoke
make ps
```

To inspect logs or stop the stack without deleting its volumes:

```bash
make logs
make down
```

## Local access

- Maelstro through Caddy: https://georchestra-127-0-0-1.nip.io:8443/maelstro/
- Maelstro frontend: http://127.0.0.1:8080/maelstro/
- Maelstro backend: http://127.0.0.1:8000/
- Gateway: http://127.0.0.1:8088/
- Datahub: https://georchestra-127-0-0-1.nip.io:8443/datahub/
- Metadata Editor: https://georchestra-127-0-0-1.nip.io:8443/metadata-editor/
- classic Datafeeder UI: https://georchestra-127-0-0-1.nip.io:8443/import/
- classic Datafeeder Swagger: https://georchestra-127-0-0-1.nip.io:8443/datafeeder/swagger-ui/index.html
- dataset PostGIS: `127.0.0.1:5433`
- development users: `testadmin/testadmin`, `tmaelstro/tmaelstro`

Both development users receive the `MAELSTRO` and `DATAFEEDER` roles during
`make bootstrap`.

The local Caddy certificate is issued by its development CA and may need to be
trusted explicitly.

## Known baseline workarounds

- The host ports 443 and 5432 are already used on the tested workstation, so
  `compose.local.yaml` publishes Caddy on 8443 and PostGIS on 5433.
- Maelstro's initialization utilities still address the legacy hostname
  `proxy`; the local composition adds it as a Gateway network alias.
- With SQLAlchemy 2.1, Maelstro currently attempts to load `psycopg` v3 although
  its project dependency declares `psycopg2-binary`. `make up` installs the v3
  driver in the development container as a temporary workaround.

These workarounds should be revisited before the composition is considered
portable and complete.

## Classic Datafeeder compatibility layer

The following services mirror the existing `datafeeder/docker-compose.yaml`
example without duplicating its database, LDAP, GeoServer, GeoNetwork or proxy:

- `datafeeder`: `georchestra/datafeeder:latest`;
- `import`: `georchestra/datafeeder-frontend:latest`;
- `smtp`: `camptocamp/smtp-sink:latest`.

They use this composition's local geOrchestra datadir, Gateway routes and
dedicated PostGIS database. This classic backend does not provide the modern administrative API
or local-task behavior required by the synchronization specification.

The classic Datafeeder-specific configuration is copied into
`config/datafeeder/`; no file is mounted or included from the sibling
`datafeeder/` example. Its sample shapefile is also copied locally to
`fixtures/armoires.zip` for manual import through the wizard.

The Gateway route used by this composition is likewise copied to
`config/gateway/routes.yaml`. This keeps the tested routing behavior local to
this composition instead of modifying or mounting files from the standalone
Datafeeder example.

The classic backend builds an incorrect `http` absolute redirect when opening
`/datafeeder/` through local HTTPS. Use the direct Swagger URL above; the API
itself and authenticated Swagger page respond correctly.
