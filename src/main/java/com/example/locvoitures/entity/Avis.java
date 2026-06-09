/*
 * ============================================================================
 * Avis - Note et commentaire deposes par un client apres une location terminee
 * ============================================================================
 *
 * Role :
 *   Represente l'evaluation post-location laissee par le client. Note de 1 a 5
 *   etoiles + texte libre. Un seul avis par location (relation 1-1).
 *
 * Persistance :
 *   Table SQL "avis" generee par Hibernate. Clef etrangere location_id
 *   pointant vers location.id. La FK est nullable depuis la refonte du
 *   decuplage des suppressions : si l'admin supprime une location, l'avis
 *   est conserve avec location_id = NULL (perd la reference, garde la note).
 *
 * Cycle de vie :
 *   1. Cree par AvisService.creerAvis() depuis ClientLocationController
 *      apres soumission du formulaire AvisForm (DTO).
 *   2. Affiche dans client/locations/detail.html (mes locations) et
 *      admin/avis/liste.html (moderation).
 *   3. Note moyenne agregee dans DashboardService.buildDashboard()
 *      via AvisRepository.noteMoyenneGlobale().
 *   4. Supprime par AdminAvisController via AvisService.supprimerAvis()
 *      (moderation admin).
 *   5. Conserve si la location est supprimee (FK location_id passee a null
 *      par LocationService.supprimerParAdmin()).
 *
 * Garde metier :
 *   - Seule une location en statut TERMINEE peut recevoir un avis
 *     (verifie dans AvisService.creerAvis)
 *   - Un seul avis par location (unique constraint via unique=true sur
 *     le JoinColumn)
 *   - Si rappel d'avis non depose, le scheduler envoyerRappelsAvis envoie
 *     un mail quotidien a 9h (LocationScheduler)
 *
 * Templates et autres references :
 *   - templates/client/locations/detail.html : affiche l'avis depose
 *     ou le bouton "Donner mon avis"
 *   - templates/client/locations/avis.html : formulaire de saisie
 *   - templates/admin/avis/liste.html : moderation admin
 *   - templates/admin/locations/detail.html : avis visible cote admin
 *   - templates/email/rappel-avis.html : mail de relance
 *
 * Annotations Lombok importees :
 *   - @Getter, @Setter : genere les accessors pour tous les champs
 *   - @NoArgsConstructor : constructeur sans args (requis par JPA)
 *   - @AllArgsConstructor : constructeur avec tous les champs (utile @Builder)
 *   - @Builder : pattern builder (Avis.builder().note(5)....build())
 *   - @ToString(exclude="location") : evite la boucle infinie via Location.avis
 * ============================================================================
 */
package com.example.locvoitures.entity;

// Imports JPA standard (jakarta.* depuis Spring Boot 3.x qui est passe a Jakarta EE 9+)
import jakarta.persistence.*;
// @Entity, @Table, @Id, @GeneratedValue, @Column, @OneToOne, @JoinColumn, @PrePersist, FetchType, GenerationType

// Validations Bean Validation (Jakarta)
import jakarta.validation.constraints.*;
// @Min, @Max, @NotBlank, @Size declenchees a la deserialisation des DTOs et
// lors d'un save() Hibernate (verification cote ORM).

// Lombok : reduit le boilerplate (getters, setters, constructeurs, toString)
import lombok.*;
// @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor, @Builder, @ToString

// API date moderne de Java 8+
import java.time.LocalDateTime;

/**
 * Entity         indique a Hibernate de mapper cette classe a une table SQL
 * Table          surcharge le nom par defaut (sinon "Avis" avec majuscule)
 * Getter/@Setter Lombok genere getNote(), setNote(), getCommentaire()...
 * NoArgsConstructor Hibernate exige un constructeur sans arguments pour
 *                    instancier l'entite a partir d'une row SQL
 * AllArgsConstructor + @Builder permettent l'usage
 *                    Avis.builder().note(5).commentaire("Top").build()
 * ToString(exclude="location") empeche la stack overflow si Location.toString()
 *                    appelle Avis.toString() (cycle bidirectionnel)
 */
@Entity
@Table(name = "avis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "location")
public class Avis {

    // ------------------------------------------------------------------------
    // Identifiant primaire
    // ------------------------------------------------------------------------
    /**
     * Id              clef primaire SQL
     * GeneratedValue(strategy=IDENTITY) delegue la generation a la colonne
     *                  AUTO_INCREMENT de MySQL (chaque insert produit un id
     *                  unique sans qu'on doive le specifier)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------------------
    // Note (1 a 5)
    // ------------------------------------------------------------------------
    /**
     * Min/@Max contraignent la valeur cote Java (verification automatique
     *           via @Valid dans les controllers et lors du save())
     * Column(nullable=false) traduit la contrainte en NOT NULL cote SQL
     * int primitif : pas null possible cote Java, simplifie le code metier
     */
    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private int note;

    // ------------------------------------------------------------------------
    // Commentaire (texte libre)
    // ------------------------------------------------------------------------
    /**
     * NotBlank refuse null + chaine vide + chaine de whitespaces
     *          (plus strict que @NotEmpty qui accepte " ")
     * Size(max=500) limite la longueur cote Java
     * Column(length=500) declare VARCHAR(500) cote SQL (coherence des deux limites)
     */
    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String commentaire;

    // ------------------------------------------------------------------------
    // Date de creation (auto-renseignee par @PrePersist)
    // ------------------------------------------------------------------------
    /**
     * Column(updatable=false) empeche la modification ulterieure de la
     *         valeur via un setter -> meme un setDateCreation() suivi d'un
     *         save() n'updatera pas la colonne. Garantit l'immutabilite
     *         de cette date apres premiere persistance.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * PrePersist callback Hibernate execute juste avant le premier INSERT
     *             SQL. Permet d'initialiser dateCreation = now() si l'appelant
     *             ne l'a pas fait. Idempotent (verifie le null avant ecrasement)
     *             car on pourrait theoriquement vouloir backdater un avis en
     *             passant la valeur via le builder.
     */
    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
    }

    // ------------------------------------------------------------------------
    // Relation a Location (cote owning du OneToOne)
    // ------------------------------------------------------------------------
    /**
     * OneToOne(fetch=LAZY) : un avis appartient a au plus une location.
     *           LAZY = la location n'est chargee depuis la DB que si on
     *           accede explicitement a avis.getLocation() (evite les joins
     *           inutiles lors d'un simple findAll() d'avis).
     *
     * JoinColumn(name="location_id") : nom de la colonne FK dans la table avis.
     * unique=true : un seul avis par location au niveau SQL (UNIQUE index)
     *
     * Pas de nullable=false : la FK est nullable depuis la refonte du
     * decouplage. Si l'admin supprime la location, l'avis est conserve
     * avec location_id = NULL (cf. LocationService.supprimerParAdmin).
     * Pas de @NotNull non plus pour la meme raison.
     *
     * Cote inverse : Location.avis (mappedBy="location", non owning).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", unique = true)
    private Location location;
}
