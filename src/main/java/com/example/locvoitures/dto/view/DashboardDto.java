/*
 * ============================================================================
 * DashboardDto - KPIs agreges pour la page d'accueil admin
 * ============================================================================
 *
 * Role :
 *   Conteneur typé regroupant tous les indicateurs affiches sur le dashboard
 *   admin. Construit par DashboardService a partir de requetes COUNT/SUM
 *   sur les repositories.
 *
 * Utilise par :
 *   - service/DashboardService.buildDashboard() : construction via builder
 *   - controller/AdminDashboardController : passe le DTO au modele
 *   - templates/admin/dashboard.html : affiche chaque champ dans une carte
 *
 * Compose :
 *   - List<TopVoitureDto> : top 5 voitures les plus louees
 *   - List<RepartitionDto> : repartition de la flotte par categorie
 *
 * Pattern :
 *   DTO pur (sans logique). Le builder Lombok permet une construction
 *   lisible (.nbVoituresTotal(...).nbDemandesEnAttente(...)...).
 * ============================================================================
 */
package com.example.locvoitures.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data : getters/setters/equals/hashCode/toString
 * Builder : pattern builder pour construction lisible
 * NoArgsConstructor / @AllArgsConstructor : requis par @Builder + frameworks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {

    // ============================================================
    // Flotte (vehicules)
    // ============================================================
    private long nbVoituresTotal;
    private long nbVoituresActives;
    private long nbVoituresHorsService;

    // ============================================================
    // Locations
    // ============================================================
    private long nbDemandesEnAttente;
    private long nbLocationsEnCours;
    /** ACCEPTEES en attente de paiement */
    private long nbLocationsPreReservees;
    /** SUM(prixTotal) sur les statuts payes (PAYEE/EN_COURS/TERMINEE) */
    private BigDecimal chiffreAffairesTotal;

    // ============================================================
    // Utilisateurs
    // ============================================================
    private long nbClientsActifs;
    private long nbPermisBannis;

    // ============================================================
    // Qualite
    // ============================================================
    /** Moyenne des notes d'avis. Calculee AVG(note) cote SQL */
    private double noteMoyenne;
    private long nbReclamationsEnTraitement;

    // ============================================================
    // Top et repartitions (alimentent les graphiques Chart.js)
    // ============================================================
    private List<TopVoitureDto> topVoitures;
    private List<RepartitionDto> repartitionParCategorie;
}
