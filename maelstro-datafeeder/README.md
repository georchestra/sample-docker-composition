# Maelstro + Datafeeder development composition

This directory is the development environment for Maelstro synchronization of
Datafeeder-managed datasets.

## Current status

The composition is self-contained inside this directory. It defines the
Maelstro and geOrchestra services directly and keeps their configuration,
environment files, bootstrap utilities and sample data locally.

The composition includes the Python Datafeeder frontend and backend, configured
with the local task executor and no Airflow services. Application state is
stored in the geOrchestra database, while imported and published datasets are
stored in the dedicated `datadb` PostGIS service.

GeoNetwork UI Datahub and Metadata Editor are also included. Reproducible
end-to-end ingestion and Maelstro synchronization fixtures still need to be
completed.

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
- Datafeeder UI: https://georchestra-127-0-0-1.nip.io:8443/dataset/
- Datafeeder API documentation: https://georchestra-127-0-0-1.nip.io:8443/datafeeder-backend/docs
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

## Datafeeder local executor

The modern integration uses:

- `georchestra/datafeeder-python-frontend:latest`;
- `georchestra/datafeeder-python-backend:latest-local-executor`;
- `TASK_EXECUTOR=LOCAL`;
- no Airflow containers.

The backend uses the main geOrchestra database for integrity links and the
dedicated `datadb` database for staging and final dataset tables. The local
executor runs ingestion jobs inside the backend container and is intended only
for development and demonstrations.

Configuration is stored under `config/georchestra/datafeeder/`. The
`armoires.zip` fixture can be uploaded through the wizard, and the bootstrap
loads `psc.antennes` into `datadb` as a database-import fixture.

This lightweight mode gives processing tasks access to internal services and
mounted files. It must not be treated as a production deployment.
