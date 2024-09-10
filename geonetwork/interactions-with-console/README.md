# About

this composition aims to provide a way to reproduce a conception "bug" with
the geOrchestra integration (as of september 2024):

When a user is connected, but the console is unavailable, then the catalogue
is unusable, because GeoNetwork is unable to perform a hit to the console and get
extra informations from the console webservices.

Normally, every needed informations are carried over the HTTP headers, so
the console should not be a strong dependency for connected users.

# Step to reproduce

1. Launch the docker composition
2. make sure the console is not available (`docker compose stop console`)
3. perform the following curl request:

```
curl 'http://localhost:8080/geonetwork/srv/eng/catalog.search' \
  -H 'sec-proxy: true' \
  -H 'sec-roles: ROLE_USER;ROLE_ADMINISTRATOR;ROLE_GN_ADMIN' \
  -H 'sec-username: testadmin'
```

# Result

An error page with a stacktrace is displayed:

```
   [...]
org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://console:8080/console/internal/users/username/testadmin": console; nested exception is java.net.UnknownHostException: console
	at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:791)
	at org.springframework.web.client.RestTemplate.execute(RestTemplate.java:717)
	at org.springframework.web.client.RestTemplate.getForEntity(RestTemplate.java:367)
	at org.georchestra.security.client.console.RestClient.get(RestClient.java:40)
	at org.georchestra.security.client.console.GeorchestraConsoleUsersApiImpl.findByUsername(GeorchestraConsoleUsersApiImpl.java:57)
	at org.georchestra.geonetwork.security.integration.GeorchestraAccountsRepository.findUserByUsername(GeorchestraAccountsRepository.java:58)
	at org.geonetwork.security.external.integration.UserSynchronizer.findCanonicalUserByUsername(UserSynchronizer.java:83)
	at org.geonetwork.security.external.integration.UserSynchronizer$$FastClassBySpringCGLIB$$88a22663.invoke(<generated>)
	at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)
	at org.springframework.aop.framework.CglibAopProxy.invokeMethod(CglibAopProxy.java:386)
	at org.springframework.aop.framework.CglibAopProxy.access$000(CglibAopProxy.java:85)
	at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:703)
  [...]
```

# Expected

No adherence to the console, and the actual `catalog.search` page is displayed.

# Investigations

Checking the code, and esp. the following class:
https://github.com/georchestra/geonetwork/blob/georchestra-gn4.4.x/georchestra-integration/georchestra-authnz/src/main/java/org/georchestra/geonetwork/security/authentication/GeorchestraPreAuthenticationFilter.java#L86

It seems that the lookup via the console webservices is only necessary when the regular
versions of the http headers are sent, not the json version.

```
curl 'http://localhost:8080/geonetwork/srv/eng/catalog.search' \
  -H 'sec-proxy: true' \
  -H "sec-organization: {base64}$(base64 -w0 organization.json)" \
  -H "sec-user: {base64}$(base64 -w0 user.json)"
```

# conclusions

The console can be unavailable but the catalogue will still work
as logged-in users under these conditions:

1. GeoNetwork is up-to-date in regards to the synchronization
2. The base64-json versions of the headers are sent to GN


