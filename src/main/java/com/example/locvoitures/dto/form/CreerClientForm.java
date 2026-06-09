/*
 * ============================================================================
 * CreerClientForm - DTO de creation de compte client par l'admin
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire "Ajouter un client" cote admin (cas du client qui
 *   se presente physiquement au bureau). Decouple du modele Client pour
 *   appliquer une validation specifique presentation.
 *
 * Utilise par :
 *   - templates/admin/utilisateurs/nouveau.html : th:object="${creerForm}"
 *   - controller/AdminUtilisateurController.creer() : @ModelAttribute @Valid
 *
 * Mappe vers :
 *   - UtilisateurService.creerClientParAdmin(...)
 *
 * Validation Bean :
 *   - email, nom, prenom : obligatoires
 *   - telephone, numeroPermis, numeroCIN : optionnels (l'admin peut completer
 *     immediatement si le client a apporte ses papiers, sinon le client les
 *     saisira lui-meme apres connexion).
 *
 * Particularites versus InscriptionForm :
 *   - Pas de motDePasse : il est genere automatiquement et envoye par mail
 *   - Compte cree directement actif (pas de mail d'activation)
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class CreerClientForm {

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50)
    private String nom;

    @NotBlank(message = "Le prenom est obligatoire")
    @Size(max = 50)
    private String prenom;

    /**
     * Telephone : optionnel. Format identique a celui exige sur l'entite
     * Client : 8 a 20 caracteres dans [+0-9 ].
     */
    @Pattern(regexp = "^$|^[+0-9 ]{8,20}$", message = "Format de telephone invalide")
    private String telephone;

    /**
     * Numero de permis : optionnel a la creation. Verifie unique cote service.
     * Si renseigne, un Permis est attache au Client. Les autres champs du
     * permis (dates et photo) peuvent etre completes plus tard par le client
     * depuis son profil.
     */
    @Size(max = 30)
    private String numeroPermis;

    /**
     * Date d'obtention du permis : optionnel a la creation.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateObtentionPermis;

    /**
     * Date d'expiration du permis : optionnel a la creation.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateExpirationPermis;

    /**
     * Numero de CIN : optionnel a la creation. Verifie unique cote service.
     */
    @Size(max = 30)
    private String numeroCIN;
}
