# About

This composition provides a way to test geOrchestra CAS as an oauth2 identity provider.

It relies on the following components:

* a geOrchestra gateway
* a LDAP on which the gateway is configured against as in a regular geOrchestra setup
* a secondary geOrchestra LDAP which contains only a `testoauth2:testoauth2` account
* a geOrchestra header
* a geOrchestra CAS configured as an oauth2 provider
* a nginx listening on port `8080` which gives access to the gateway and CAS

Once launched, you can access the following url:

http://localhost:8080/

When clicking on login, you will either be able to log in to geOrchestra using
the usual test accounts directly on the Gateway login page, or click onto
"cas-oauth2" link which will get you to CAS, where you could use the
`testoauth2` login, along with the `testoauth2` password.

# Caveats

CAS can take some time to get into life.
