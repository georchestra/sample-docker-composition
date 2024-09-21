# About

This docker composition provides a way to test the password rotation policy
from the LDAP, and how it affects the templates on CAS when one to try to log
in with an expired account, or with an account on which the password is
about to expire.

# Ports

* CAS is available on port 8080
* LDAP is available on port 3389

# scenario

a georchestra/ldap is launched along with the  environment variable
`SLAPD_PASSWORD_MGT_POLICY` set to `rotation`.

When the LDAP server bootstraps, a `docker-entrypoint.d` init script injects a
new password policy which makes the passwords expiring after 2 seconds, and
affects this new policy to the `testuser` account. Another policy sets a
policy to 2 days and affects it to the `testeditor` user.

2 seconds is probably enough to have the passwords for the user expired before
being able to log in as `testuser`.

2 days should leave the time to get a "Warning: password is about to expire" for
user `testeditor`.

After having launched the composition, if you visit
`http://localhost:8080/cas/login`, and try to connect as `testuser/testuser`,
you should see a message telling you that your password expired, along with a
link to the console (not provided by the composition) so that you could reset
it.

Connecting as `testeditor` in the 2 days timespan after having launched the
docker composition should give you the "password is about to expire" message.

# More technical details

The password policy management in OpenLDAP is performed via an overlay, see
[official documentation](https://www.openldap.org/devel/admin/overlays.html),
chapter 12.10.

Once the overlay is loaded into `slapd`, you can add the `pwdPolicySubentry`
operational attribute to your users, and the attribute has to point onto an
existing policy, loaded under `ou=pwpolicy,dc=georchestra,dc=org`.

geOrchestra provides 2 password policies by default:

* a `cn=pwd-no-expire`, mainly reserved to bot accounts, which does not
  define an expiration date for passwords (`pwdMaxAge: 0`).
* a `cn=default`, which makes the user passwords expiring after 6 months.

As the `pwdPolicySubentry` is an operational attribute (managed by slapd and
not complying to the regular LDAP schemas), the attribute won't appear when
listing the users' entry as LDIF, if not requested explicitely.
