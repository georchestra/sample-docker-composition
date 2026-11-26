# POC Keycloak

Disclaimer: cette doc n'est qu'en français...

## Périmètre

### Motivation du POC

Le POC part du besoin de pouvoir disposer d'un système de connexion à plusieurs facteurs.

### Contraintes prises en compte

J'ai essayé ici de gérer les cas suivants:

- Disposer de la même liste de gestion des droits pour geoserver, geoentwork et datafeeder. <== Groupes de travail
- Synchroniser geonetwork avec Keycloak et non plus la console.
- Utilisation d'un provider externe: ici Proconnect
- Garder le LDAP

### Limitations

Je n'ai pas trouvé de solution simple à mettre à place pour remplacer la délégation.

### Mise en oeuvre future

Il y a plusieurs points que l'on pourrait améliorer:

- Gérer la délégation d'organisation au sens Keycloak:
    - Keycloak permet d'activer des "organisations" lui permettant de scinder la gestion, les fournisseurs d'identité
      etc.. pour chaque organisme.
    - Cette fonctionnalité nécessiterait une refonte de la manière dont on gère nous, les organismes, au sein de
      geOrchestra.
    - J'ai l'impression que ce serait bénéfique que l'on engage une discussion dans ce sens. 
- On pourrait également se passer du LDAP -> A activer/désactiver selon les plateformes.
    - On peut faire en sorte que Geoserver récupère les rôles à partir de
      keycloak https://docs.geoserver.org/main/en/user/community/keycloak/
- Accepter un header Bearer côté gateway pour authentifier un utilisateur qui n'a pas un compte local et donc d'auth
  Basic
    - Keycloak permet de récupérer un jeton, quelque soit le provider externe et donc d'authentifier l'utilisateur (mais
      la gateway ne supporte pas ça encore)
- Thème de login/page etc...

## Mise en place

Modifier le clientId et Secret de proconnect dans `georchestra-realm.json`:

```
        "clientId": "...",
        "clientSecret": "...",
```

Et lancer via docker.

Keycloak remplace la console : il gère utilisateurs, organismes et groupes, et le LDAP reste derrière lui. Un groupe
Keycloak donne les mêmes droits sur les métadonnées (GeoNetwork) et sur les données (GeoServer).

| Application | Changement                                                                                                                                                                           |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Keycloak    | Extension : code figé par groupe (`cn`), case « Est un organisme », écriture des organismes dans le LDAP, claim `group_roles` (`ROLE_GRP_<CODE>`). Console d'administration patchée. |
| Gateway     | Configuration seulement : connexion OIDC par Keycloak, `sec-roles` complété par `group_roles`.                                                                                       |
| GeoServer   | Rien : il lit déjà ses rôles dans `sec-roles`.                                                                                                                                       |
| GeoNetwork  | Source de comptes Keycloak (`georchestra.accounts.source=keycloak`), un groupe GN par groupe Keycloak (`role_per_org`). Branche `feat/keycloak-accounts-source`, non commitée.       |
| Datafeeder  | Annuaire Keycloak (`DIRECTORY_PROVIDER=keycloak`), même liste de groupes pour les données et les métadonnées. Branche `feat/keycloak-directory`.                                     |

Détails : [CHANGEMENTS.md](CHANGEMENTS.md), [README.fr.md](README.fr.md).

## Comparatif de gestion

|                                 | Gateway + console (LDAP)                                                                                                                                                                                                                 | Gateway + Keycloak + LDAP                                                                                                                                                                                                     |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Comptes et organismes**       | + outil connu : inscription, validation des comptes en attente, délégation, emails<br>− un seul organisme par utilisateur, pas de groupes de partage                                                                                     | + console Keycloak : groupes, ProConnect, SIRET → organisme<br>− deux annuaires à garder alignés (extension qui écrit dans le LDAP, job de synchro)<br>− console d'administration Keycloak patchée (case « Est un organisme ») |
| **GeoServer**                   | + rôles LDAP listés dans Data security, avec `ROLE_`<br>− droits par rôle seulement, pas par organisme ou groupe                                                                                                                         | + inchangé, rôles LDAP listés<br>+ `ROLE_GRP_<CODE>` par groupe via `sec-roles`, sans code GeoServer<br>− les `ROLE_GRP_*` n'apparaissent pas dans Data security (mais possible de le faire)                                  |
| **GeoNetwork**                  | + intégration standard publiée, synchro depuis la console                                                                                                                                                                                | + synchro depuis Keycloak, tous les groupes deviennent des groupes GN (`role_per_org`)<br>+ possibilité de gestion par groupes de travail                                                                                     |
| **Datafeeder**                  | + version publiée<br>− métadonnées partagées par organisme, données par rôle : deux listes différentes                                                                                                                                   | + annuaire Keycloak : mêmes groupes pour les données et les métadonnées, identifiants Keycloak                                                                                                                                |
| **API / Bearer / QGIS**         | + HTTP Basic pour les comptes LDAP (QGIS identifiant / mot de passe)<br>− comptes externes (ProConnect) : aucun accès aux API<br>− Bearer inutilisable sans fournisseur qui délivre des JWT (il faudrait des clés d'API dans la gateway) | + Basic pour les comptes LDAP et Bearer Keycloak pour tous, ProConnect compris (device flow, OAuth2 dans QGIS)<br>+ gateway susceptible de supporter un token Bearer                                                          |
| **Migration depuis l'existant** | sans objet                                                                                                                                                                                                                               | + aucune migration de comptes<br>− règles datafeeder et liens GN à réassocier aux identifiants Keycloak                                                                                                |
