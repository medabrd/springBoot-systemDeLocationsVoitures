/*
 * ============================================================================
 * RepartitionDto - DTO d'une ligne de repartition (graphique camembert)
 * ============================================================================
 *
 * Role :
 *   Une paire (libelle, count) pour graphique de repartition. Utilise pour
 *   "voitures par categorie" sur le dashboard admin.
 *
 * Utilise par :
 *   - service/DashboardService : mappe les Object[] retournes par la
 *     requete JPQL en RepartitionDto
 *   - dto/DashboardDto.repartitionParCategorie : liste
 *   - templates/admin/dashboard.html : sert les donnees a Chart.js
 *
 * Note :
 *   Pas de @Builder car constructeur a 2 parametres suffit.
 *   @AllArgsConstructor + @NoArgsConstructor pour permettre le mapping
 *   simple dans le service.
 * ============================================================================
 */
package com.example.locvoitures.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RepartitionDto {
    /**
     * Libelle a afficher (ex: nom de categorie "SUV").
     */
    private String libelle;

    /**
     * Comptage associe.
     */
    private long count;
}
