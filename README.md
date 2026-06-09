# Agence de Location de Voitures

Application web de gestion d'une agence de location de voitures. Mini-projet
Spring Boot MVC realise en 4eme annee Genie Logiciel (2025-2026), Pr. Saoudi
Haythem.

L'application couvre le cycle complet d'une location : catalogue public, depot
de demande, validation par l'admin, paiement en especes au bureau, remise des
cles, restitution, avis post-location et gestion des reclamations.

## Stack technique

| Couche | Technologie |
|--------|-------------|
| Framework | Spring Boot 3.3.4 (Java 17) |
| Web / Templates | Spring MVC + Thymeleaf + thymeleaf-extras-springsecurity6 |
| Persistance | Spring Data JPA + Hibernate (MySQL 8 via XAMPP) |
| Securite | Spring Security 6 (form login, BCrypt, CSRF) |
| Mail | Spring Mail (Gmail SMTP) |
| PDF | Flying Saucer + OpenPDF (factures) |
| UI | Bootstrap 5 + FontAwesome 6 + Flatpickr |
| Outils | Lombok, Spring Boot DevTools, PlantUML (diagrammes) |

## Fonctionnalites couvertes

**EF1 - Modele de donnees** : 12 entites, associations `@OneToOne`,
`@OneToMany`, `@ManyToMany` et `@ManyToOne`.

**EF2 - Upload d'images** : photo de voiture, logo de marque, photo de profil,
permis et CIN, photo jointe a une reclamation, photo du second conducteur.

**EF3 - Pagination + recherche** : catalogue client et listes admin avec
filtres multi-criteres et tri serveur-side.

**EF4 - Dashboard KPIs** : aggregations JPQL cote SQL, top 5 voitures les
plus louees, repartition par categorie.

**EF5 - Validation** : Jakarta Bean Validation (`@NotBlank`, `@Email`,
`@Size`, `@DecimalMin`, validators personnalises au niveau service).

### Workflow location

```
EN_ATTENTE -> ACCEPTEE -> PAYEE -> EN_COURS -> TERMINEE
              \-> REFUSEE
              \-> EXPIREE (48h sans paiement)
```

- `EN_ATTENTE` : demande deposee par le client
- `ACCEPTEE` : admin valide la demande, mail + facture PDF envoyes, 48h
  pour payer
- `REFUSEE` : motif obligatoire, mail au client
- `PAYEE` : paiement en especes valide au bureau
- `EN_COURS` : admin remet les cles (action manuelle)
- `TERMINEE` : admin recupere les cles (action manuelle)
- `EXPIREE` : delai de 48h depasse (scheduler 15 min)

### Autres fonctionnalites

- Inscription avec activation par lien email
- Mot de passe oublie / reinitialisation (token UUID valable 1h, usage unique,
  anti-enumeration)
- Bannissement par numero de permis (pas par compte) : un banni peut se
  connecter mais en lecture seule
- Avis client uniquement apres `TERMINEE`, reclamation uniquement pendant
  `EN_COURS`
- Decouplage FK a la suppression : supprimer une voiture, un client ou une
  location preserve les locations / avis / reclamations associes (FK nullable)
- Catalogue filtrable par marque, categorie, prix, transmission, carburant,
  equipements (AND multi-select) et periode de disponibilite
- Calendrier flatpickr avec dates indisponibles barrees et dates passees
  masquees
- Apercu live du prix total a la creation de location

## Prerequis

- JDK 17+
- Maven 3.9+
- MySQL 8 (via XAMPP ou autonome) - port 3306, utilisateur `root` sans mot
  de passe par defaut

## Demarrage

1. **Cloner le depot** :
   ```bash
   git clone https://github.com/medabrd/springBoot-systemDeLocationsVoitures.git
   cd springBoot-systemDeLocationsVoitures
   ```

2. **Demarrer MySQL** (via le panel XAMPP par exemple). Aucune action sur la
   base : Hibernate cree le schema au premier boot
   (`createDatabaseIfNotExist=true`).

3. **Configurer les secrets locaux**. Le fichier `application.properties`
   committe ne contient aucun secret. Les identifiants Gmail sont lus depuis
   un fichier local gitignore :
   ```bash
   cd src/main/resources
   cp application-local.properties.example application-local.properties
   ```
   Puis editer `application-local.properties` et y mettre :
   ```properties
   spring.mail.username=ton.email@gmail.com
   spring.mail.password=ton-mot-de-passe-application-gmail
   app.mail.from=ton.email@gmail.com
   ```
   > Pour generer un mot de passe d'application Gmail :
   > https://myaccount.google.com/apppasswords (necessite la 2FA activee
   > sur le compte Google).
   >
   > Si le fichier `application-local.properties` est absent, l'application
   > demarre quand meme (grace a `spring.config.import=optional:...`) mais
   > les envois d'emails echoueront silencieusement.

4. **Lancer l'application** :
   ```bash
   mvn spring-boot:run
   ```
   Ou via l'IDE : executer la classe `LocationVoituresApplication`.

Au premier demarrage, Hibernate cree le schema et deux initialiseurs
peuplent la base :
- `DataInitializer` : compte admin + 12 marques + 6 categories + 12
  equipements
- `TestVoituresInitializer` : 10 voitures de test avec photos
  telechargees depuis `loremflickr.com` (peut prendre 30-60s)

L'application est ensuite accessible sur **http://localhost:8080**.

## Comptes par defaut

| Role | Email | Mot de passe |
|------|-------|--------------|
| Admin | `mohamed.abroud@polytechnicien.tn` | `admin123` |

Les comptes client se creent via `/auth/inscription` (avec lien
d'activation par email).

## Structure du projet

```
src/main/java/com/example/locvoitures/
  config/         Spring config (Security, DataInitializer, etc.)
  controller/    Controleurs MVC (admin/, client/, public)
  dto/           Formulaires et DTOs
  entity/        Entites JPA (12)
  enumeration/   Enums (Role, StatutLocation, StatutVoiture, etc.)
  exception/     Exceptions metier
  repository/    Spring Data JPA repos
  service/       Logique metier

src/main/resources/
  templates/     Vues Thymeleaf (admin/, client/, catalogue/, auth/,
                 email/, fragments/, erreur/, pdf/)
  static/css/    Feuille de style
  application.properties

uploads/         Fichiers uploades (cree au boot)
diagrammes/      PlantUML + PNG (classes, use cases admin/client)
```

## Diagrammes

Disponibles dans `diagrammes/` :
- `DiagrammeClasses.png` - diagramme de classes complet
- `DiagrammeUseCaseClient.png` - use cases cote client
- `DiagrammeUseCaseAdmin.png` - use cases cote admin

Pour les regenerer : `java -jar plantuml.jar diagrammes/*.puml`.

## URL principales

| URL | Acces | Description |
|-----|-------|-------------|
| `/auth/login` | Public | Connexion |
| `/auth/inscription` | Public | Inscription client |
| `/auth/mot-de-passe-oublie` | Public | Demande de reinit |
| `/catalogue` | CLIENT | Catalogue avec filtres |
| `/catalogue/{id}` | CLIENT | Detail voiture |
| `/client/locations` | CLIENT | Mes locations |
| `/client/profil` | CLIENT | Mon profil |
| `/admin/dashboard` | ADMIN | KPIs |
| `/admin/voitures` | ADMIN | Gestion flotte |
| `/admin/locations` | ADMIN | Gestion locations |
| `/admin/utilisateurs` | ADMIN | Gestion clients + bannissement |
| `/admin/reclamations` | ADMIN | Traitement reclamations |
| `/admin/avis` | ADMIN | Moderation avis |

## Limites connues

- Pas de tests unitaires ni d'integration
- Pas de migrations versionnees (Flyway/Liquibase) - schema en
  `ddl-auto=update`
- Secrets en fichier local gitignore (pas de coffre type Vault / AWS Secrets
  Manager) - suffisant pour un mini-projet pedagogique
- Suppression d'entites en cascade decouplee (FK nullables) - approche
  techniquement fonctionnelle mais pollue les templates avec des
  `th:if` defensifs ; un soft-delete aurait ete plus elegant
- Calendrier admin natif HTML5 vs flatpickr cote client - inconsistance UI

## Licence

Mini-projet pedagogique, usage academique uniquement.
