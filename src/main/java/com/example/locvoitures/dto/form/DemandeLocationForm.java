/*
 * ============================================================================
 * DemandeLocationForm - DTO de demande de location par le client
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire complet de creation d'une demande de location :
 *   voiture choisie, dates, option second conducteur avec ses documents.
 *   Decouple le formulaire des entites pour appliquer une validation
 *   specifique a la presentation (ex: @Future sur dateFin).
 *
 * Utilise par :
 *   - templates/client/locations/form.html : formulaire principal
 *   - controller/ClientLocationController.soumettre() : recoit le DTO
 *     + MultipartFile pour les photos du second conducteur
 *
 * Mappe vers :
 *   - LocationService.soumettreDemande(client, voiture, dateDebut, dateFin,
 *     secondConducteur)
 *
 * Validation Bean :
 *   - voitureId obligatoire
 *   - dateDebut >= aujourd'hui (FutureOrPresent)
 *   - dateFin > aujourd'hui (Future)
 *   - Les champs du second conducteur sont conditionnels (Bean Validation
 *     ne couvre pas le conditionnel, on verifie cote controller si
 *     avecSecondConducteur=true)
 *
 * Photos :
 *   Les photoPermis et photoCIN du second conducteur ne sont pas dans le
 *   DTO : Spring les recupere comme parametres MultipartFile separes.
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

// Validation temporelle Bean Validation
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class DemandeLocationForm {

    /**
     * Id de la voiture selectionnee. Vient generalement d'un hidden field
     * (pre-rempli par le bouton "Reserver" depuis le catalogue).
     */
    @NotNull(message = "La voiture est obligatoire")
    private Long voitureId;

    /**
     * FutureOrPresent : aujourd'hui ou plus tard accepte.
     */
    @NotNull(message = "La date de debut est obligatoire")
    @FutureOrPresent(message = "La date de debut doit etre aujourd'hui ou ulterieure")
    private LocalDate dateDebut;

    /**
     * Future : strictement apres aujourd'hui.
     */
    @NotNull(message = "La date de fin est obligatoire")
    @Future(message = "La date de fin doit etre future")
    private LocalDate dateFin;

    /**
     * Toggle "ajouter un second conducteur" (checkbox dans le formulaire).
     * Si false, les champs second* sont ignores.
     */
    private boolean avecSecondConducteur;

    @Size(max = 50)
    private String secondNom;

    @Size(max = 50)
    private String secondPrenom;

    @Size(max = 30)
    private String secondNumeroPermis;

    /**
     * Date d'obtention du permis du second conducteur. Si le numero existe
     * deja en base, cette date est ignoree (PermisService reutilise l'entite
     * existante).
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate secondDateObtentionPermis;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate secondDateExpirationPermis;

    @Size(max = 30)
    private String secondNumeroCIN;
    // Les photos (permis + CIN) sont gerees en MultipartFile dans le controller
}
