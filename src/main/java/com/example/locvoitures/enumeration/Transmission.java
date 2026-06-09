/*
 * ============================================================================
 * Transmission - Type de boite de vitesses d'un vehicule
 * ============================================================================
 *
 * Role :
 *   Caracteristique technique de la voiture, utilisee a la fois comme champ
 *   descriptif et comme critere de filtrage dans le catalogue.
 *
 * Utilise par :
 *   - entity/DetailsVoiture.java : champ transmission
 *   - dto/VoitureForm.java : champ transmission (pour le formulaire admin)
 *   - controller/CatalogueController.liste : @RequestParam Transmission
 *     pour filtrer la recherche
 *   - controller/AdminVoitureController : meme filtre
 *   - repository/VoitureRepository.filtrer : utilise dans la JPQL
 *     (:transmission IS NULL OR v.details.transmission = :transmission)
 *   - templates/catalogue/liste.html et admin/voitures/liste.html :
 *     dropdown de selection
 *
 * Conversion Spring MVC :
 *   Spring convertit automatiquement le query string "?transmission=MANUELLE"
 *   en Transmission.MANUELLE grace au converter d'enum par defaut. Pas
 *   besoin de @InitBinder.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum Transmission {
    // Boite manuelle (avec pedale d'embrayage).
    MANUELLE,

    // Boite automatique (sans embrayage manuel).
    // Souvent associee a un tarif journalier plus eleve.
    AUTOMATIQUE
}
