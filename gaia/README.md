# Sample docker compose for gaia

```
docker compose up -d
cd volumes
sudo chown 999:999 geonetwork_datadir geoserver_* postgresql -R
docker compose down && docker compose up -d
```

open http://127.0.0.1:8080/login

go to http://127.0.0.1:8080/gaia/
