/*
 * ============================================================================
 * TopVoitureDto - DTO d'une ligne du top voitures (dashboard)
 * ============================================================================
 *
 * Role :
 *   Represente une ligne du classement "voitures les plus louees". Mappee
 *   depuis Object[] retourne par VoitureRepository.topVoituresLouees().
 *
 * Utilise par :
 *   - service/DashboardService : conversion Object[] -> TopVoitureDto
 *   - dto/DashboardDto.topVoitures : liste agregee
 *   - templates/admin/dashboard.html : affichage en table/graphique
 * ============================================================================
 */
package com.example.locvoitures.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopVoitureDto {
    /**
     * Id de la voiture (utile pour generer un lien vers la fiche).
     */
    private Long voitureId;
    /**
     * Modele commercial (ex: "Clio 5").
     */
    private String modele;
    /**
     * Nom de la marque (ex: "Renault").
     */
    private String marque;
    /**
     * Nombre de locations confirmees (PAYEE + EN_COURS + TERMINEE).
     */
    private long nombreLocations;
}
