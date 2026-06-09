/*
 * ============================================================================
 * ReinitialisationForm - DTO de saisie du nouveau mot de passe
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire de reinitialisation de mot de passe (apres clic
 *   sur le lien recu par mail). L'utilisateur saisit le nouveau mot de
 *   passe + confirmation, le token est en hidden field.
 *
 * Utilise par :
 *   - templates/auth/reinitialiser-mot-de-passe.html
 *   - controller/AuthController.reinitialiserMotDePasse() : @Valid
 *
 * Mappe vers :
 *   - UtilisateurService.reinitialiserMotDePasse(token, mdp, confirmation)
 *
 * Validation cote service :
 *   - Token doit exister et ne pas etre expire
 *   - mdp = confirmation
 *   - mdp >= 6 caracteres
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReinitialisationForm {

    /**
     * Token UUID recupere dans la query string. Hidden field cote HTML.
     */
    @NotBlank
    private String token;

    /**
     * Nouveau mot de passe en clair (hashe ensuite cote service).
     */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 6, max = 100, message = "Le mot de passe doit contenir au moins 6 caracteres")
    private String motDePasse;

    /**
     * Confirmation pour eviter les fautes de frappe.
     */
    @NotBlank(message = "La confirmation est obligatoire")
    private String confirmationMotDePasse;
}
