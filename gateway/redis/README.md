# About

This composition provides a way to test session storing in Redis for the georchestra
gateway. It takes inspiration from the `gateway/oauth2` sample docker composition
provided earlier, available in the same repository.

Same components as the other sample docker composition are provided, the main
difference being that the gateway is scaled to 2.

The intent is to be able to easily test the purpose of
[PR georchestra/georchestra-gateway#272](https://github.com/georchestra/georchestra-gateway/pull/272).

Once launched, you can access the following url:

http://localhost:8080/

When clicking on login, you will either be able to log in to geOrchestra using
the usual test accounts directly on the Gateway login page, or click onto
"cas-oauth2" link which will get you to CAS, where you could use the
`testoauth2` login, along with the `testoauth2` password.

# Tests

Once a session is opened, you can check both gateway IP addresses using
`docker inspect` then try to push the session cookie onto the `/whoami` endpoint
with a curl command, and check the result on both, to make sure it is coherent,
e.g.:

```
curl -H'Cookie: SESSION=74853d21-9913-4f25-a674-50820e2d4644' http://172.21.0.7:8080/whoami
[... JSON output of a connected user ...]  
curl -H'Cookie: SESSION=74853d21-9913-4f25-a674-50820e2d4644' http://172.21.0.9:8080/whoami   
[... JSON output of a connected user ...]  
```

The test can be performed whether the user connects with the regular form login, or via CAS as
an oauth2 provider.

Basic-authentication can also be tested, but the `/whoami` endpoint is not supposed to save
anything in session, so Spring will likely persist nothing in Redis, keeping the HTTP call
stateless.

# Caveats

CAS can take some time to get into life.
