/*
 * ============================================================================
 * StatutLocation - Enumeration des etats possibles d'une location
 * ============================================================================
 *
 * Role :
 *   Modelise la machine a etats du cycle de vie d'une demande/location.
 *   Chaque valeur correspond a un etat metier distinct entre la soumission
 *   par le client et la cloture finale.
 *
 * Transitions :
 *   EN_ATTENTE --(accepter)--> ACCEPTEE --(valider paiement)--> PAYEE
 *                                |                                |
 *                                +--(refuser)--> REFUSEE          |
 *                                +--(48h sans paiement)--> EXPIREE|
 *                                                                 v
 *                                                EN_COURS --> TERMINEE
 *                                            (remise des cles -> restitution)
 *
 * Utilise par :
 *   - entity/Location.java : champ statut (colonne SQL VARCHAR via @Enumerated)
 *   - service/LocationService.java : validation des transitions autorisees
 *   - service/ReclamationService.java : verifie que la location est EN_COURS
 *     avant d'autoriser le depot d'une reclamation
 *   - service/AvisService.java : verifie que la location est TERMINEE
 *   - repository/LocationRepository.java : filtres JPQL countByStatut, etc.
 *   - repository/VoitureRepository.java : exclure les voitures bloquees
 *     (ACCEPTEE/PAYEE/EN_COURS) pour les recherches de disponibilite
 *   - templates Thymeleaf : badges colores (badge-EN_ATTENTE, badge-PAYEE,
 *     ...) definis dans static/css/style.css
 *   - dto/DemandeLocationForm.java (indirectement) : le statut initial est
 *     toujours EN_ATTENTE a la creation cote client
 *
 * Persistance :
 *   Stocke en VARCHAR(20) cote DB grace a @Enumerated(EnumType.STRING)
 *   sur l'attribut Location.statut. Le nom de la constante Java est ecrit
 *   tel quel en base, ce qui rend les requetes SQL lisibles et evite la
 *   fragilite de l'ordre ordinal en cas d'ajout/retrait de valeurs.
 *
 * Aucune importation externe : enum simple sans annotations ni methodes.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

// Declaration du package : situe cette enum dans la sous-arborescence
// dediee aux enumerations metier, separee des entites et des DTOs.

/**
 * Enum public car referencee depuis tous les packages (entity, service,
 * repository, controller). Pas final / abstract : les enums Java sont
 * implicitement final et ne peuvent pas etre etendues.
 */
public enum StatutLocation {

    // Demande tout juste deposee par le client via DemandeLocationForm.
    // Statut par defaut affecte dans LocationService.soumettreDemande().
    EN_ATTENTE,

    // L'admin a clique "Accepter" sur le detail de la demande.
    // Declenche la generation de facture PDF + envoi mail au client.
    // Pose dateExpiration = now + 48h (champ Location.dateExpiration).
    ACCEPTEE,

    // L'admin a refuse la demande avec un motif obligatoire (champ
    // Location.motifRefus). Etat terminal cote workflow.
    REFUSEE,

    // L'admin a valide le paiement en especes au bureau (action
    // validerPaiement). dateExpiration repassee a null,
    // datePaiement = now. La voiture est reservee mais les cles ne sont
    // pas encore remises (cas typique : reservation pour plus tard).
    PAYEE,

    // L'admin a clique "Remettre les cles" (LocationService.demarrerLocation).
    // Le client peut maintenant deposer une reclamation (cf. ReclamationService).
    // Transition manuelle (anciennement automatisee par scheduler, change
    // pour coller au scenario reel "remise physique des cles au bureau").
    EN_COURS,

    // L'admin a clique "Restituer" (LocationService.cloreLocation).
    // Le client peut maintenant deposer un avis (cf. AvisService).
    // Le scheduler envoyerRappelsAvis envoie un mail de relance quotidien
    // a 9h pour les TERMINEE sans avis.
    TERMINEE,

    // 48h depassees sans validation de paiement.
    // LocationScheduler.expirerReservations (cron */15 minutes) detecte
    // les ACCEPTEE dont dateExpiration < now et les passe en EXPIREE +
    // envoi mail au client.
    EXPIREE
}
