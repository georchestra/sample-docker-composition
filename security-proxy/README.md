# About

This docker composition showcases how we can leverage session sharing in Jetty allowing
to scale and run several instances of the Security-Proxy at the same time.

# How to use

```
$ docker compose up
```

You might need to launch it several times as there are no dependencies declared between
services and some may fail if e.g. the database is not ready yet.

Once everything is up and running, you should be able to connect:

```
http://localhost:8080/datahub/?login
```

the `datahub` is not a real datahub (from the GN-ui project), but is a simple webapp which
will print out the HTTP headers being received from the request.

# Check

Check connection / session state on each proxy instances:

```
for i in $(seq 1 5) ; do
  echo gsren-597-proxy-$i ;
  docker exec -it security-proxy-proxy-$i curl http://localhost:8080/whoami -H'Cookie: JSESSIONID=node0...';
  printf "\n\n";
done
```
