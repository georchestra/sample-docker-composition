# POC : geOrchestra avec Keycloak, sans console

Cette composition fait tourner geOrchestra avec Keycloak 26.8.1 comme seul point
de connexion, et **sans la console** :

- le **LDAP geOrchestra reste l'annuaire des comptes**. Keycloak le fédère : les
  utilisateurs, `ou=roles` en rôles du realm, `ou=orgs` en groupes.
- la **gateway** authentifie les utilisateurs par Keycloak (OIDC). `/` mène à
  une petite page qui affiche le `/whoami` de la gateway, avec un lien de
  connexion / déconnexion.
- **GeoNetwork** garde l'intégration geOrchestra habituelle (en-têtes `sec-*`,
  synchronisation périodique des comptes), mais synchronise utilisateurs,
  organismes et rôles depuis Keycloak au lieu de la console. Il faut pour cela
  la branche geonetwork `feat/keycloak-accounts-source`, voir plus bas.
- **Datafeeder** lit organismes, groupes, rôles et utilisateurs par l'API admin
  de Keycloak. Il faut pour cela la branche datafeeder `feat/keycloak-directory`,
  voir plus bas.
- **GeoServer** ne change pas : il fait confiance aux en-têtes `sec-*` et prend
  ses rôles dans `sec-roles` (rôles du realm, plus un `ROLE_GRP_<CODE>` par
  groupe Keycloak).

Version anglaise : [README.md](README.md). Changements par application :
[CHANGEMENTS.md](CHANGEMENTS.md). Conception du modèle organismes / groupes :
[conception-groupes-organismes.md](conception-groupes-organismes.md).

```
navigateur ──> gateway ──OIDC──> keycloak ──fédération──> ldap
                  │
                  ├─ sec-* ──> geoserver
                  ├─ sec-* ──> geonetwork ──synchro (API admin)──> keycloak
                  └─ sec-* ──> datafeeder ──API admin────────────> keycloak
```

## Utilisation

```
# image datafeeder backend avec le support Keycloak (dépôt datafeeder,
# branche feat/keycloak-directory)
docker build -f apps/backend/Dockerfile --target local-executor \
  -t georchestra/datafeeder-python-backend:keycloak-directory-local-executor .

# image geonetwork avec la source de comptes Keycloak (dépôt geonetwork,
# branche feat/keycloak-accounts-source), taguée
# georchestra/geonetwork:keycloak-accounts-source (ou définir GEONETWORK_IMAGE)

docker compose pull   # les images en latest (gateway, datafeeder frontend...)
docker compose up -d
```

- http://localhost:8080/ : page whoami (connexion / déconnexion)
- http://localhost:8080/dataset/ : datafeeder
- http://localhost:8180/admin : console d'administration Keycloak (`admin` / `admin`)
- http://localhost:8180/realms/georchestra/account : page « mon compte » de
  Keycloak (remplace celle de la console)

Les utilisateurs sont les comptes LDAP geOrchestra habituels : `testadmin`,
`testeditor`, `testuser`… (mot de passe = identifiant).

## Composants

| Service                | Rôle |
|------------------------|------|
| `ldap`                 | LDAP geOrchestra, annuaire des comptes |
| `keycloak`             | Fournisseur OIDC. Realm `georchestra` importé depuis `keycloak/georchestra-realm.json`. Image construite par `keycloak/Dockerfile` (ajoute l'extension geOrchestra et la console d'administration modifiée) |
| `keycloak-ldap-sync`   | Ponctuel : importe dans Keycloak tous les utilisateurs, rôles et organismes du LDAP (sinon créés à la première connexion) |
| `gateway`              | Gateway geOrchestra, client OIDC de Keycloak |
| `whoami-app`           | nginx qui sert `whoami-app/index.html`, routé sur `/` |
| `database`             | Base geOrchestra (GeoNetwork, datafeeder) |
| `geoserver`            | `/geoserver/` |
| `geonetwork`, `elasticsearch` | `/geonetwork/`. Image construite depuis la branche geonetwork `feat/keycloak-accounts-source` |
| `datafeeder-backend`, `datafeeder-frontend`, `datadb` | `/dataset/`, `/datafeeder-backend/` |

## Keycloak

### Fédération LDAP (`georchestra-ldap`)

- Utilisateurs de `ou=users` (`uid`, `entryUUID`). Mode d'édition `WRITABLE` :
  les changements de mot de passe et de profil faits dans Keycloak sont écrits
  dans le LDAP.
- Mapper `roles` : `ou=roles` (`groupOfMembers`) → rôles du realm (`LDAP_ONLY`).
- Mapper `organizations` : `ou=orgs` (`cn=<ORG>` `groupOfMembers`) → groupes de
  premier niveau (mode `IMPORT`, voir plus bas). Le nom long (`o`) est gardé en
  attribut du groupe.
- L'action requise « Verify profile » est désactivée : certains comptes LDAP
  n'ont pas de prénom.
- Avec le mapper de rôles en `LDAP_ONLY`, les utilisateurs LDAP n'ont que leurs
  rôles LDAP, pas `default-roles-georchestra`. Le rôle `USER` (donné à tout
  utilisateur geOrchestra) est donc un composite des rôles du client `account`
  `manage-account` et `view-profile`, pour que les utilisateurs accèdent à leur
  page de compte Keycloak.

Keycloak ne connaît un utilisateur, un rôle ou un organisme qu'après l'avoir vu
à une connexion, ou après une synchronisation. `keycloak-ldap-sync` fait une
synchronisation complète au démarrage. Ensuite Keycloak réimporte les
utilisateurs LDAP périodiquement (ceux modifiés toutes les 5 min, tous toutes
les heures). Le relancer après avoir ajouté des rôles ou des organismes dans le
LDAP :

```
docker compose run --rm keycloak-ldap-sync
```

### Organismes et groupes

Un groupe est un **organisme** quand il est marqué « Est un organisme » : une
case à cocher dans la fenêtre de création / modification de groupe de la
console d'administration (groupes de premier niveau seulement), stockée dans
l'attribut de groupe `isOrganization` (`true` / `false`). Décocher la case
supprime l'organisme (entrées LDAP, option de la liste déroulante) ; le groupe
et ses membres restent, comme groupe simple. Les autres groupes sont des groupes
simples : un utilisateur a **un** organisme et autant de groupes que voulu.

La case vient d'une console d'administration modifiée : Keycloak ne permet pas
d'ajouter des champs aux groupes, donc `keycloak/Dockerfile` reconstruit la
console depuis les sources de Keycloak (tag `26.8.1`) avec
`keycloak/admin-ui/groups-modal-organization.patch`, et remplace son jar dans
l'image. Le patch est à revérifier à chaque montée de version de Keycloak.

Un groupe (organisme ou non) a :

- un **nom** : le nom du groupe, le seul qu'on saisit et qu'on lit (ex.
  « Project Steering Committee ») ;
- un **code** : identifiant technique (DN LDAP, groupe GeoNetwork, rôle
  GeoServer, datafeeder, `sec-org`), attribut de groupe `cn`, généré à partir du
  nom à la création (ex. `DIRECTION_REGIONALE_DE_L_ENVIRONNEMENT`) et jamais
  modifié ensuite : renommer un groupe ne change que son libellé ;
- pour un organisme, des détails facultatifs, attributs de groupe écrits dans
  son entrée LDAP `o=<CODE>` : `siret` (`orgUniqueId`), `businessCategory`,
  `description`, `mail`.

L'organisme d'un utilisateur s'affiche dans sa fiche sous forme de liste
déroulante « Organisme » (attribut `organization` = le code ; modifiable par les
administrateurs, visible par l'utilisateur).

L'extension `keycloak/organization-extension` (event listener
`georchestra-organization`, activé dans le realm) maintient la cohérence, une
fois la modification validée :

| Changement dans Keycloak | Effet |
| --- | --- |
| groupe marqué organisme (créé ou modifié) | reçoit un code ; `cn=<CODE>` (nom dans `o`, membres) et `o=<CODE>` (détails) écrits dans le LDAP ; ajouté à la liste déroulante |
| groupe simple créé | reçoit un code (jamais écrit dans le LDAP) |
| organisme renommé / détails modifiés | `o` de `cn=<CODE>` / `o=<CODE>` mis à jour dans le LDAP ; libellé de la liste mis à jour ; code inchangé |
| case décochée | n'est plus un organisme : entrées LDAP supprimées, la liste déroulante des membres suit |
| organisme supprimé | entrées LDAP supprimées, retiré de la liste déroulante |
| liste déroulante modifiée (admin) | l'utilisateur quitte son organisme et rejoint celui choisi (`member` LDAP aussi) ; ses autres groupes ne bougent pas |
| organisme rejoint (onglet Groupes) | devient le seul organisme de l'utilisateur (LDAP aussi), la liste déroulante suit |
| organisme quitté (onglet Groupes) | `member` LDAP retiré, liste déroulante vidée |
| connexion (SIRET ProConnect) | la liste déroulante suit les groupes |

Le mapper de groupes LDAP est en mode `IMPORT` : Keycloak est maître des
organismes et des appartenances, l'extension les écrit dans le LDAP (les groupes
simples n'y vont jamais). `keycloak-ldap-sync` importe les organismes du LDAP
(marqués automatiquement : un groupe non marqué qui a un code vient du LDAP) et
leurs membres, déclare la liste déroulante et la remplit.

### Thème geOrchestra (connexion, inscription, compte)

Thème `georchestra` (`keycloak/themes/georchestra/`, copié dans l'image et monté
en volume pour l'éditer à chaud, `start-dev` désactivant le cache des thèmes) :

- **login** (parent `keycloak.v2`, PatternFly 5) : `template.ftl` ajoute un
  panneau de marque à gauche (masqué sur mobile), `register.ftl` regroupe les
  champs en sections (Compte, Identité, Organisme), prénom/nom côte à côte ;
  couleurs dans `resources/css/georchestra.css` (clair et sombre). Les autres
  pages (mot de passe oublié, OTP, mise à jour du profil…) héritent du gabarit.
- **account** (parent `keycloak.v3`, application React de Keycloak) : logo, titre
  et couleurs seulement (`theme.properties`, `resources/css/georchestra.css`).

Le lien des logos vient de `GEORCHESTRA_URL` (variable du service keycloak).
Realm : `loginTheme`/`accountTheme` = `georchestra`, inscription et mot de passe
oublié activés, français par défaut (+ anglais).

**Organisme à l'inscription** : l'attribut `organization` est éditable par
l'utilisateur (liste déroulante du formulaire d'inscription), puis verrouillé par
le validateur `georchestra-organization-locked` de l'extension : hors contextes
admin, inscription et revue IdP, un utilisateur qui a déjà un organisme ne peut
plus le changer (message `organizationLocked`). Les permissions Keycloak ne
dépendent pas du contexte, d'où ce validateur. À l'inscription (événement
`REGISTER`) et à la mise à jour du profil (`UPDATE_PROFILE`), le groupe
d'organisme suit l'attribut.

### Persistance

La base de Keycloak (H2 de dev) est conservée dans le volume `keycloak_data`. Le
realm n'est importé depuis `keycloak/georchestra-realm.json` que s'il n'existe
pas encore : pour appliquer une modification de ce fichier, soit la reporter par
l'API admin (ou la console), soit supprimer le volume (`docker compose down -v`,
ou `docker volume rm poc-keycloak_keycloak_data`).

### ProConnect

Keycloak sert d'intermédiaire avec ProConnect (environnement d'intégration,
`https://fca.integ01.dev-agentconnect.fr/api/v2`) : la page de connexion
Keycloak affiche un bouton « ProConnect ».

- client : application de test, `client_secret_post`, scopes
  `openid given_name usual_name email uid siret organization_label`
- l'application ProConnect doit déclarer ces URL :
  - redirect URI : `http://localhost:8180/realms/georchestra/broker/proconnect/endpoint`
  - post logout redirect URI : `http://localhost:8180/realms/georchestra/broker/proconnect/endpoint/logout_response`
- mappers : identifiant = email (en minuscules), prénom = `given_name`, nom =
  `usual_name`, email = `email`, et le rôle `USER` à la création du compte
- organisme depuis le SIRET : pour chaque organisme LDAP qui a un SIRET
  (`orgUniqueId` de son entrée `o=<ORG>`), `keycloak-ldap-sync` tient à jour un
  mapper ProConnect « Advanced Claim to Group » (claim `siret` = le SIRET → le
  groupe de l'organisme, écrit dans le LDAP). Mode de synchro `FORCE` : à chaque
  connexion ProConnect, l'utilisateur rejoint l'organisme de son SIRET et quitte
  les autres organismes liés à un SIRET. Relancer
  `docker compose run --rm keycloak-ldap-sync` après avoir ajouté ou modifié un
  SIRET dans le LDAP.
- un nouvel utilisateur ProConnect est créé dans Keycloak, donc dans le LDAP
  (fédération `WRITABLE`). Si son SIRET ne correspond à aucun organisme, il n'en
  a pas : lui en attribuer un ensuite. Un utilisateur dont l'email existe déjà
  (compte LDAP) se voit proposer de lier les deux comptes.

### Clients

**`georchestra-gateway`** est le client de la gateway. Ses jetons portent :
- `roles` : les rôles du realm, avec le préfixe `ROLE_` attendu par les règles
  d'accès de la gateway et par les backends ;
- `organization` : le code de l'organisme de l'utilisateur (attribut
  `organization`, la liste déroulante « Organisme »), utilisé par la gateway
  pour `sec-org` ;
- `group_roles` : un `ROLE_GRP_<CODE>` par groupe de l'utilisateur (organisme
  ou non ; le membre d'un sous-groupe reçoit aussi le rôle des groupes parents),
  produit par le mapper « geOrchestra group roles » de l'extension. La gateway
  les ajoute à `sec-roles` (`roles.json.path` dans `security.yaml`) et GeoServer
  prend ses rôles dans cet en-tête : les règles « données » de datafeeder sur un
  groupe visent ce rôle.

**`geonetwork`** et **`datafeeder`** sont des clients à compte de service,
utilisés par GeoNetwork et datafeeder pour lire l'annuaire par l'API admin de
Keycloak. Leurs comptes de service ont les rôles `realm-management`
`view-users`, `query-groups` et `view-realm`.

### URL

Le navigateur joint Keycloak sur `localhost:8180`, les conteneurs sur
`keycloak:8080`. Keycloak tourne avec `KC_HOSTNAME=http://localhost:8180` et
`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`. La gateway déclare les endpoints
explicitement au lieu d'utiliser la découverte par `issuer-uri`
([`datadir/gateway/security.yaml`](datadir/gateway/security.yaml)) :
- les redirections du navigateur (`authorization-uri`, `end-session-uri`) vont
  vers `localhost:8180` ;
- les appels serveur à serveur (`token-uri`, `jwk-set-uri`, `user-info-uri`)
  vont vers `keycloak:8080`.

`end-session-uri` est une extension de la gateway : `GET /logout` ferme aussi la
session Keycloak (déconnexion initiée par le client).

## Gateway

La configuration est dans [`datadir/gateway/security.yaml`](datadir/gateway/security.yaml).

- Les utilisateurs se connectent par Keycloak. Il n'y a pas de formulaire LDAP
  (`disableLdapFormLogin`) : `/login` redirige directement vers Keycloak.
- `disableUidTransformation: keycloak` : les identifiants restent ceux du LDAP
  (`testadmin`, pas `keycloak_testadmin`).
- `createNonExistingUsersInLDAP: false` : les comptes sont déjà dans le LDAP.
- Rôles : claims `roles` et `group_roles` (`ROLE_GRP_<CODE>`).
- La connexion LDAP est gardée pour le HTTP Basic. Datafeeder l'utilise pour
  appeler GeoServer à travers la gateway avec `testadmin`/`testadmin`.
- GeoNetwork reçoit les en-têtes geOrchestra habituels (`sec-proxy`, `sec-user`,
  `sec-orgname`).

## GeoNetwork

L'image est construite depuis la branche geonetwork
`feat/keycloak-accounts-source` (`GEONETWORK_IMAGE`, par défaut
`georchestra/geonetwork:keycloak-accounts-source`). GeoNetwork fonctionne comme
dans tout geOrchestra : il authentifie par les en-têtes `sec-*` envoyés par la
gateway et synchronise les comptes en tâche de fond. Seule la source des comptes
change, dans `datadir/geonetwork/geonetwork.properties` :

```
georchestra.accounts.source=keycloak          # console (défaut) ou keycloak
georchestra.keycloak.url=http://keycloak:8080
georchestra.keycloak.realm=georchestra
georchestra.keycloak.clientId=geonetwork
georchestra.keycloak.clientSecret=geonetwork-secret
georchestra.keycloak.orgGroupPath=            # vide : les organismes sont les groupes de premier niveau
georchestra.keycloak.orgNameAttribute=o       # attribut de groupe portant le nom long
georchestra.keycloak.orgMailAttribute=mail
```

Tous les groupes, organismes ou non, sont synchronisés
(`georchestra.keycloak.syncAllGroups=true`, avec `geonetwork.syncMode=role_per_org`) :

- chaque groupe Keycloak de premier niveau est un groupe GeoNetwork, nommé
  d'après son code, avec son nom pour libellé ;
- l'utilisateur est membre du groupe de son organisme, avec le profil donné par
  ses rôles `GN_*` (`geonetwork.profiles.rolemappings.*`) ;
- et de ses autres groupes en RegisteredUser (le repository lui donne un rôle
  `<CODE>:USER` par groupe).

Autres réglages :
- **`geonetwork.scheduled.*` :** une synchronisation complète toutes les 60 s
  par défaut. Elle crée, met à jour et supprime utilisateurs et groupes.

Un utilisateur inconnu de GeoNetwork est aussi synchronisé à sa première requête.

## Datafeeder

Le backend datafeeder vient de la branche `feat/keycloak-directory` de
`georchestra/datafeeder`. Il tourne avec :

- `DIRECTORY_PROVIDER=keycloak` : organismes, groupes, rôles et noms affichés
  des utilisateurs viennent de l'API admin de Keycloak au lieu de la console.
  Les règles de partage stockent les ID des groupes Keycloak.
- les **métadonnées** peuvent être partagées avec tous les groupes (organismes
  et groupes simples), et un utilisateur voit ce qui est partagé avec son
  organisme ou n'importe lequel de ses groupes. Un groupe pas encore synchronisé
  par GeoNetwork y est créé par datafeeder quand une règle en a besoin
  (GeoNetwork le relie au groupe Keycloak à sa synchronisation suivante).
- les **données** sont partagées avec les mêmes groupes : le panneau « Accès aux
  données » liste les groupes, et une règle sur un groupe écrit
  `ROLE_GRP_<CODE>` dans les règles de couche GeoServer (lecture comme écriture,
  en attendant les profils par groupe). Ces rôles n'existent pas dans le rôle
  service de GeoServer (LDAP) : ils viennent seulement de `sec-roles`, et
  n'apparaissent pas dans l'écran Data security de GeoServer.
- `KEYCLOAK_*` : paramètres de connexion à Keycloak.

GeoServer et GeoNetwork sont appelés à travers la gateway en HTTP Basic
(`testadmin`), comme d'habitude.

## Limites connues

- **Les ID Keycloak servent d'identifiants.** Les liens de groupes GeoNetwork et
  les règles de partage datafeeder stockent des ID Keycloak. Une instance migrée
  depuis la console doit faire réassocier ses liens et ses règles.
- **Les anciennes règles « données » sur des rôles** ne sont plus résolues avec
  l'annuaire Keycloak : elles sont à refaire sur des groupes.
- **La déconnexion finit sur la page de connexion Keycloak.** Après la
  déconnexion, Keycloak renvoie vers `/login?logout`, qui redirige vers
  Keycloak. Cette redirection est codée en dur dans la gateway.
- **Datafeeder tourne en mode local-executor.** Les traitements s'exécutent dans
  le conteneur backend. C'est réservé aux démonstrations.
- **La configuration vient de georchestra/docker.** `datadir/` et `envs/` sont
  copiés depuis [georchestra/docker](https://github.com/georchestra/docker),
  réduits aux services utilisés ici.
