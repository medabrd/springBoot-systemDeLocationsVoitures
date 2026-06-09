# Diagrammes UML — Application de Location de Voitures

Ce dossier contient les diagrammes UML du projet au format **PlantUML**,
avec leurs rendus PNG pré-générés.

## Fichiers

| Source PlantUML | Rendu PNG | Description |
|---|---|---|
| `diagramme_classes.puml` | `DiagrammeClasses.png` | Modèle de données (13 entités + 7 enums) |
| `diagramme_usecase_client.puml` | `DiagrammeUseCaseClient.png` | Cas d'utilisation côté Client / Visiteur / Client banni |
| `diagramme_usecase_admin.puml` | `DiagrammeUseCaseAdmin.png` | Cas d'utilisation côté Administrateur |

Les PNG sont versionnés pour pouvoir être consultés sans avoir Java
installé. Si vous modifiez un `.puml`, regénérez le PNG correspondant
(voir Option 4 ci-dessous) et committez les deux fichiers ensemble.

## Comment visualiser les diagrammes

### Option 1 — En ligne (le plus rapide)
1. Ouvrir https://www.plantuml.com/plantuml/uml/
2. Copier-coller le contenu du fichier `.puml`
3. Le diagramme s'affiche immédiatement (image téléchargeable en PNG/SVG)

### Option 2 — VS Code
1. Installer l'extension **PlantUML** (jebbs.plantuml)
2. Ouvrir le fichier `.puml`
3. `Alt+D` pour prévisualiser
4. Clic droit → **Export Current Diagram** pour générer PNG/SVG/PDF

### Option 3 — IntelliJ IDEA
1. Installer le plugin **PlantUML Integration**
2. Ouvrir le fichier `.puml`
3. Le rendu apparaît automatiquement à droite

### Option 4 — Ligne de commande
```bash
# Nécessite Java + plantuml.jar (téléchargeable sur plantuml.com,
# gitignoré ici — voir .gitignore racine).
java -jar plantuml.jar diagramme_classes.puml
java -jar plantuml.jar diagramme_usecase_client.puml
java -jar plantuml.jar diagramme_usecase_admin.puml
```

## Synthèse du modèle (diagramme de classes)

### Hiérarchie d'héritage (JPA `@Inheritance(JOINED)`)

`Utilisateur` est **abstraite** et porte les champs communs d'authentification
(email, mot de passe, tokens, dates). Les deux sous-classes concrètes :

- `Client` — utilisateur final loueur (téléphone, CIN, permis, locations)
- `Admin` — gestionnaire interne de l'agence (poste occupé, date d'embauche)

Chaque sous-classe a sa propre table SQL partageant la clé primaire avec
`utilisateur` (stratégie JOINED).

### Associations obligatoires (cahier des charges EF1)

| Type | Relations |
|---|---|
| `@OneToOne` | `Client ↔ Permis` (agrégation, le permis survit à la suppression du client pour préserver le ban), `Voiture ↔ DetailsVoiture` (composition, cascade=ALL), `Location ↔ Avis` (FK nullable côté Avis), `Location ↔ ConducteurSecondaire` (composition, cascade=ALL) |
| `@OneToMany` / `@ManyToOne` | `Marque → Voiture`, `Categorie → Voiture`, `Client → Location`, `Voiture → Location`, `Location → Reclamation`, `Permis → ConducteurSecondaire`, `Admin → Permis` (l'admin qui a prononcé le ban) |
| `@ManyToMany` | `Voiture ↔ Equipement` (bidirectionnelle, table de jointure `voiture_equipement`) |

### Sémantique des losanges (UML / cycle de vie JPA)

| Symbole | Sens | Mapping JPA |
|---|---|---|
| `*--` (losange plein) | **Composition** : la partie ne survit pas au tout | `cascade=ALL` + `orphanRemoval=true` |
| `o--` (losange vide) | **Agrégation** : la partie survit au tout | `cascade=PERSIST+MERGE` (sans REMOVE) |
| `--` (ligne simple) | Association classique | Pas de cascade destructive |

### FK nullables (découplage à la suppression)

Plusieurs FK sont volontairement nullables pour préserver l'historique
quand une entité parente est supprimée :

- `Location.client_id` → suppression d'un client conserve ses locations
- `Location.voiture_id` → suppression d'une voiture conserve son historique
- `Avis.location_id` → suppression d'une location conserve l'avis
- `Reclamation.location_id` → suppression d'une location conserve les réclamations
- `Permis.admin_bannisseur_id` → suppression d'un admin conserve les bans qu'il a prononcés

## Synthèse des cas d'usage

### Acteurs
- **Visiteur** : navigation publique du catalogue + inscription
- **Client** : compte actif, peut soumettre une demande de location, payer, déposer un avis ou une réclamation
- **Client banni** (par numéro de permis) : connexion en lecture seule, location interdite
- **Administrateur** : gestion complète (flotte, locations, utilisateurs, avis, réclamations, bannissements)
