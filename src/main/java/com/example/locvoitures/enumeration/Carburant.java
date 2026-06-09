/*
 * ============================================================================
 * Carburant - Type de carburant / motorisation d'un vehicule
 * ============================================================================
 *
 * Role :
 *   Caracteristique technique decrivant le mode de propulsion.
 *   Utilisee comme champ descriptif et comme critere de filtrage dans le
 *   catalogue (clients qui cherchent specifiquement de l'electrique etc.).
 *
 * Utilise par :
 *   - entity/DetailsVoiture.java : champ typeCarburant
 *     (nomme typeCarburant pour eviter conflit avec un eventuel champ "carburant")
 *   - dto/VoitureForm.java : champ pour formulaire admin
 *   - controller/CatalogueController.liste : @RequestParam Carburant
 *   - controller/AdminVoitureController : meme filtre
 *   - repository/VoitureRepository.filtrer : critere JPQL conditionnel
 *   - templates : dropdown de selection
 *
 * Conversion Spring MVC : automatique via converter d'enum standard.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum Carburant {
    // Moteur essence classique.
    ESSENCE,

    // Moteur diesel (gasoil), generalement plus economique sur longue distance.
    DIESEL,

    // Motorisation hybride (essence + electrique).
    HYBRIDE,

    // 100% electrique (batterie). Necessite point de charge.
    ELECTRIQUE
}
