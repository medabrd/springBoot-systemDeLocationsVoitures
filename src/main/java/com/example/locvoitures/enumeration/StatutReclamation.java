/*
 * ============================================================================
 * StatutReclamation - Etat de traitement d'une reclamation
 * ============================================================================
 *
 * Role :
 *   Workflow simplifie a deux etats (decide en cours de projet apres
 *   simplification du flux). EN_TRAITEMENT = admin doit traiter, CLOTUREE
 *   = traitement termine. Permet de filtrer la file d'attente cote admin.
 *
 * Utilise par :
 *   - entity/Reclamation.java : champ statut (defaut EN_TRAITEMENT au @PrePersist)
 *   - service/ReclamationService.cloturer() : passe le statut a CLOTUREE
 *     et envoie eventuellement une reponse par mail
 *   - controller/AdminReclamationController : filtre la liste, action
 *     "Cloturer" sur reclamation EN_TRAITEMENT
 *   - templates/admin/reclamations/liste.html : badge couleur selon statut
 *   - templates/client/reclamations/liste.html : visibilite cote client
 *
 * Note historique :
 *   Au debut, il y avait plus de statuts (NOUVELLE, EN_COURS, RESOLUE,
 *   REJETEE). Simplifie a la demande pour eviter une complexite metier
 *   inutile sur un mini-projet.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum StatutReclamation {
    // Reclamation deposee par le client, en attente de traitement par l'admin.
    // Etat par defaut a la creation (voir Reclamation.@PrePersist).
    EN_TRAITEMENT,

    // Reclamation traitee : l'admin a clos le dossier, eventuellement
    // avec un message de reponse envoye par email au client.
    CLOTUREE
}
