/*
 * ============================================================================
 * StatutVoiture - Statut d'exploitation d'un vehicule de la flotte
 * ============================================================================
 *
 * Role :
 *   Indique si une voiture est exploitable (peut etre louee) ou retiree
 *   temporairement (maintenance, panne, sinistre). Different du statut
 *   d'une LOCATION (StatutLocation) : ici on parle du vehicule lui-meme.
 *
 * Utilise par :
 *   - entity/Voiture.java : champ statutGeneral
 *   - service/LocationService.soumettreDemande() : refuse la creation
 *     d'une location si voiture != ACTIVE
 *   - service/LocationService.creerParAdmin() : meme garde
 *   - controller/CatalogueController : force le filtre ACTIVE pour le
 *     catalogue client (les HORS_SERVICE n'apparaissent pas)
 *   - repository/VoitureRepository : countByStatutGeneral pour KPIs
 *     dashboard, filtrer par statut
 *   - templates/admin/voitures/liste.html : badge vert si ACTIVE, jaune sinon
 *   - templates/admin/voitures/form.html : dropdown pour basculer
 *
 * Limite actuelle (documentee dans le rapport) :
 *   Rien n'empeche un admin de passer une voiture en HORS_SERVICE alors
 *   qu'elle a une location EN_COURS. Pas de garde de coherence ajoutee.
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum StatutVoiture {
    // Vehicule en flotte exploitable. Peut recevoir une location si la
    // periode demandee est libre (pas de chevauchement avec une location
    // ACCEPTEE/PAYEE/EN_COURS).
    ACTIVE,

    // Vehicule retire temporairement (maintenance preventive, accident,
    // retour atelier). Invisible du catalogue client, ne peut recevoir
    // de nouvelle location. Visible cote admin pour pouvoir reactiver.
    HORS_SERVICE
}
