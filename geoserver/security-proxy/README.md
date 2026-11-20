# About

A simple & minimalist docker composition to boot geOrchestra's GeoServer, along with
the security-proxy and CAS.

# Usage

Once the composition is launched, simply open [http://localhost:8080/geoserver/](http://localhost:8080/geoserver/) in your browser,
then log in to CAS with the default geOrchestra accounts (e.g. `testadmin/testadmin`).

# Caveats / Room for improvement

We should provide the same docker composition but which would make use of the [geOrchestra Gateway](https://github.com/georchestra/georchestra-gateway) instead.
