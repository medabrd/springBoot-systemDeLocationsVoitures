/*
 * ============================================================================
 * DetailsVoiture - Caracteristiques techniques d'une voiture
 * ============================================================================
 *
 * Role :
 *   Regroupe les champs techniques (motorisation, consommation, places...)
 *   en un objet dedie pour ne pas surcharger l'entite Voiture. Relation 1-1
 *   avec Voiture, donc en pratique on aurait pu garder les champs dans
 *   Voiture, mais la separation rend la fiche technique plus lisible cote
 *   templates et facilite l'evolution (ex: ajout d'options techniques).
 *
 * Utilise par :
 *   - entity/Voiture.java : OneToOne details (FK details_id, cascade=ALL)
 *   - dto/VoitureForm.java : ces memes champs sont saisis dans le formulaire
 *     admin et copies dans DetailsVoiture par le service
 *   - service/VoitureService : creer/modifier propagent les valeurs
 *   - controller/CatalogueController : critere de filtrage sur transmission
 *     et carburant via VoitureRepository.filtrer()
 *   - templates/catalogue/details.html et admin/voitures/details.html :
 *     affichage fiche technique
 *
 * Persistence :
 *   Cascade=ALL cote Voiture -> creation/suppression automatique avec la
 *   voiture. orphanRemoval=true cote Voiture.details supprime le
 *   DetailsVoiture si on detache la relation.
 * ============================================================================
 */
package com.example.locvoitures.entity;

// Enums dedies pour transmission et carburant (decoupage des valeurs metier)
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.Transmission;

// JPA core
import jakarta.persistence.*;

// Validation Bean : wildcard car on utilise @NotNull, @Min, @Max, @DecimalMin, @DecimalMax
import jakarta.validation.constraints.*;

// Lombok
import lombok.*;

/**
 * Entity / @Table : table "details_voiture"
 */
@Entity
@Table(name = "details_voiture")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DetailsVoiture {

    /**
     * Cle primaire auto-incrementee.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type de boite de vitesses (MANUELLE / AUTOMATIQUE).
     * Enumerated(EnumType.STRING) : stocke "MANUELLE" en VARCHAR plutot
     * que 0/1 ordinal (plus robuste si on ajoute des valeurs intermediaires).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Transmission transmission;

    /**
     * Motorisation (ESSENCE, DIESEL, HYBRIDE, ELECTRIQUE).
     * Nomme "typeCarburant" pour eviter conflit avec un eventuel champ
     * "carburant" sur Voiture.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Carburant typeCarburant;

    /**
     * Consommation en L/100km (ou kWh/100km pour ELECTRIQUE).
     * DecimalMin / @DecimalMax : bornes sur les decimaux.
     */
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Column(nullable = false)
    private double consommation;

    /**
     * Puissance en chevaux fiscaux/DIN.
     * Min(1) : exclut 0 et negatif. @Max(2000) : borne de bon sens.
     */
    @Min(1)
    @Max(2000)
    @Column(nullable = false)
    private int puissance;

    /**
     * Nombre de places assises (conducteur compris).
     */
    @Min(1)
    @Max(10)
    @Column(nullable = false)
    private int nombrePlaces;

    /**
     * Nombre de portes (coffre arriere inclus si 5 portes).
     */
    @Min(2)
    @Max(6)
    @Column(nullable = false)
    private int nombrePortes;

    /**
     * Volume du coffre en litres (banquette en place).
     */
    @Min(0)
    @Column(nullable = false)
    private int volumeCoffre;
}
