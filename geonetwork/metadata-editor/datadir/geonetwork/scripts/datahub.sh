#!/bin/bash

rm -f /var/lib/jetty/webapps/geonetwork/WEB-INF/lib/gn-datahub-integration-4.4.8-georchestra.jar
cp /etc/georchestra/geonetwork/scripts/gn-datahub-integration-4.4.9-SNAPSHOT.jar /var/lib/jetty/webapps/geonetwork/WEB-INF/lib/gn-datahub-integration-4.4.8-georchestra.jar