# About

This composition provides an example of how to secure an application behind the gateway.

It relies on the following components:

* a geOrchestra gateway
* a LDAP on which the gateway is configured against as in a regular geOrchestra setup
* a Python Flask application, which is the application to be secured

Once launched, you can access the following url:

- Insecure route : http://localhost:8080/flask/ 
- Secured route : http://localhost:8080/flask/admin

User : testadmin / Password : testadmin

Example of admin response
```json
{
  "Sec-Username": "testadmin",
  "Sec-Org": "PSC",
  "Sec-Email": "psc+testadmin@georchestra.org",
  "Sec-Firstname": "Test",
  "Sec-Lastname": "ADMIN",
  "Sec-Roles": "ROLE_SUPERUSER;ROLE_MAPSTORE_ADMIN;ROLE_USER;ROLE_ADMINISTRATOR;ROLE_GN_ADMIN;ROLE_EMAILPROXY;ROLE_IMPORT",
  "Sec-External-Authentication": "false",
  "Sec-Proxy": "true",
  "Sec-Orgname": "Project Steering Committee",
  "Sec-User": "{base64}eyJ1c2VybmFtZSI6InRlc3RhZG1pbiIsInJvbGVzIjpbIlJPTEVfU1VQRVJVU0VSIiwiUk9MRV9NQVBTVE9SRV9BRE1JTiIsIlJPTEVfVVNFUiIsIlJPTEVfQURNSU5JU1RSQVRPUiIsIlJPTEVfR05fQURNSU4iLCJST0xFX0VNQUlMUFJPWFkiLCJST0xFX0lNUE9SVCJdLCJvcmdhbml6YXRpb24iOiJQU0MiLCJpZCI6IjBjNmJiNTU2LTRlZTgtNDZmMi04OTJkLTYxMTZlMjYyYjQ4OSIsImxhc3RVcGRhdGVkIjoiOTk0MmI1MzQ4YzgzZDg2MDdkZTg1N2Y3ZDc1ZWY3ZGZjYmZmNjk5ZDc5MjlkMzg2NzRlYjkwYTBiZmMxMGNlNyIsImZpcnN0TmFtZSI6IlRlc3QiLCJsYXN0TmFtZSI6IkFETUlOIiwiZW1haWwiOiJwc2MrdGVzdGFkbWluQGdlb3JjaGVzdHJhLm9yZyIsIm5vdGVzIjoiSW50ZXJuYWwgQ1JNIG5vdGVzIG9uIHRlc3RhZG1pbiIsImxkYXBXYXJuIjpmYWxzZSwiaXNFeHRlcm5hbEF1dGgiOmZhbHNlfQ==",
  "Sec-Organization": "{base64}eyJpZCI6ImJkZGY0NzRkLTEyNWQtNGIxOC05MmJkLWJkOGViYjY2OTlhOSIsInNob3J0TmFtZSI6IlBTQyIsIm5hbWUiOiJQcm9qZWN0IFN0ZWVyaW5nIENvbW1pdHRlZSIsImxpbmthZ2UiOiJodHRwczovL3d3dy5nZW9yY2hlc3RyYS5vcmcvIiwicG9zdGFsQWRkcmVzcyI6IjEyNyBydWUgZ2VvcmNoZXN0cmEsIDczNTkwIENoYW1ibGlsbGUiLCJjYXRlZ29yeSI6IkFzc29jaWF0aW9uIiwiZGVzY3JpcHRpb24iOiJBc3NvY2lhdGlvbiBQU0MgZ2VPcmNoZXN0cmEiLCJub3RlcyI6IkludGVybmFsIENSTSBub3RlcyBvbiBQU0MiLCJtYWlsIjoicHNjQGdlb3JjaGVzdHJhLm9yZyIsImxhc3RVcGRhdGVkIjoiYjdhMjk1ZTk2NTRjYzRkM2JkZDgyMDFmZWIxYWM3NGRkMjNjYzllYTJjOTk0N2YyYjA1YjQxMThhYzNiYmU0MSIsIm1lbWJlcnMiOlsidGVzdGFkbWluIiwidGVzdHVzZXIiXX0="
}
```

It's displaying sec-headers, which are set by the gateway.
