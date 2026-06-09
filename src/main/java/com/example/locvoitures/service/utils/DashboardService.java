/*
 * ============================================================================
 * DashboardService - Agregation des KPIs du dashboard admin
 * ============================================================================
 *
 * Role :
 *   Construit le DTO DashboardDto en agregeant les KPIs cote SQL via les
 *   requetes JPQL des repositories. Aucun calcul Streams en memoire :
 *   tout est delegue a la base pour passer a l'echelle.
 *
 * Appele depuis :
 *   - controller/AdminDashboardController : GET /admin -> rend templates/admin/dashboard.html
 *
 * Appelle :
 *   - VoitureRepository : count, countByStatutGeneral, topVoituresLouees,
 *     repartitionParCategorie
 *   - LocationRepository : countDemandesEnAttente, countByStatut,
 *     chiffreAffairesTotal
 *   - UtilisateurRepository : countByRoleAndActif
 *   - PermisRepository : countByBanniTrue (KPI permis bannis)
 *   - AvisRepository : noteMoyenneGlobale
 *   - ReclamationRepository : countByStatut
 *
 * Transactionnel :
 *   @Transactional(readOnly=true) au niveau classe : toutes les methodes
 *   sont en lecture. Hibernate desactive le dirty checking -> plus rapide.
 *
 * Note :
 *   Les requetes top et repartition retournent Object[] (projection libre).
 *   On les mappe ici vers des DTO typed pour les passer proprement au
 *   template Thymeleaf.
 * ============================================================================
 */
package com.example.locvoitures.service.utils;

import com.example.locvoitures.dto.view.DashboardDto;
import com.example.locvoitures.dto.view.RepartitionDto;
import com.example.locvoitures.dto.view.TopVoitureDto;
import com.example.locvoitures.enumeration.StatutLocation;
import com.example.locvoitures.enumeration.StatutReclamation;
import com.example.locvoitures.enumeration.StatutVoiture;
// Wildcard pour importer tous les repositories utilises
import com.example.locvoitures.repository.*;

import lombok.RequiredArgsConstructor;

// PageRequest : implementation concrete de Pageable pour limiter le top a 5
import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /*
     * Note architecturale : DashboardService est un "aggregator service"
     * en lecture seule. Contrairement aux autres services qui n'appellent
     * que leur repository propre, celui-ci agrege des KPIs heterogenes
     * (voitures, locations, clients, permis bannis, avis, reclamations).
     * Passer par chaque service metier serait sur-engineering : on ferait
     * 6 appels delegants vers des methodes count* triviales. Pattern accepte
     * pour les vues de reporting / dashboard.
     */
    private final VoitureRepository voitureRepo;
    private final LocationRepository locationRepo;
    private final ClientRepository clientRepo;
    private final PermisRepository permisRepo;
    private final AvisRepository avisRepo;
    private final ReclamationRepository reclamationRepo;

    /**
     * Construit le DashboardDto avec tous les KPIs.
     * Chaque .count*() declenche une requete SQL legere COUNT(*).
     */
    public DashboardDto buildDashboard() {
        // Top 5 voitures par nombre de locations confirmees.
        // PageRequest.of(0, 5) = limit 5 sans pagination supplementaire.
        // row[0]=id, row[1]=modele, row[2]=marqueNom, row[3]=count (cast Number safe).
        List<TopVoitureDto> topVoitures = voitureRepo
                .topVoituresLouees(PageRequest.of(0, 5))
                .stream()
                .map(row -> new TopVoitureDto(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();

        // Repartition par categorie : [categorieNom, count].
        List<RepartitionDto> repartition = voitureRepo.repartitionParCategorie()
                .stream()
                .map(row -> new RepartitionDto(
                        (String) row[0],
                        ((Number) row[1]).longValue()))
                .toList();

        // Construction du DTO complet via builder.
        return DashboardDto.builder()
                // Flotte
                .nbVoituresTotal(voitureRepo.count())
                .nbVoituresActives(voitureRepo.countByStatutGeneral(StatutVoiture.ACTIVE))
                .nbVoituresHorsService(voitureRepo.countByStatutGeneral(StatutVoiture.HORS_SERVICE))
                // Locations
                .nbDemandesEnAttente(locationRepo.countDemandesEnAttente())
                .nbLocationsEnCours(locationRepo.countByStatut(StatutLocation.EN_COURS))
                .nbLocationsPreReservees(locationRepo.countByStatut(StatutLocation.ACCEPTEE))
                .chiffreAffairesTotal(orZero(locationRepo.chiffreAffairesTotal()))
                // Utilisateurs
                .nbClientsActifs(clientRepo.countByActifTrue())
                .nbPermisBannis(permisRepo.countByBanniTrue())
                // Qualite
                .noteMoyenne(avisRepo.noteMoyenneGlobale())
                .nbReclamationsEnTraitement(reclamationRepo.countByStatut(StatutReclamation.EN_TRAITEMENT))
                // Top et repartitions
                .topVoitures(topVoitures)
                .repartitionParCategorie(repartition)
                .build();
    }

    /**
     * Helper : retourne 0 si null (chiffreAffaires peut etre null si pas de
     * location, COALESCE en SQL est sense le gerer mais defense-en-profondeur).
     */
    private BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
