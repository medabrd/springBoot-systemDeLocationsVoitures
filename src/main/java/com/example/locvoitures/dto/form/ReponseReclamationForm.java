/*
 * ============================================================================
 * ReponseReclamationForm - DTO de cloture d'une reclamation par l'admin
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire "Cloturer" cote admin. Le champ reponse est
 *   FACULTATIF : l'admin peut cloturer sans envoyer de message (cloture
 *   silencieuse).
 *
 * Utilise par :
 *   - templates/admin/reclamations/details.html (formulaire cloture)
 *   - controller/AdminReclamationController.cloturer() : @Valid
 *
 * Mappe vers :
 *   - ReclamationService.repondre(id, reponse)
 *
 * Validation :
 *   Pas de @NotBlank sur reponse (facultatif). Juste @Size pour eviter les
 *   pavés. Si reponse fournie, EmailService envoie le mail au client.
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReponseReclamationForm {

    /**
     * Message facultatif a envoyer au client. Si vide, cloture silencieuse.
     */
    @Size(max = 1000, message = "La reponse ne peut depasser 1000 caracteres")
    private String reponse;
}
