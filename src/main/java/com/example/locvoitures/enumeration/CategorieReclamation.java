/*
 * ============================================================================
 * CategorieReclamation - Typologie metier d'une reclamation client
 * ============================================================================
 *
 * Role :
 *   Classe la reclamation deposee par un client en categorie metier pour
 *   faciliter le tri/statistique cote admin. La valeur est choisie par le
 *   client au moment du depot via un dropdown.
 *
 * Utilise par :
 *   - entity/Reclamation.java : champ categorie (NOT NULL en base)
 *   - dto/ReclamationForm.java : champ categorie pour le formulaire client
 *   - controller/ClientLocationController : recoit le DTO avec la categorie
 *   - controller/AdminReclamationController : peut filtrer/afficher par categorie
 *   - templates/client/reclamations/form.html : dropdown de selection
 *   - templates/admin/reclamations/liste.html : affichage en colonne
 *
 * Conversion Spring MVC :
 *   Le converter d'enum par defaut convertit "PANNE" en CategorieReclamation.PANNE
 *   directement depuis le @RequestParam ou le @ModelAttribute.
 *
 * Persistence :
 *   Stocke en VARCHAR via @Enumerated(EnumType.STRING) sur l'entite, plus
 *   robuste que ORDINAL si on ajoute des valeurs.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum CategorieReclamation {
    // Probleme mecanique non lie a un accident (moteur, batterie, embrayage...).
    PANNE,

    // Accident corporel ou materiel (collision, sortie de route).
    ACCIDENT,

    // Probleme de proprete du vehicule a la prise en charge.
    PROPRETE,

    // Equipement defectueux ou manquant (GPS, siege bebe, climatisation...).
    EQUIPEMENT,

    // Catch-all pour les cas qui ne rentrent dans aucune categorie ci-dessus.
    AUTRE
}
