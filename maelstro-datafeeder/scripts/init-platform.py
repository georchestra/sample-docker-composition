"""Initialize the local users and roles needed by Maelstro and Datafeeder."""

import requests


BASE_URL = "http://gateway:8080/console/private"
AUTH = ("testadmin", "testadmin")


def post(path: str, payload: dict, accepted: tuple[int, ...] = (200, 409)) -> None:
    response = requests.post(
        f"{BASE_URL}/{path}",
        json=payload,
        auth=AUTH,
        headers={"Accept": "application/json"},
        timeout=30,
    )
    print(f"POST {path}: {response.status_code} {response.text}")
    if response.status_code not in accepted:
        response.raise_for_status()


def exists(path: str) -> bool:
    response = requests.get(
        f"{BASE_URL}/{path}",
        auth=AUTH,
        headers={"Accept": "application/json"},
        timeout=30,
    )
    if response.status_code not in (200, 404):
        response.raise_for_status()
    return response.status_code == 200


for role in ("MAELSTRO", "DATAFEEDER"):
    if not exists(f"roles/{role}"):
        post("roles", {"cn": role, "description": "Local development role"})

if not exists("users/tmaelstro"):
    post(
        "users",
        {
            "pending": "false",
            "org": "PSC",
            "manager": "",
            "sn": "MAELSTRO",
            "givenName": "Test",
            "mail": "psc+maelstrotest@georchestra.org",
            "description": "Local development user for Maelstro and Datafeeder",
        },
    )

for user in ("testadmin", "tmaelstro"):
    post(
        "roles_users",
        {"users": [user], "PUT": ["MAELSTRO", "DATAFEEDER"], "DELETE": []},
        accepted=(200,),
    )
