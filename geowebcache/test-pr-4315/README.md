# About

The purpose of this composition was to runtime-test
[PR#4315](https://github.com/georchestra/georchestra/pull/4315).

A simple nginx configuration is provided if needed
to automatically fake the HTTP headers being sent to geowebcache.

# How to use

## http

Nginx provides 2 routes:
* http://localhost:8080/geoserver/: a regular geoserver, with `admin:geoserver` as admin account.
* http://localhost:8080/geowebcache/: the GWC being tested

You can tweak the geoserver configuration, and modify the geowebcache.xml configuration
under the resources/ sudirectory, to enable cache of protected layers, and see
if the environment substitution (which is the purpose of the above PR) is
working as expected.

## JDWP

GWC is also listening on localhost:5005 if needed to connect a Java debugger via JDWP.
