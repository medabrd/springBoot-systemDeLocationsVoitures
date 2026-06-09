/*
 * ============================================================================
 * VoitureForm - DTO de formulaire CRUD d'une voiture
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire admin pour creer/modifier une voiture. Aplatit
 *   les caracteristiques techniques (DetailsVoiture) dans le DTO pour un
 *   formulaire monolithique cote HTML, et utilise des ids pour marque/
 *   categorie/equipements (pas d'entites JPA dans le DTO).
 *
 * Utilise par :
 *   - templates/admin/voitures/form.html : th:object="${form}"
 *   - controller/AdminVoitureController.creer/modifier : @Valid VoitureForm
 *
 * Mappe vers/depuis :
 *   - toVoiture() : construit une Voiture (sans marque/categorie/equipements
 *     resolus par le controller via les ids)
 *   - fromVoiture(v) : pour pre-remplir le formulaire en modification
 *
 * Validation :
 *   Toutes les contraintes de Voiture + DetailsVoiture, mais cote DTO pour
 *   eviter de polluer l'entite avec des contraintes de presentation.
 *
 * Photo :
 *   N'est PAS dans le DTO : geree separement comme MultipartFile parameter
 *   du controller pour beneficier de l'auto-binding multipart de Spring.
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import com.example.locvoitures.entity.Equipement;
import com.example.locvoitures.entity.Voiture;
import com.example.locvoitures.entity.DetailsVoiture;
import com.example.locvoitures.enumeration.Carburant;
import com.example.locvoitures.enumeration.StatutVoiture;
import com.example.locvoitures.enumeration.Transmission;
// Wildcard pour @NotBlank, @NotNull, @Size, @Min, @Max, @DecimalMin, @DecimalMax
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class VoitureForm {

    /**
     * Id de la voiture. Null en creation, rempli en modification.
     */
    private Long id;

    @NotBlank(message = "Le modele est obligatoire")
    @Size(max = 50)
    private String modele;

    @Min(value = 1990, message = "Annee minimum 1990")
    @Max(value = 2030, message = "Annee maximum 2030")
    private int annee;

    @NotBlank(message = "La couleur est obligatoire")
    @Size(max = 30)
    private String couleur;

    @NotBlank(message = "L'immatriculation est obligatoire")
    @Size(max = 20)
    private String immatriculation;

    @NotNull(message = "Le tarif est obligatoire")
    @DecimalMin(value = "1.0", message = "Tarif minimum 1 DT")
    private BigDecimal tarifJournalier;

    @Size(max = 1000)
    private String description;

    /**
     * Statut par defaut ACTIVE a la creation.
     */
    @NotNull
    private StatutVoiture statutGeneral = StatutVoiture.ACTIVE;

    /**
     * Ids des relations. Resolus en entites cote controller via Marque/Categorie/Equipement Service.
     */
    @NotNull(message = "La marque est obligatoire")
    private Long marqueId;

    @NotNull(message = "La categorie est obligatoire")
    private Long categorieId;

    /**
     * Equipements selectionnes (checkboxes). null/vide si aucun.
     */
    private List<Long> equipementIds;

    // ============================================================
    // Champs aplatis de DetailsVoiture
    // ============================================================
    @NotNull(message = "Transmission obligatoire")
    private Transmission transmission;

    @NotNull(message = "Carburant obligatoire")
    private Carburant typeCarburant;

    @DecimalMin("0.0") @DecimalMax("100.0")
    private double consommation;

    @Min(1) @Max(2000)
    private int puissance;

    @Min(1) @Max(10)
    private int nombrePlaces;

    @Min(2) @Max(6)
    private int nombrePortes;

    @Min(0)
    private int volumeCoffre;

    /**
     * Construit une entite Voiture (sans marque/categorie/equipements/photo
     * resolus dans le controller).
     */
    public Voiture toVoiture() {
        Voiture v = new Voiture();
        v.setId(id);
        v.setModele(modele);
        v.setAnnee(annee);
        v.setCouleur(couleur);
        v.setImmatriculation(immatriculation);
        v.setTarifJournalier(tarifJournalier);
        v.setDescription(description);
        v.setStatutGeneral(statutGeneral);

        // Reconstruction de DetailsVoiture via builder Lombok
        DetailsVoiture details = DetailsVoiture.builder()
                .transmission(transmission)
                .typeCarburant(typeCarburant)
                .consommation(consommation)
                .puissance(puissance)
                .nombrePlaces(nombrePlaces)
                .nombrePortes(nombrePortes)
                .volumeCoffre(volumeCoffre)
                .build();
        v.setDetails(details);
        return v;
    }

    /**
     * Methode statique inverse : pre-remplit le DTO depuis une Voiture
     * existante (ecran modification).
     */
    public static VoitureForm fromVoiture(Voiture v) {
        VoitureForm f = new VoitureForm();
        f.setId(v.getId());
        f.setModele(v.getModele());
        f.setAnnee(v.getAnnee());
        f.setCouleur(v.getCouleur());
        f.setImmatriculation(v.getImmatriculation());
        f.setTarifJournalier(v.getTarifJournalier());
        f.setDescription(v.getDescription());
        f.setStatutGeneral(v.getStatutGeneral());
        f.setMarqueId(v.getMarque() != null ? v.getMarque().getId() : null);
        f.setCategorieId(v.getCategorie() != null ? v.getCategorie().getId() : null);
        // toList() Java 16+ : retourne List immutable, OK car on s'en sert juste pour set
        f.setEquipementIds(v.getEquipements().stream().map(Equipement::getId).toList());
        if (v.getDetails() != null) {
            f.setTransmission(v.getDetails().getTransmission());
            f.setTypeCarburant(v.getDetails().getTypeCarburant());
            f.setConsommation(v.getDetails().getConsommation());
            f.setPuissance(v.getDetails().getPuissance());
            f.setNombrePlaces(v.getDetails().getNombrePlaces());
            f.setNombrePortes(v.getDetails().getNombrePortes());
            f.setVolumeCoffre(v.getDetails().getVolumeCoffre());
        }
        return f;
    }
}
