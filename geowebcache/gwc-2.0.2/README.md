# About

Testing GWC, especially the upgrade to 2.0.2.

# How to use

## http

Nginx provides 2 routes:
* http://localhost:8080/geoserver/: a regular geoserver, with `admin:geoserver` as admin account.
* http://localhost:8080/geowebcache/: the GWC being tested

## JDWP

GWC is also listening on localhost:5005 if needed to connect a Java debugger via JDWP.
