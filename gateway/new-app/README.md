# About

This composition provides an example of how to secure an application behind the gateway.

It relies on the following components:

* a geOrchestra gateway
* a LDAP on which the gateway is configured against as in a regular geOrchestra setup
* a Python Flask application, which is the application to be secured

Once launched, you can access the following url:

- Insecure route : http://localhost:8080/flask/ 
- Secured route : http://localhost:8080/flask/admin

It's displaying sec-headers, which are set by the gateway.
