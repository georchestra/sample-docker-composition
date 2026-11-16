# Changements par application

Ce que le POC modifie dans chaque application pour faire tourner geOrchestra sans
console, avec Keycloak comme source des utilisateurs, organismes et groupes.
Fonctionnement d'ensemble : [README.fr.md](README.fr.md). Modèle cible :
[conception-groupes-organismes.md](conception-groupes-organismes.md).

| Application | Où | Nature du changement |
| --- | --- | --- |
| Keycloak | `keycloak/` (ce dépôt) | extension Java, console d'administration modifiée, realm, job de synchro |
| Gateway | `datadir/gateway/` (ce dépôt) | configuration seulement, image `georchestra/gateway:latest` |
| GeoServer | aucun | aucun changement |
| GeoNetwork | dépôt `geonetwork`, branche `feat/keycloak-accounts-source` | nouvelle source de comptes Keycloak (code), configuration |
| Datafeeder | dépôt `datafeeder`, branche `feat/keycloak-directory` | annuaire Keycloak (code backend), configuration |
| Console | aucun | n'est plus déployée |

## Keycloak

Image `poc-keycloak/keycloak:26.8.1`, construite par `keycloak/Dockerfile` à
partir de Keycloak 26.8.1.

**Extension `keycloak/organization-extension`** (jar `georchestra-organization.jar`) :

- *Event listener* `georchestra-organization`, activé dans le realm :
  - donne à chaque groupe de premier niveau un **code** (attribut `cn`), généré
    à partir du nom à la création puis figé ;
  - gère la case « Est un organisme » (attribut `isOrganization`) : un organisme
    est écrit dans le LDAP (`cn=<CODE>` avec ses membres, `o=<CODE>` avec ses
    détails), un groupe simple jamais ;
  - aligne l'attribut utilisateur `organization` (liste déroulante
    « Organisme ») avec l'appartenance aux groupes organismes : un seul
    organisme par utilisateur ;
  - suit renommages, suppressions et changements de case (LDAP, liste
    déroulante, membres).
- *Protocol mapper* « geOrchestra group roles » (`georchestra-group-roles`) :
  ajoute au jeton un claim `group_roles` contenant `ROLE_GRP_<CODE>` pour chaque
  groupe de l'utilisateur et pour les parents de ses sous-groupes. Ces rôles
  servent aux droits sur les données dans GeoServer.
- *Password hash provider* `georchestra-ldap` : vérifie les empreintes de mots
  de passe LDAP importées telles quelles (`{SHA}`, `{SSHA}`, `{SHA256}`…), puis
  Keycloak rehache le mot de passe avec son algorithme à la première connexion.
  Sert à la migration sans LDAP ([poc-keycloak2](../poc-keycloak2/README.fr.md)).

**Console d'administration** : `keycloak/admin-ui/groups-modal-organization.patch`
ajoute la case « Est un organisme » à la fenêtre de création / modification de
groupe (`GroupsModal.tsx`). Le Dockerfile reconstruit la console depuis les
sources de Keycloak et remplace son jar. Le patch est à revérifier à chaque
montée de version.

**Realm `keycloak/georchestra-realm.json`** :

- fédération LDAP `georchestra-ldap` en `WRITABLE` ; mapper `roles` (`ou=roles`
  → rôles du realm, `LDAP_ONLY`) ; mapper `organizations` (`ou=orgs` → groupes,
  `IMPORT`) ;
- rôle `USER` composite de `account/manage-account` et `account/view-profile` ;
- client `georchestra-gateway` avec les claims `roles` (préfixe `ROLE_`),
  `organization` (code de l'organisme) et `group_roles` ;
- clients à compte de service `geonetwork` et `datafeeder` (rôles
  `realm-management` `view-users`, `query-groups`, `view-realm`) ;
- fournisseur d'identité ProConnect (environnement d'intégration) ;
- event listener `georchestra-organization` activé.

Le realm n'est importé que si la base Keycloak est vide : une modification du
fichier se reporte aussi par l'API admin sur une stack existante.

**Job `keycloak/sync-ldap.py`** (service `keycloak-ldap-sync`, ponctuel) :
synchronisation complète LDAP → Keycloak (utilisateurs, rôles, organismes),
mappers ProConnect SIRET → organisme, déclaration et remplissage de la liste
déroulante « Organisme ».

## Gateway

Aucun code : image `georchestra/gateway:latest`, configuration dans
`datadir/gateway/` :

- `security.yaml` : connexion OIDC par Keycloak uniquement
  (`disableLdapFormLogin`), identifiants LDAP conservés
  (`disableUidTransformation: keycloak`), pas de création de comptes LDAP
  (`createNonExistingUsersInLDAP: false`), endpoints Keycloak déclarés
  explicitement (navigateur sur `localhost:8180`, conteneurs sur
  `keycloak:8080`), déconnexion Keycloak par `end-session-uri` ;
- claims : organisme = `$.organization` ; rôles = `$.roles` **et**
  `$.group_roles`, donc `sec-roles` contient aussi les `ROLE_GRP_<CODE>` ;
- LDAP conservé pour le HTTP Basic (comptes techniques, ex. datafeeder →
  GeoServer) ;
- `gateway.yaml` / `routes.yaml` : routes `/` (page whoami), `/geoserver`,
  `/geonetwork`, `/dataset`, `/datafeeder-backend`.

## GeoServer

Aucun changement, ni code ni configuration. GeoServer prend déjà les rôles de
l'utilisateur dans l'en-tête `sec-roles` (filtre `proxy`, `roleSource=Header`) :
les `ROLE_GRP_<CODE>` envoyés par la gateway suffisent pour que les règles de
couche écrites par datafeeder s'appliquent. Ces rôles n'existent pas dans le
rôle service (LDAP) et n'apparaissent donc pas dans l'écran Data security.

## GeoNetwork

Dépôt `~/dev/georchestra/geonetwork`, branche `feat/keycloak-accounts-source`
(partie de `georchestra-gn4.4.x`), modifications **non commitées**. Image
`georchestra/geonetwork:keycloak-accounts-source`.

- `georchestra-authnz`, nouveau paquet `...security.integration.keycloak` :
  - `KeycloakAccountsRepository` : implémentation de
    `CanonicalAccountsRepository` sur l'API admin Keycloak, alternative à la
    console pour la synchronisation des comptes. Organisme = groupe (code =
    attribut `cn`, libellé = nom du groupe), rôles = rôles du realm ; avec
    `syncAllGroups`, tous les groupes de premier niveau sont des groupes
    GeoNetwork, et l'utilisateur reçoit un rôle `<CODE>:USER` par groupe
    (mode `role_per_org`) ;
  - `KeycloakAdminClient` : client HTTP de l'API admin (jeton du compte de
    service, *client credentials*) ;
  - `KeycloakProperties` : propriétés `georchestra.keycloak.*` ;
  - tests `KeycloakAccountsRepositoryTest` (JUnit 4).
- `GeorchestraExternalizedAccountsConfiguration` : choix de la source par
  `georchestra.accounts.source=console|keycloak`.
- `externalized-accounts`, `AbstractGroupSynchronizer` : met aussi à jour le
  libellé d'un groupe quand seul le titre de l'organisme change (le code, lui,
  ne change pas).

Configuration (`datadir/geonetwork/geonetwork.properties`) :
`georchestra.accounts.source=keycloak`, `georchestra.keycloak.*`,
`georchestra.keycloak.syncAllGroups=true`, `geonetwork.syncMode=role_per_org`.

## Datafeeder

Dépôt `~/dev/georchestra/datafeeder`, branche `feat/keycloak-directory` (partie
de `origin/main`, non poussée). Image
`georchestra/datafeeder-python-backend:keycloak-directory-local-executor` ; le
frontend est l'image `latest`, inchangée.

Commits (backend uniquement) :

- `0e01c8fe` annuaire Keycloak : interface `DirectoryService` avec deux
  implémentations choisies par `DIRECTORY_PROVIDER` (`console` par défaut,
  `keycloak`) ; tous les appels à la console passent par elle. Option
  `GEONETWORK_AUTH=keycloak` pour appeler GeoNetwork avec un jeton du compte de
  service (non utilisée dans le POC, qui reste en HTTP Basic).
- `aa62ccb0` code de l'organisme lu dans un attribut de groupe
  (`KEYCLOAK_ORG_CODE_ATTRIBUTE=cn`).
- `5a3106df` seuls les groupes marqués organisme sont des organismes
  (`KEYCLOAK_ORG_FLAG_ATTRIBUTE=isOrganization`).
- `4d8431a2` correctif indépendant de Keycloak : la fiche de métadonnées est
  créée dans le groupe GeoNetwork du jeu de données (et non dans le groupe 100).
  Pourrait partir sur `main` séparément.
- `ac28a751` partage des métadonnées avec tous les groupes Keycloak, pas
  seulement les organismes ; un utilisateur accède à ce qui est partagé avec
  n'importe lequel de ses groupes.
- `5e252fac` création du groupe GeoNetwork manquant quand une règle de partage
  en a besoin (avant la synchro GeoNetwork suivante).
- `e56033eb` partage des données avec les mêmes groupes : le panneau « Accès
  aux données » liste les groupes (`get_data_roles()`), et une règle sur un
  groupe écrit `ROLE_GRP_<CODE>` dans les règles de couche GeoServer. Avec la
  console, rien ne change (règles sur les rôles).
- `e7ac631f` `GEOSERVER_AUTH=headers` / `GEONETWORK_AUTH=headers` : appel direct
  de GeoServer et GeoNetwork avec les en-têtes `sec-*` d'un compte technique
  (`SEC_HEADERS_ROLES`), pour un déploiement sans LDAP où la gateway ne vérifie
  plus le HTTP Basic. Utilisé par poc-keycloak2.

Configuration (variables d'environnement de `datafeeder-backend` dans
`docker-compose.yaml`) : `DIRECTORY_PROVIDER=keycloak`, `KEYCLOAK_INTERNAL_URL`,
`KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET`,
`KEYCLOAK_ORG_CODE_ATTRIBUTE=cn`, `KEYCLOAK_ORG_FLAG_ATTRIBUTE=isOrganization` ;
`GN_SYNC_MODE` reste à sa valeur par défaut, `ORG`.

Résultat dans l'interface : les panneaux « Accès aux informations du jeu de
données » et « Accès aux données » proposent la même liste de groupes.

## Reste à faire

- Profils par groupe (sous-groupes `editors`, `reviewers`, `admins`) :
  `<CODE>:GN_EDITOR`… dans GeoNetwork, `ROLE_GRP_<CODE>_EDITOR` pour l'écriture
  sur les données. Aujourd'hui, une règle « Édition » sur les données donne
  l'écriture à tous les membres du groupe.
- Migration d'une instance existante : réassocier les ID console aux ID Keycloak
  (liens de groupes GeoNetwork, règles datafeeder).
