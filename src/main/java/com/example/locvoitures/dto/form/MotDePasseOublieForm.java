/*
 * ============================================================================
 * MotDePasseOublieForm - DTO de demande de reinitialisation
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire "Mot de passe oublie ?". L'utilisateur saisit
 *   son email, on lui envoie un lien de reinitialisation (si le compte
 *   existe et est actif).
 *
 * Utilise par :
 *   - templates/auth/mot-de-passe-oublie.html
 *   - controller/AuthController.motDePasseOublie() : @ModelAttribute @Valid
 *
 * Mappe vers :
 *   - UtilisateurService.demanderReinitialisation(email)
 *
 * Anti-enumeration :
 *   La methode service est silencieuse si l'email n'existe pas. Cote UI,
 *   on affiche toujours le meme message "Si ce compte existe, un mail a
 *   ete envoye". Un attaquant ne peut pas distinguer un email valide d'un
 *   email inconnu.
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MotDePasseOublieForm {

    /**
     * Email du compte pour lequel demander la reinitialisation.
     */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 100)
    private String email;
}
