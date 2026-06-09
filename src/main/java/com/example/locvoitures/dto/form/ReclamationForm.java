/*
 * ============================================================================
 * ReclamationForm - DTO de soumission d'une reclamation
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire de depot de reclamation par un client sur sa
 *   location en cours. La photo eventuelle est traitee comme MultipartFile
 *   separe dans le controller.
 *
 * Utilise par :
 *   - templates/client/locations/reclamation.html
 *   - controller/ClientLocationController.creerReclamation() : @Valid
 *
 * Mappe vers :
 *   - ReclamationService.soumettre(location, client, categorie, description,
 *     photo)
 *
 * Validation :
 *   - categorie obligatoire (enum dropdown)
 *   - description obligatoire, max 1000 cars (pas de pavés excessifs)
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

import com.example.locvoitures.enumeration.CategorieReclamation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReclamationForm {

    /**
     * Categorie metier (PANNE, ACCIDENT, PROPRETE, EQUIPEMENT, AUTRE).
     * Choisie via dropdown cote HTML.
     */
    @NotNull(message = "La categorie est obligatoire")
    private CategorieReclamation categorie;

    /**
     * Description detaillee du probleme.
     */
    @NotBlank(message = "La description est obligatoire")
    @Size(max = 1000, message = "La description ne peut depasser 1000 caracteres")
    private String description;
}
