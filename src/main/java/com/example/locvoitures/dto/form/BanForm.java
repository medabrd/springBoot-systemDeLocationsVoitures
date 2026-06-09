/*
 * ============================================================================
 * BanForm - DTO de bannissement d'un permis
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire admin pour bannir un numero de permis. Decouple
 *   le formulaire de l'entite PermisBanni (qui porte aussi adminBannisseur
 *   et dateBan poses cote service, hors du DTO).
 *
 * Utilise par :
 *   - templates/admin/utilisateurs/bannir.html (formulaire)
 *   - controller/AdminUtilisateurController.bannir() : @ModelAttribute @Valid
 *
 * Mappe vers :
 *   - BannissementService.bannir(numeroPermis, motif, adminCourant)
 *
 * Validation :
 *   - numeroPermis : obligatoire, max 30 cars
 *   - motif : obligatoire, max 500 cars (audit lisible)
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BanForm {

    /**
     * Numero du permis a bannir. Saisi par l'admin ou pre-rempli si on
     * arrive depuis le profil d'un client.
     */
    @NotBlank(message = "Le numero de permis est obligatoire")
    @Size(max = 30)
    private String numeroPermis;

    /**
     * Motif du bannissement (audit). Justifie l'action et apparait dans
     * le mail envoye au client.
     */
    @NotBlank(message = "Le motif est obligatoire")
    @Size(max = 500, message = "Le motif ne peut depasser 500 caracteres")
    private String motif;
}
