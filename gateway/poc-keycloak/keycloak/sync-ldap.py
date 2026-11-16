"""Imports every LDAP user, role and organization into Keycloak, and maps SIRETs to organizations.

1. Triggers a full user synchronization of the "georchestra-ldap" user federation,
   then the "sync LDAP -> Keycloak" action of its role and group mappers. Without it
   Keycloak only knows the users, roles and groups seen at login, so GeoNetwork and
   datafeeder would not get them all.
2. For each LDAP organization having a SIRET (orgUniqueId), maintains a ProConnect
   identity provider mapper "Advanced Claim to Group": a ProConnect user whose
   `siret` claim matches joins the organization group (sync mode FORCE: at every
   ProConnect login, the organization follows the SIRET). Mappers of organizations
   that lost their SIRET are removed.
3. Declares the "organization" user profile attribute, a dropdown listing the
   organizations (admins edit it, users see it), and fills it for the users that
   belong to exactly one organization. The "georchestra-organization" event listener
   then keeps it in sync with the organization group membership.
"""

import json
import os
import urllib.error
import urllib.parse
import urllib.request

from ldap3 import Connection, Server

BASE = os.environ["KEYCLOAK_URL"].rstrip("/")
REALM = os.environ["KEYCLOAK_REALM"]
IDP_ALIAS = os.environ.get("PROCONNECT_ALIAS", "proconnect")
# Path of the group holding the organizations ("" = top-level groups)
ORG_GROUP_PATH = os.environ.get("ORG_GROUP_PATH", "").strip("/")
SIRET_MAPPER_PREFIX = "siret "
ORG_ATTRIBUTE = os.environ.get("ORG_ATTRIBUTE", "organization")
# Group attribute holding the organization code (the group name being the organization name)
ORG_CODE_ATTRIBUTE = os.environ.get("ORG_CODE_ATTRIBUTE", "cn")
# Group attribute flagging an organization ("Est un organisme"), value "true"
ORG_FLAG_ATTRIBUTE = os.environ.get("ORG_FLAG_ATTRIBUTE", "isOrganization")


def code(group):
    """Code of an organization group (its name until it has one)"""
    return ((group.get("attributes") or {}).get(ORG_CODE_ATTRIBUTE) or [group["name"]])[0]


def call(method, path, token=None, data=None, json_body=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    body = None
    if data is not None:
        body = urllib.parse.urlencode(data).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif json_body is not None:
        body = json.dumps(json_body).encode()
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    with urllib.request.urlopen(request) as response:
        content = response.read()
        return json.loads(content) if content else None


def ldap_sirets():
    """{SIRET: organization short name} of the LDAP organizations having a SIRET"""
    connection = Connection(
        Server(os.environ["LDAP_URL"]),
        os.environ["LDAP_BIND_DN"],
        os.environ["LDAP_BIND_PASSWORD"],
        auto_bind=True,
    )
    connection.search(
        os.environ.get("LDAP_ORGS_DN", "ou=orgs,dc=georchestra,dc=org"),
        "(&(objectClass=georchestraOrg)(orgUniqueId=*))",
        attributes=["o", "orgUniqueId"],
    )
    sirets = {}
    for entry in connection.entries:
        siret = "".join(str(entry.orgUniqueId.value).split())
        org = str(entry.o.value)
        if siret in sirets:
            print(f"WARNING: SIRET {siret} shared by {sirets[siret]} and {org}, keeping {sirets[siret]}")
            continue
        sirets[siret] = org
    return sirets


def sync_federation(token):
    components = call("GET", f"/admin/realms/{REALM}/components", token)
    ldap = next(c for c in components if c["providerId"] == "ldap")
    result = call("POST", f"/admin/realms/{REALM}/user-storage/{ldap['id']}/sync?action=triggerFullSync", token)
    print(f"users: {result}")
    mappers = [
        c
        for c in components
        if c.get("parentId") == ldap["id"] and c["providerId"] in ("role-ldap-mapper", "group-ldap-mapper")
    ]
    for mapper in mappers:
        result = call(
            "POST",
            f"/admin/realms/{REALM}/user-storage/{ldap['id']}/mappers/{mapper['id']}/sync?direction=fedToKeycloak",
            token,
        )
        print(f"{mapper['name']}: {result}")


def ldap_members():
    """{organization code: [member uid]} from the LDAP organizations (cn=<CODE>)"""
    connection = Connection(
        Server(os.environ["LDAP_URL"]),
        os.environ["LDAP_BIND_DN"],
        os.environ["LDAP_BIND_PASSWORD"],
        auto_bind=True,
    )
    connection.search(
        os.environ.get("LDAP_ORGS_DN", "ou=orgs,dc=georchestra,dc=org"),
        "(objectClass=groupOfMembers)",
        attributes=["cn", "member"],
    )
    members = {}
    for entry in connection.entries:
        uids = []
        for dn in entry.member.values if "member" in entry else []:
            first = str(dn).split(",", 1)[0]
            if first.lower().startswith("uid="):
                uids.append(first[4:])
        members[str(entry.cn.value)] = uids
    return members


def import_ldap_memberships(token):
    """Adds to the Keycloak organization groups the members they have in LDAP (the group mapper,
    in IMPORT mode, only imports them for new users). Never removes: Keycloak is the master."""
    base = f"/admin/realms/{REALM}"
    groups = {code(g): g for g in org_groups(token)}
    added = 0
    for org, uids in ldap_members().items():
        group = groups.get(org)
        if group is None:
            continue
        current = {m["username"] for m in call("GET", f"{base}/groups/{group['id']}/members?max=-1&briefRepresentation=true", token)}
        for uid in uids:
            if uid.lower() in current:
                continue
            users = call("GET", f"{base}/users?username={urllib.parse.quote(uid)}&exact=true", token)
            if users:
                call("PUT", f"{base}/users/{users[0]['id']}/groups/{group['id']}", token)
                added += 1
                print(f"organization: {uid} added to '{group['name']}' (LDAP member)")
    print(f"organization: {added} LDAP membership(s) imported")


def sync_siret_mappers(token):
    idp_path = f"/admin/realms/{REALM}/identity-provider/instances/{IDP_ALIAS}"
    try:
        call("GET", idp_path, token)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"siret: no '{IDP_ALIAS}' identity provider, skipped")
            return
        raise

    path_by_code = {code(g): g["path"] for g in org_groups(token)}
    wanted = {}
    for siret, org in ldap_sirets().items():
        if org not in path_by_code:
            print(f"WARNING: no Keycloak organization with code {org} for SIRET {siret}, skipped")
            continue
        wanted[SIRET_MAPPER_PREFIX + siret] = {
            "name": SIRET_MAPPER_PREFIX + siret,
            "identityProviderAlias": IDP_ALIAS,
            "identityProviderMapper": "oidc-advanced-group-idp-mapper",
            "config": {
                "syncMode": "FORCE",
                "claims": json.dumps([{"key": "siret", "value": siret}]),
                "are.claim.values.regex": "false",
                "group": path_by_code[org],
            },
        }

    existing = {
        m["name"]: m
        for m in call("GET", f"{idp_path}/mappers", token)
        if m["name"].startswith(SIRET_MAPPER_PREFIX)
    }
    for name, mapper in wanted.items():
        if name in existing:
            if existing[name]["config"] != mapper["config"]:
                call("PUT", f"{idp_path}/mappers/{existing[name]['id']}", token,
                     json_body={**mapper, "id": existing[name]["id"]})
                print(f"siret: updated {name} -> {mapper['config']['group']}")
        else:
            call("POST", f"{idp_path}/mappers", token, json_body=mapper)
            print(f"siret: created {name} -> {mapper['config']['group']}")
    for name, mapper in existing.items():
        if name not in wanted:
            call("DELETE", f"{idp_path}/mappers/{mapper['id']}", token)
            print(f"siret: removed {name}")
    print(f"siret: {len(wanted)} organization(s) mapped")


def candidate_groups(token):
    """Groups that may be organizations (top-level groups, or children of ORG_GROUP_PATH)"""
    base = f"/admin/realms/{REALM}"
    params = "briefRepresentation=false&max=-1"
    if not ORG_GROUP_PATH:
        return call("GET", f"{base}/groups?{params}", token)
    parent = call("GET", f"{base}/group-by-path/{urllib.parse.quote(ORG_GROUP_PATH)}", token)
    return call("GET", f"{base}/groups/{parent['id']}/children?{params}", token)


def org_groups(token):
    """Organization groups (flagged), with their attributes"""
    return [
        g for g in candidate_groups(token)
        if ((g.get("attributes") or {}).get(ORG_FLAG_ATTRIBUTE) or [""])[0].lower() == "true"
    ]


def flag_ldap_organizations(token):
    """Organizations imported from LDAP (they have a code but no flag) get flagged"""
    for g in candidate_groups(token):
        attributes = g.get("attributes") or {}
        if attributes.get(ORG_CODE_ATTRIBUTE) and not attributes.get(ORG_FLAG_ATTRIBUTE):
            attributes[ORG_FLAG_ATTRIBUTE] = ["true"]
            call("PUT", f"/admin/realms/{REALM}/groups/{g['id']}", token,
                 json_body={"name": g["name"], "attributes": attributes})
            print(f"organization: '{g['name']}' (from LDAP) flagged as an organization")


def sync_organization_attribute(token):
    base = f"/admin/realms/{REALM}"
    orgs = sorted(org_groups(token), key=lambda g: g["name"].lower())
    # value = code, label = organization name (the group name)
    names = [code(g) for g in orgs]
    labels = {code(g): g["name"] for g in orgs}

    # user profile: the organization attribute is a dropdown of the organizations
    profile = call("GET", f"{base}/users/profile", token)
    attribute = {
        "name": ORG_ATTRIBUTE,
        "displayName": "Organisme",
        "multivalued": False,
        "permissions": {"view": ["admin", "user"], "edit": ["admin"]},
        "annotations": {"inputType": "select", "inputOptionLabels": labels},
        "validations": {"options": {"options": names}} if names else {},
    }
    others = [a for a in profile["attributes"] if a["name"] != ORG_ATTRIBUTE]
    current = next((a for a in profile["attributes"] if a["name"] == ORG_ATTRIBUTE), None)
    if current is None or {k: current.get(k) for k in attribute} != attribute:
        profile["attributes"] = others + [attribute]
        call("PUT", f"{base}/users/profile", token, json_body=profile)
        print(f"organization: user profile attribute updated ({len(names)} organizations)")

    # fill the attribute of the users of exactly one organization
    orgs_of = {}
    for group in orgs:
        for member in call("GET", f"{base}/groups/{group['id']}/members?max=-1&briefRepresentation=true", token):
            orgs_of.setdefault(member["id"], []).append(code(group))
    filled = 0
    for user_id, user_orgs in orgs_of.items():
        if len(user_orgs) > 1:
            user = call("GET", f"{base}/users/{user_id}", token)
            print(f"WARNING: {user['username']} belongs to {len(user_orgs)} organizations "
                  f"({', '.join(user_orgs)}): choose one in its profile")
            continue
        user = call("GET", f"{base}/users/{user_id}", token)
        attributes = user.get("attributes") or {}
        if attributes.get(ORG_ATTRIBUTE) != user_orgs:
            attributes[ORG_ATTRIBUTE] = user_orgs
            user["attributes"] = attributes
            call("PUT", f"{base}/users/{user_id}", token, json_body=user)
            filled += 1
    print(f"organization: {filled} user(s) updated")


token = call(
    "POST",
    "/realms/master/protocol/openid-connect/token",
    data={
        "grant_type": "password",
        "client_id": "admin-cli",
        "username": os.environ["KEYCLOAK_ADMIN_USERNAME"],
        "password": os.environ["KEYCLOAK_ADMIN_PASSWORD"],
    },
)["access_token"]
sync_federation(token)
flag_ldap_organizations(token)
import_ldap_memberships(token)
sync_siret_mappers(token)
sync_organization_attribute(token)
