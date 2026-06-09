/*
 * ============================================================================
 * PosteAdmin - Poste interne occupe par un administrateur
 * ============================================================================
 *
 * Role :
 *   Specialisation metier d'un compte ADMIN au sein de l'agence. Permet de
 *   differencier les profils internes sans creer de classes Java distinctes
 *   pour chaque poste.
 *
 * Utilise par :
 *   - entity/Admin.java : champ posteOccupe (NOT NULL en base)
 *   - templates/admin/* : badge "Poste : ..." sur les ecrans de gestion
 *
 * Note :
 *   Valeurs choisies pour refleter une organisation realiste d'une agence
 *   de location. L'enum est extensible (ajouter une valeur ne casse rien
 *   tant qu'on n'utilise pas EnumType.ORDINAL en persistence).
 * ============================================================================
 */
package com.example.locvoitures.enumeration;

public enum PosteAdmin {
    // Direction generale, vision strategique de l'agence
    DIRECTION,

    // Personnel d'accueil : enregistrement des clients au comptoir,
    // remise/restitution des cles
    ACCUEIL,

    // Responsable de la flotte : gestion CRUD voitures, marques,
    // categories, equipements
    GESTIONNAIRE_FLOTTE,

    // Comptabilite : suivi des paiements, factures, chiffre d'affaires
    COMPTABILITE
}
