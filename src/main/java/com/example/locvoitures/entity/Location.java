/*
 * ============================================================================
 * Location - Reservation/location d'un vehicule par un client
 * ============================================================================
 *
 * Role :
 *   Entite centrale du domaine. Represente le cycle de vie complet d'une
 *   reservation : EN_ATTENTE -> ACCEPTEE (delai de paiement pose) -> PAYEE
 *   -> EN_COURS (debut de location) -> TERMINEE (restitution). Branches
 *   d'echec : REFUSEE (par admin) ou EXPIREE (paiement non recu dans les
 *   delais).
 *
 * Utilise par :
 *   - entity/Client.java : OneToMany inverse
 *   - entity/Voiture.java : OneToMany inverse
 *   - entity/Avis.java : OneToOne (FK nullable cote Avis)
 *   - entity/Reclamation.java : ManyToOne (FK nullable cote Reclamation)
 *   - entity/ConducteurSecondaire.java : OneToOne owning cote Location
 *   - service/LocationService : tout le workflow metier (soumettre, accepter,
 *     refuser, payer, demarrer, terminer, expirer)
 *   - service/LocationScheduler : passage automatique aux statuts terminaux
 *     (EXPIREE si delai depasse, EN_COURS au jour J, TERMINEE apres dateFin)
 *   - controller/ClientLocationController : ecrans client
 *   - controller/AdminLocationController : ecrans admin
 *   - service/PdfService : facture PDF
 *   - service/EmailService : notifications de transition
 *
 * Indexes :
 *   - idx_location_voiture_dates : accelere la requete de chevauchement
 *     (WHERE voiture_id=? AND dateDebut <= ? AND dateFin >= ?)
 *   - idx_location_statut : accelere le filtrage admin par statut
 *
 * FK nullables (decouplage) :
 *   client_id et voiture_id sont nullables. Cela permet de supprimer
 *   l'utilisateur ou le vehicule sans casser l'historique des locations
 *   (templates affichent "Client supprime" / "Vehicule supprime").
 *
 * Cascade :
 *   - ConducteurSecondaire en CascadeType.ALL + orphanRemoval=true :
 *     cree/supprime automatiquement avec la location
 *   - Avis et Reclamations PAS en cascade : preserves a la suppression
 *     de la location pour audit
 * ============================================================================
 */
package com.example.locvoitures.entity;

// Enum de statut metier
import com.example.locvoitures.enumeration.StatutLocation;

// JPA core (entites, relations, indexes)
import jakarta.persistence.*;

// Validation Bean (wildcard pour @NotNull, @Size)
import jakarta.validation.constraints.*;

// Lombok
import lombok.*;

// BigDecimal pour le prix (precision financiere)
import java.math.BigDecimal;
// LocalDate pour les dates de location (jour entier)
// LocalDateTime pour les timestamps techniques (creation, expiration)
import java.time.LocalDate;
import java.time.LocalDateTime;
// Collections pour les Set<Reclamation>
import java.util.HashSet;
import java.util.Set;

/**
 * Entity / @Table : table "location"
 * Table.indexes : creation d'index secondaires en SQL (visibles dans
 *                  SHOW INDEX FROM location)
 * ToString(exclude=...) : evite la boucle bidirectionnelle Location->Client->
 *                          Locations et les lazy init en serialisation
 */
@Entity
@Table(name = "location", indexes = {
        @Index(name = "idx_location_voiture_dates", columnList = "voiture_id, dateDebut, dateFin"),
        @Index(name = "idx_location_statut", columnList = "statut")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"client", "voiture", "avis", "conducteurSecondaire", "reclamations"})
public class Location {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Date de debut de location (jour de prise en charge).
     * Pas de @Future ici : la validation temporelle se fait au niveau du DTO
     * (DemandeLocationForm) car une location en base peut avoir dateDebut
     * dans le passe une fois EN_COURS ou TERMINEE.
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate dateDebut;

    /**
     * Date de fin de location (jour de restitution prevu).
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate dateFin;

    /**
     * Nombre de jours factures = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1.
     * Calcule au moment de la creation par LocationService.
     */
    @Column(nullable = false)
    private int nombreJours;

    /**
     * Prix total = nombreJours * prixJournalier de la voiture.
     * BigDecimal precision=10, scale=2 : jusqu'a 99999999.99.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prixTotal;

    /**
     * Statut courant. @Builder.Default = EN_ATTENTE à la creation via builder.
     * Enumerated(EnumType.STRING) : stocke "EN_ATTENTE" en VARCHAR (robuste).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutLocation statut = StatutLocation.EN_ATTENTE;

    /**
     * Motif du refus saisi par l'admin si REFUSEE. Affiche au client.
     */
    @Size(max = 500)
    @Column(length = 500)
    private String motifRefus;

    /**
     * Posee a l'acceptation : dateExpiration = now + delaiPaiement.
     * Le scheduler passe la location a EXPIREE si elle reste ACCEPTEE
     * apres ce moment.
     */
    private LocalDateTime dateExpiration;

    /**
     * Posee au moment du paiement (passage ACCEPTEE -> PAYEE).
     */
    private LocalDateTime datePaiement;

    /**
     * Timestamp de creation de la demande. updatable=false : ne change jamais
     * apres l'insertion (defense-en-profondeur).
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Flag indiquant si le mail de rappel d'avis a deja ete envoye apres
     * TERMINEE (evite les doublons par le scheduler).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean avisRappelEnvoye = false;

    /**
     * PrePersist : callback JPA invoque AVANT l'INSERT.
     * Initialise dateCreation a now() si pas deja pose.
     */
    @PrePersist
    protected void onCreate() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Client owner de la location.
     * Nullable car la suppression d'un compte client doit preserver
     * l'historique (les locations restent visibles, le client devient null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    /**
     * Voiture louee.
     * Nullable pour la meme raison : permettre la suppression d'un vehicule
     * de la flotte sans casser l'historique.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voiture_id")
    private Voiture voiture;

    /**
     * Avis associe (au plus un). Cote inverse : Avis porte la FK location_id.
     * Pas de cascade : si on supprime la location, l'avis reste (FK nullable
     * cote Avis -> il devient orphelin mais reste visible cote admin).

     * Pas de fetch=LAZY : sur le cote non-owning d'un @OneToOne, Hibernate
     * doit verifier en BDD si l'avis existe pour retourner null vs proxy, donc
     * il charge l'entite quoi qu'il arrive. Mettre LAZY serait mensonger.
     */
    @OneToOne(mappedBy = "location")
    private Avis avis;

    /**
     * Second conducteur (optionnel). Cote owning du OneToOne.
     * CascadeType.ALL + orphanRemoval=true : creation et suppression
     * automatiques avec la location.
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "conducteur_secondaire_id", unique = true)
    private ConducteurSecondaire conducteurSecondaire;

    /**
     * Reclamations liees a cette location.
     * Pas de cascade : la suppression de la location ne supprime pas les
     * reclamations (FK nullable cote Reclamation).
     */
    @OneToMany(mappedBy = "location", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Reclamation> reclamations = new HashSet<>();
}
