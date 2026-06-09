/*
 * ============================================================================
 * Voiture - Vehicule de la flotte de l'agence
 * ============================================================================
 *
 * Role :
 *   Entite principale de la flotte. Porte les caracteristiques commerciales
 *   (modele, annee, couleur, immatriculation, tarif, photo) et les relations
 *   vers la marque, la categorie, les details techniques (1-1), les
 *   equipements (N-N) et les locations historiques (1-N).
 *
 * Utilise par :
 *   - entity/Location.java : ManyToOne voiture
 *   - entity/Categorie.java, entity/Marque.java, entity/Equipement.java,
 *     entity/DetailsVoiture.java : relations sortantes
 *   - repository/VoitureRepository : filtrer (JPQL avec criteres dynamiques),
 *     countByStatutGeneral, etc.
 *   - service/VoitureService : creer/modifier/supprimer, gere uploads photos
 *   - service/LocationService : verifie disponibilite (overlap des locations
 *     non terminales sur les dates demandees)
 *   - controller/CatalogueController : /catalogue, /catalogue/{id}
 *   - controller/AdminVoitureController : /admin/voitures (CRUD admin)
 *   - controller/VoitureDisponibiliteController : verification AJAX
 *   - templates/catalogue/* et templates/admin/voitures/*
 *
 * Suppression :
 *   Voiture.locations en lazy mais pas en cascade. Location.voiture est
 *   nullable : on detache d'abord les locations (voiture=null), puis on
 *   supprime la Voiture pour preserver l'historique.
 *
 * Persistence :
 *   immatriculation UNIQUE en base (plaque unique par definition).
 *   StatutVoiture stocke en VARCHAR via EnumType.STRING.
 * ============================================================================
 */
package com.example.locvoitures.entity;

// Enum statut general (ACTIVE / HORS_SERVICE)
import com.example.locvoitures.enumeration.StatutVoiture;

// JPA core
import jakarta.persistence.*;

// Validation Bean (wildcard pour @NotBlank, @Size, @Min, @Max, @NotNull, @DecimalMin)
import jakarta.validation.constraints.*;

// Lombok
import lombok.*;

// BigDecimal pour le tarif journalier (precision financiere)
import java.math.BigDecimal;
// Collections pour Sets equipements et locations
import java.util.HashSet;
import java.util.Set;

/**
 * Entity / @Table : table "voiture"
 * ToString(exclude=...) : evite lazy init des collections et la boucle
 *                          bidirectionnelle Voiture<->Equipement
 */
@Entity
@Table(name = "voiture")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"equipements", "locations"})
public class Voiture {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Modele commercial (ex: "Clio 5", "208 GTi").
     */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String modele;

    /**
     * Annee de mise en circulation. Borne min/max pour eviter les saisies absurdes.
     */
    @Min(1990)
    @Max(2030)
    @Column(nullable = false)
    private int annee;

    /**
     * Couleur (champ libre court, affiche dans la fiche).
     */
    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String couleur;

    /**
     * Plaque d'immatriculation. Unique en base.
     */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String immatriculation;

    /**
     * Tarif journalier en euros. BigDecimal precision=10 scale=2.
     * Multiplique par nombreJours pour calculer le prix total d'une location.
     */
    @NotNull
    @DecimalMin("1.0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifJournalier;

    /**
     * Chemin relatif vers la photo du vehicule (uploads/voitures/).
     */
    @Size(max = 255)
    private String photo;

    /**
     * Description commerciale libre (jusqu'a 1000 caracteres).
     */
    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    /**
     * Statut general (ACTIVE = en flotte, HORS_SERVICE = retire temporairement).
     * Filtre par defaut a ACTIVE cote catalogue client.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutVoiture statutGeneral = StatutVoiture.ACTIVE;

    /**
     * Marque. Obligatoire (FK NOT NULL).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marque_id", nullable = false)
    private Marque marque;

    /**
     * Categorie. Obligatoire (FK NOT NULL).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    /**
     * Details techniques (1-1). Cote owning : FK details_id sur Voiture.
     * Cascade ALL + orphanRemoval : creation et suppression automatiques
     * avec la voiture.
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "details_id", unique = true)
    private DetailsVoiture details;

    /**
     * Equipements (N-N). Cote owning, donc @JoinTable porte ici la table
     * de jointure "voiture_equipement" avec ses deux colonnes FK.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "voiture_equipement",
            joinColumns = @JoinColumn(name = "voiture_id"),
            inverseJoinColumns = @JoinColumn(name = "equipement_id")
    )
    @Builder.Default
    private Set<Equipement> equipements = new HashSet<>();

    /**
     * Locations historiques sur cette voiture. Cote inverse (Location.voiture
     * porte la FK). Pas de cascade : on detache plutot que de supprimer.
     */
    @OneToMany(mappedBy = "voiture", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Location> locations = new HashSet<>();
}
