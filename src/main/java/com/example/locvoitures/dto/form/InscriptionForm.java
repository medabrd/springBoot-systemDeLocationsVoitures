/*
 * ============================================================================
 * InscriptionForm - DTO de creation de compte client
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire d'inscription. Decouple du modele Utilisateur
 *   pour ajouter la confirmation de mot de passe (champ non persiste) et
 *   appliquer une validation cote presentation.
 *
 * Utilise par :
 *   - templates/auth/inscription.html : th:object="${form}"
 *   - controller/AuthController.inscrire() : @ModelAttribute @Valid
 *
 * Mappe vers :
 *   - UtilisateurService.inscrireClient(email, motDePasseClair, nom, prenom)
 *
 * Validation Bean :
 *   - email obligatoire, format @Email, max 100 cars
 *   - motDePasse obligatoire, 6-100 cars
 *   - confirmationMotDePasse obligatoire (verification manuelle dans le DTO)
 *
 * Methode metier :
 *   motsDePasseConcordent() : verifie la coherence entre mdp et confirmation.
 *   Appelee dans le controller pour ajouter une erreur de binding si necessaire
 *   (Bean Validation n'offre pas de @Match natif).
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InscriptionForm {

    /**
     * Email = login. Sera normalise (trim + lowercase) cote service.
     */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    private String email;

    /**
     * Mot de passe en clair. Hashe en BCrypt par UtilisateurService avant
     * persistance.
     */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 6, max = 100, message = "Le mot de passe doit contenir au moins 6 caracteres")
    private String motDePasse;

    /**
     * Confirmation. Doit etre identique a motDePasse (verifie par
     * motsDePasseConcordent()).
     */
    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmationMotDePasse;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50)
    private String nom;

    @NotBlank(message = "Le prenom est obligatoire")
    @Size(max = 50)
    private String prenom;

    /**
     * Helper : true si motDePasse == confirmationMotDePasse.
     * Pas une contrainte @AssertTrue car on prefere un message d'erreur
     * personnalise et un binding result du controller.
     */
    public boolean motsDePasseConcordent() {
        return motDePasse != null && motDePasse.equals(confirmationMotDePasse);
    }
}
