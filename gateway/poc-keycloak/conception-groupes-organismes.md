# Groupes de partage et organismes — geOrchestra avec Keycloak

2026-11-06

## Contexte et objectif

Proposition : séparer l'**organisme** (rattachement, un seul par utilisateur) des **groupes de partage** (plusieurs par utilisateur), et faire du groupe l'unité de droits commune à GeoNetwork, GeoServer et datafeeder.

Aujourd'hui, datafeeder partage les métadonnées par organisme (groupes GeoNetwork) et les données par rôle (règles GeoServer) : deux listes et deux sémantiques différentes. Partager avec un autre organisme oblige à mettre l'utilisateur dans deux organismes, ce que geOrchestra refuse (`More than one org per user` dans la gateway).

Cible : un utilisateur peut lire ou éditer les fiches et les couches d'un groupe transverse (projet, partenariat) sans changer d'organisme, et une règle datafeeder sur un groupe a le même effet côté données et côté métadonnées.

## Modèle

Quatre notions distinctes, chacune avec un seul usage.

| Notion | Par utilisateur | Sert à | Exemple |
| --- | --- | --- | --- |
| Organisme | exactement 1 | rattachement : `sec-org`, contact des fiches, organisme propriétaire et schéma dans datafeeder | PSC |
| Groupe de partage | 0 à N | unité de partage des données et des métadonnées | projet-littoral |
| Profil dans un groupe | 1 par groupe | qui lit, édite, relit, administre dans ce groupe | éditeur de projet-littoral |
| Rôle | 0 à N | droits fonctionnels globaux, sans lien avec un groupe | `IMPORT`, `ADMINISTRATOR`, `GN_ADMIN` |

**Chaque organisme est aussi un groupe de partage**, créé par défaut : ses membres sont les utilisateurs rattachés à l'organisme. Sans groupe transverse, le comportement est donc identique à geOrchestra aujourd'hui.

Dans le groupe de son organisme, le profil d'un utilisateur vient de ses rôles globaux (`GN_EDITOR`, `GN_REVIEWER`…), comme aujourd'hui. Dans un groupe transverse, il vient du sous-groupe dont il est membre (section suivante).

## Mise en œuvre dans le POC (état actuel)

Le POC applique déjà une partie du modèle, avec une convention plus simple que les racines `/organizations` et `/groups` décrites plus bas :

- **Organisme** = groupe portant l'attribut `isOrganization=true` (« Est un organisme ») ; les autres groupes sont de simples groupes, en nombre quelconque par utilisateur.
- **Nom** = nom du groupe, seul saisi. **Code** = attribut `cn`, généré à la création et figé ; il sert d'identifiant à LDAP, GeoNetwork, GeoServer, datafeeder et `sec-org`.
- **Organisme d'un utilisateur** = liste déroulante « Organisme » de sa fiche (attribut `organization` = code), alignée avec l'appartenance par l'extension `georchestra-organization`, qui écrit aussi organismes et membres dans LDAP (mapper LDAP en mode `IMPORT`).
- **Groupes simples** : ils ont aussi un code figé (`isOrganization=false`). Tous les groupes de premier niveau sont des groupes GeoNetwork (mode `role_per_org`, `syncAllGroups`) et des groupes de partage des métadonnées dans datafeeder, qui tient compte de tous les groupes d'un utilisateur pour ses accès.
- **Données** : un mapper Keycloak de l'extension (« geOrchestra group roles ») met `ROLE_GRP_<CODE>` pour chaque groupe de l'utilisateur dans le claim `group_roles`, que la gateway ajoute à `sec-roles`. GeoServer prend ses rôles dans cet en-tête : aucun job ni customizer gateway. Datafeeder propose les mêmes groupes pour les données et les métadonnées ; une règle données sur G écrit `ROLE_GRP_<CODE>` (lecture et écriture) dans les règles de couche.
- Reste à faire : profils par groupe (sous-groupes `editors`…, puis `ROLE_GRP_<CODE>_EDITOR` pour l'écriture).

## Conventions Keycloak

Tout passe par des groupes Keycloak, rangés sous deux racines. Le nom d'un groupe (dernier segment du chemin) est unique entre les deux racines.

```
/organizations/<ORG>              organisme : 1 par utilisateur, attributs o (nom long), mail, description
/groups/<GROUPE>                  groupe transverse : membres = lecteurs
/groups/<GROUPE>/editors          éditeurs
/groups/<GROUPE>/reviewers        relecteurs
/groups/<GROUPE>/admins           administrateurs du groupe
```

**Groupe par défaut par organisme** : c'est le groupe `/organizations/<ORG>` lui-même, il n'y a rien d'autre à créer. Il est projeté partout comme un groupe de partage, au même titre qu'un `/groups/<GROUPE>`.

**Renseigner l'organisme d'un utilisateur** : liste déroulante « Organisme » de sa fiche Keycloak (attribut `organization`), ou appartenance à `/organizations/<ORG>` (onglet Groupes). L'extension Keycloak `georchestra-organization` garde les deux alignés, et rejoindre un organisme fait quitter les autres. Dans le scénario A, l'appartenance est écrite dans LDAP ; dans le scénario B, elle reste dans Keycloak.

**Un seul organisme par utilisateur** : l'extension l'impose pour les changements faits dans Keycloak (liste déroulante, onglet Groupes). Le job signale les utilisateurs déjà rattachés à plusieurs organismes ; en attendant la correction, la gateway retient le premier et journalise un avertissement.

**Un seul claim** : le mapper « Group Membership », chemin complet activé, émet `groups` (`["/organizations/PSC", "/groups/projet-littoral/editors"]`). La gateway en déduit l'organisme et les droits ; les rôles du realm restent dans le claim `roles`, préfixe `ROLE_`.

## Projection par composant

Un groupe Keycloak devient un groupe GeoNetwork et deux rôles GeoServer. Datafeeder propose la même liste de groupes pour les données et les métadonnées.

| Composant | Reçoit | Changement |
| --- | --- | --- |
| Gateway | claim `groups` | customizer : organisme = `/organizations/<ORG>` ; ajoute `ROLE_GRP_<G>` pour chaque groupe, et `ROLE_GRP_<G>_EDITOR` si l'utilisateur est éditeur, relecteur ou admin dans ce groupe |
| GeoNetwork | annuaire Keycloak (synchro périodique) | source Keycloak existante + mode `role_per_org` : chaque groupe devient un groupe GN ; `/groups/G/editors` donne le rôle `G:GN_EDITOR` (`reviewers` → `G:GN_REVIEWER`, `admins` → `G:GN_USERADMIN`, simple membre → `G:USER`) |
| GeoServer | `sec-roles` | aucun code ; un job crée les `ROLE_GRP_*` dans `roles.xml` pour l'écran Data security |
| Datafeeder | annuaire Keycloak + `sec-*` | une seule liste de groupes pour les deux panneaux ; la règle stocke l'ID du groupe Keycloak ; l'organisme reste utilisé pour le propriétaire, le contact et le schéma |

Noms de rôles GeoServer : nom du groupe en majuscules, tout caractère hors `A-Z0-9_` remplacé par `_`. La gateway, le job et datafeeder appliquent exactement la même normalisation.

| Règle datafeeder sur le groupe G | GeoNetwork (fiche) | GeoServer (couche) |
| --- | --- | --- |
| Lecture | privilèges view et download pour G | `r = ROLE_GRP_G` |
| Écriture | privilège editing pour G, effectif pour les éditeurs et plus de G | `w = ROLE_GRP_G_EDITOR` |

Résultat : « qui peut modifier ce jeu de données » a la même réponse des deux côtés, les éditeurs de G. Datafeeder reste la référence des règles sur ses couches et ses fiches : une modification faite à la main dans GeoServer ou GeoNetwork est écrasée à la synchro suivante.

## Scénario A : LDAP conservé

LDAP reste la référence des comptes, des organismes et des rôles ; Keycloak le fédère et ajoute les groupes transverses, qui n'existent que dans Keycloak.

| Élément | Source | Mise en œuvre |
| --- | --- | --- |
| Comptes, mots de passe | LDAP `ou=users` | fédération Keycloak `WRITABLE`, comme dans le POC |
| Organismes | LDAP `ou=orgs` | mapper de groupes vers `/organizations` (aujourd'hui `/`), mode `LDAP_ONLY` : l'appartenance se gère dans Keycloak et s'écrit dans LDAP |
| Rôles | LDAP `ou=roles` | mapper de rôles vers les rôles du realm, comme dans le POC |
| Groupes transverses | Keycloak | groupes locaux sous `/groups`, base Keycloak persistante (PostgreSQL) |
| Comptes techniques | LDAP | la gateway garde LDAP pour le HTTP Basic (datafeeder vers GeoServer et GeoNetwork) |

Création d'un organisme : Keycloak ne crée que l'entrée `cn=<ORG>`, sans l'entrée `o=<ORG>` qu'attend geOrchestra. On ne crée donc jamais d'organisme depuis Keycloak ; un petit script LDAP remplace la console pour cette opération (création des deux entrées).

Avantage : aucune migration de comptes, et les composants qui lisent encore LDAP (rôles GeoServer, MapStore) continuent de fonctionner.

## Scénario B : sans LDAP

Keycloak devient la seule référence : comptes, organismes, rôles et groupes. Il lui faut une base PostgreSQL persistante et sauvegardée.

| Élément | Source | Mise en œuvre |
| --- | --- | --- |
| Comptes, mots de passe | Keycloak | utilisateurs locaux |
| Organismes | Keycloak | groupes `/organizations/<ORG>`, attributs `o` et `mail` saisis dans l'admin Keycloak |
| Rôles | Keycloak | rôles du realm |
| Groupes transverses | Keycloak | identique au scénario A |
| Rôles GeoServer | job | le rôle service LDAP disparaît ; le job écrit les rôles du realm et les `ROLE_GRP_*` dans le rôle service XML |
| Comptes techniques | à définir | sans LDAP, la gateway n'a plus de HTTP Basic : datafeeder doit appeler GeoServer et GeoNetwork autrement |

Migration depuis LDAP : fédérer une dernière fois, synchroniser, puis délier les utilisateurs pour en faire des comptes locaux. Les mots de passe restent dans LDAP : les utilisateurs doivent les réinitialiser, sauf import des empreintes par une extension Keycloak.

À vérifier avant de retenir ce scénario : tout composant qui lit encore LDAP directement, en particulier MapStore.

## Points ouverts et étapes

Points ouverts :

- **Écran Data security de GeoServer (scénario A)** : il liste les rôles du rôle service actif, qui est LDAP. Les `ROLE_GRP_*` du rôle service XML n'y apparaîtraient pas sans changer de service actif.
- **Comptes techniques (scénario B)** : appel direct des backends avec des en-têtes `sec-*` sur le réseau interne, ou gateway acceptant un jeton Bearer (développement à faire).
- **Migration des identifiants** : les règles datafeeder existantes portent des UUID console, les liens de groupes GeoNetwork aussi ; il faut les réassocier aux ID Keycloak par nom.

Étapes, dans le POC :

1. Keycloak : racines `/organizations` et `/groups`, sous-groupes de profil, mapper `groups` en chemin complet.
2. Gateway : customizer qui déduit l'organisme et ajoute les `ROLE_GRP_*`.
3. GeoNetwork : la source Keycloak traduit les sous-groupes en rôles `G:GN_*` ; passage en `role_per_org`.
4. GeoServer : job qui crée les `ROLE_GRP_*`.
5. Datafeeder : une seule liste de groupes, traduction des règles vers GeoNetwork et GeoServer.
6. Scénario B seulement : Keycloak sur PostgreSQL, comptes techniques, import des comptes.
