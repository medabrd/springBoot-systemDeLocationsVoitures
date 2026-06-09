/*
 * ============================================================================
 * AvisForm - DTO de soumission d'un avis client
 * ============================================================================
 *
 * Role :
 *   Binding du formulaire HTML quand un client depose un avis sur sa
 *   location terminee. Decouple le formulaire de l'entite Avis pour
 *   isoler la validation cote presentation et eviter le mass-assignment.
 *
 * Utilise par :
 *   - templates/client/locations/avis.html : th:object="${avisForm}"
 *   - controller/ClientLocationController.soumettreAvis() : @ModelAttribute
 *     @Valid AvisForm avisForm
 *
 * Mappe vers :
 *   - AvisService.creerAvis(location, client, note, commentaire) : le
 *     controller extrait les champs du DTO et delegue
 *
 * Validation Bean :
 *   - @Min/@Max sur la note (1-5)
 *   - @NotBlank + @Size sur le commentaire
 *
 * Lombok @Data :
 *   Genere getters, setters, equals, hashCode, toString. Convient aux DTO
 *   purs (pas d'entites JPA car @Data inclut equals/hashCode sur tous les
 *   champs, ce qui est risque pour entites managees).
 * ============================================================================
 */
package com.example.locvoitures.dto.form;

// Validation Bean : contraintes utilisees pour @Valid dans le controller
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Lombok : @Data = combo getter/setter/toString/equals/hashCode/RequiredArgsConstructor
import lombok.Data;

@Data
public class AvisForm {

    /**
     * Note de 1 a 5 etoiles. Mappee depuis un radio-button cote HTML.
     */
    @Min(value = 1, message = "La note doit etre comprise entre 1 et 5")
    @Max(value = 5, message = "La note doit etre comprise entre 1 et 5")
    private int note;

    /**
     * Commentaire libre, obligatoire et limite a 500 caracteres
     * (eviter les pavés).
     */
    @NotBlank(message = "Le commentaire est obligatoire")
    @Size(max = 500, message = "Le commentaire ne peut depasser 500 caracteres")
    private String commentaire;
}
