# Diagrammes UML — Application de Location de Voitures

Ce dossier contient les diagrammes UML du projet au format **PlantUML**.

## Fichiers

| Fichier | Description |
|---|---|
| `diagramme_classes.puml` | Modèle de données (12 entités + 7 enums) |
| `diagramme_usecase_client.puml` | Cas d'utilisation côté Client / Visiteur / Client banni |
| `diagramme_usecase_admin.puml` | Cas d'utilisation côté Administrateur |

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
# Nécessite Java + plantuml.jar
java -jar plantuml.jar diagramme_classes.puml
java -jar plantuml.jar diagramme_usecase_client.puml
java -jar plantuml.jar diagramme_usecase_admin.puml
```

## Synthèse du modèle

### Associations obligatoires (cahier des charges)
- **`@OneToMany`** : Marque→Voiture, Categorie→Voiture, Client→Location, Location→Reclamation, Utilisateur→PermisBanni
- **`@ManyToMany`** : Voiture ↔ Equipement (bidirectionnelle)
- **`@OneToOne`** : Utilisateur↔Client, Voiture↔DetailsVoiture, Location↔Avis, Location↔ConducteurSecondaire

### Acteurs
- **Visiteur** : navigation publique + inscription
- **Client** : compte actif, peut louer
- **Client Banni** : connexion en lecture seule
- **Administrateur** : gestion complète
