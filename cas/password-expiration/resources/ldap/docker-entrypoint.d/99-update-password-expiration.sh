#!/bin/bash

# start slapd in background
echo -n "Starting slapd daemon in background..."
slapd -u ${RUN_AS_UID} -g ${RUN_AS_GID} -h "ldapi:/// ldap://127.0.0.1/"
echo "Started: OK"

echo -n "Waiting for LDAP deamon to start..."
while true; do
    sleep 1
    ldapsearch -x >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        break
    fi
done;
echo "Waiting: OK"


echo "Adding a 2seconds password expiration policy"

cat <<EOF | ldapadd -Dcn=admin,dc=georchestra,dc=org -w ${SLAPD_PASSWORD} -x
dn: cn=2secondsexpiration,ou=pwpolicy,dc=georchestra,dc=org
objectClass: person
objectClass: pwdPolicy
objectClass: pwdPolicyChecker
cn: default
cn: pwpolicy
pwdAttribute: userPassword
sn: pwpolicy
pwdExpireWarning: 2592000
pwdGraceAuthNLimit: 0
pwdMaxAge: 2
pwdMinAge: 0

EOF

echo "Affecting the 2seconds password expiration policy to testuser"

cat <<EOF | ldapmodify -Dcn=admin,dc=georchestra,dc=org -w ${SLAPD_PASSWORD} -x
dn: uid=testuser,ou=users,dc=georchestra,dc=org
changetype: modify
add: pwdPolicySubentry
pwdPolicySubentry: cn=2secondsexpiration,ou=pwpolicy,dc=georchestra,dc=org

EOF

pkill slapd

# wait for ldap to stop
 echo -n "Waiting for LDAP to stop..."
 while true; do
     sleep 1
     pgrep slapd >/dev/null 2>&1
     if [ $? -ne 0 ]; then
         break
     fi
 done;
 echo "Waiting: OK"
